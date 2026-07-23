package com.focusflow.services

import com.focusflow.data.*
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ResourceMonitorService — anonymous JVM/OS health telemetry for FocusFlow.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  WHAT IS REPORTED                                                       │
 * │  • Heap usage (used / allocated / max MB + percent)                     │
 * │  • Non-heap memory                                                      │
 * │  • Physical RAM (total / free)                                          │
 * │  • CPU model name (e.g. "Intel Core i7-12700K") + core count           │
 * │  • CPU load — process (FocusFlow's own %) and system-wide %            │
 * │  • Machine power tier (Low / Mid / High) — stored to DB for future use │
 * │  • Live thread count (peak, daemon)                                     │
 * │  • GC collections + pause time                                         │
 * │  • OS name, Java version, app version                                  │
 * │                                                                         │
 * │  WHAT IS NEVER REPORTED                                                 │
 * │  • User name, home directory, file paths, or any PII                   │
 * │  • App content (tasks, sessions, blocked-app lists)                    │
 * │  • IP address or network identifiers                                    │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  REPORT SCHEDULE                                                        │
 * │  • Periodic heartbeat  — every REPORT_INTERVAL_MS (default 1 hour)     │
 * │  • Heap warning alert  — heap > HEAP_WARNING_PERCENT of max             │
 * │  • Heap critical alert — heap > HEAP_CRITICAL_PERCENT of max            │
 * │  • Thread spike alert  — live thread count > THREAD_WARNING_COUNT       │
 * │  Alert types have individual cooldowns to prevent Discord flooding.     │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  PRIVACY                                                                │
 * │  Respects the "crash_reports_enabled" opt-out toggle in Settings.       │
 * │  When the user sets it to "false", no data is ever sent.                │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
object ResourceMonitorService {

    // ── Webhook ────────────────────────────────────────────────────────────────
    //
    // Webhook URL is NOT stored in source. Inject it at build time via:
    //   -Dfocusflow.webhook.monitor=https://discord.com/api/webhooks/...
    // If the system property is absent, telemetry is silently disabled.
    // All send functions already guard with takeIf { it.isNotBlank() } ?: return.
    //
    private val DISCORD_WEBHOOK_URL: String by lazy {
        System.getProperty("focusflow.webhook.monitor", "").trim()
    }

    // ── App version (injected from CrashReporter's single source of truth) ────
    private val appVersion get() = CrashReporter.APP_VERSION

    // ── Thresholds ─────────────────────────────────────────────────────────────
    private const val HEAP_WARNING_PERCENT  = 75   // % of max heap → yellow alert
    private const val HEAP_CRITICAL_PERCENT = 90   // % of max heap → red alert
    private const val THREAD_WARNING_COUNT  = 150  // live threads  → yellow alert
    /** Process CPU % above this for a single sample triggers a yellow alert.
     *  FocusFlow is a background enforcement app — sustained >25% is abnormal. */
    private const val CPU_PROCESS_WARN_PERCENT = 25

    // ── Schedule ───────────────────────────────────────────────────────────────
    private const val SAMPLE_INTERVAL_MS  = 60_000L       // 1 minute between samples
    private const val REPORT_INTERVAL_MS  = 3_600_000L    // hourly heartbeat report
    private const val ALERT_COOLDOWN_MS   = 1_800_000L    // 30 min cooldown per alert type

    // ── State ──────────────────────────────────────────────────────────────────
    private val running         = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null

    // Aggregation accumulators (reset each reporting period)
    @Volatile private var periodSampleCount   = 0
    @Volatile private var periodPeakHeapPct   = 0
    @Volatile private var periodPeakHeapMb    = 0L
    @Volatile private var periodPeakThreads   = 0
    @Volatile private var periodSumHeapMb     = 0L
    @Volatile private var periodPeakCpuPct    = -1   // -1 = no valid sample yet
    @Volatile private var periodSumCpuPct     = 0L
    @Volatile private var periodCpuSamples    = 0    // count of samples with valid CPU data

    // Alert cooldown timestamps (epoch ms, 0 = never fired)
    private val lastHeapWarnSentMs     = AtomicLong(0L)
    private val lastHeapCriticalSentMs = AtomicLong(0L)
    private val lastThreadWarnSentMs   = AtomicLong(0L)
    private val lastCpuWarnSentMs      = AtomicLong(0L)

    /**
     * Machine power tier — computed once from core count + RAM, then cached here
     * AND persisted to the DB ("machine_tier") so future app versions can read it
     * without re-computing, and so we can segment telemetry by device class.
     *
     * Tier thresholds (conservative — err toward Mid rather than High):
     *   Low  : ≤4 cores  AND ≤8 GB RAM
     *   High : ≥12 cores OR  ≥24 GB RAM
     *   Mid  : everything else
     */
    @Volatile private var machineTierCached: String? = null

    /**
     * CPU model name — e.g. "Intel(R) Core(TM) i7-12700K CPU @ 3.60GHz".
     * Queried once via `wmic cpu get Name` (Windows-only, fast), then cached.
     * Falls back to PROCESSOR_IDENTIFIER env var, then "unknown" if both fail.
     */
    @Volatile private var cpuNameCached: String? = null

    private val TIMESTAMP_HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Start the resource monitor.  Safe to call multiple times — only one
     * monitor thread ever runs.  Call after [CrashReporter.install] in main().
     */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread(::monitorLoop).also {
            it.isDaemon = true
            it.name     = "focusflow-resource-monitor"
            it.priority = Thread.MIN_PRIORITY
            it.start()
        }
    }

    /**
     * Stop the monitor.  Interrupts the background thread gracefully.
     */
    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    // ── Monitor loop ───────────────────────────────────────────────────────────

    private fun monitorLoop() {
        var nextReportAt = System.currentTimeMillis() + REPORT_INTERVAL_MS

        while (running.get()) {
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
            if (!running.get()) break

            // Check opt-out once per sample cycle (not cached — user can change it)
            if (!isOptedIn()) continue

            val snap = takeSnapshot()
            accumulateSample(snap)
            checkThresholdAlerts(snap)

            if (System.currentTimeMillis() >= nextReportAt) {
                sendHeartbeat(snap)
                resetAccumulators()
                nextReportAt = System.currentTimeMillis() + REPORT_INTERVAL_MS
            }
        }
    }

    // ── Metrics snapshot ───────────────────────────────────────────────────────

    private data class ResourceSnapshot(
        val timestamp:        String,
        val heapUsedMb:       Long,
        val heapTotalMb:      Long,
        val heapMaxMb:        Long,
        val heapPercent:      Int,
        val nonHeapUsedMb:    Long,
        val physTotalMb:      Long,
        val physFreeMb:       Long,
        val cpuCores:         Int,
        /** Full CPU model string, e.g. "Intel(R) Core(TM) i7-12700K CPU @ 3.60GHz". */
        val cpuName:          String,
        /** FocusFlow process CPU load [0–100], or -1 if the JVM cannot measure it. */
        val cpuProcessPct:    Int,
        /** Whole-system CPU load [0–100], or -1 if unavailable. */
        val cpuSystemPct:     Int,
        /** Low / Mid / High — derived from cores + RAM, persisted to DB. */
        val machineTier:      String,
        val threadCount:      Int,
        val daemonThreads:    Int,
        val peakThreads:      Int,
        val gcCollections:    Long,
        val gcTimeMs:         Long,
        val osName:           String,
        val javaVersion:      String
    )

    private fun takeSnapshot(): ResourceSnapshot {
        val rt           = Runtime.getRuntime()
        val heapUsed     = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
        val heapTotal    = rt.totalMemory() / 1_048_576L
        val heapMax      = rt.maxMemory()   / 1_048_576L
        val heapPct      = if (heapMax > 0) ((heapUsed * 100) / heapMax).toInt() else 0

        var nonHeapMb    = 0L
        var physTotalMb  = 0L
        var physFreeMb   = 0L
        var threadCount  = 0
        var daemonCount  = 0
        var peakCount    = 0
        var gcCount      = 0L
        var gcTime       = 0L

        try {
            val mx = ManagementFactory.getMemoryMXBean()
            nonHeapMb = maxOf(0L, mx.nonHeapMemoryUsage.used / 1_048_576L)
        } catch (_: Throwable) {}

        var cpuProcessPct = -1
        var cpuSystemPct  = -1

        try {
            val osMx = ManagementFactory.getOperatingSystemMXBean()
            val totalMethod   = osMx.javaClass.getMethod("getTotalPhysicalMemorySize")
            val freeMethod    = osMx.javaClass.getMethod("getFreePhysicalMemorySize")
            physTotalMb = (totalMethod.invoke(osMx) as Number).toLong() / 1_048_576L
            physFreeMb  = (freeMethod.invoke(osMx)  as Number).toLong() / 1_048_576L

            // Process CPU load — how much CPU FocusFlow itself is using.
            // getProcessCpuLoad() is a com.sun extension; use reflection so we don't
            // take a hard compile-time dependency on internal Sun APIs.
            try {
                val processLoad = osMx.javaClass.getMethod("getProcessCpuLoad").invoke(osMx) as Double
                if (processLoad >= 0.0) cpuProcessPct = (processLoad * 100).toInt().coerceIn(0, 100)
            } catch (_: Throwable) {}

            // System-wide CPU load — overall machine utilisation.
            // Try getCpuLoad() (Java 14+) first, then getSystemCpuLoad() (older).
            try {
                val systemLoad = try {
                    osMx.javaClass.getMethod("getCpuLoad").invoke(osMx) as Double
                } catch (_: Throwable) {
                    osMx.javaClass.getMethod("getSystemCpuLoad").invoke(osMx) as Double
                }
                if (systemLoad >= 0.0) cpuSystemPct = (systemLoad * 100).toInt().coerceIn(0, 100)
            } catch (_: Throwable) {}

        } catch (_: Throwable) {}

        try {
            val tmx    = ManagementFactory.getThreadMXBean()
            threadCount = tmx.threadCount
            daemonCount = tmx.daemonThreadCount
            peakCount   = tmx.peakThreadCount
        } catch (_: Throwable) {}

        try {
            for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gc.collectionCount > 0) gcCount += gc.collectionCount
                if (gc.collectionTime  > 0) gcTime  += gc.collectionTime
            }
        } catch (_: Throwable) {}

        val cores = rt.availableProcessors()
        return ResourceSnapshot(
            timestamp     = LocalDateTime.now().format(TIMESTAMP_HUMAN),
            heapUsedMb    = heapUsed,
            heapTotalMb   = heapTotal,
            heapMaxMb     = heapMax,
            heapPercent   = heapPct,
            nonHeapUsedMb = nonHeapMb,
            physTotalMb   = physTotalMb,
            physFreeMb    = physFreeMb,
            cpuCores      = cores,
            cpuName       = computeCpuName(),
            cpuProcessPct = cpuProcessPct,
            cpuSystemPct  = cpuSystemPct,
            machineTier   = computeMachineTier(cores, physTotalMb),
            threadCount   = threadCount,
            daemonThreads = daemonCount,
            peakThreads   = peakCount,
            gcCollections = gcCount,
            gcTimeMs      = gcTime,
            osName        = prop("os.name") + " " + prop("os.version"),
            javaVersion   = prop("java.version")
        )
    }

    /**
     * Classify this machine into Low / Mid / High based on core count and RAM.
     * Computed once and cached; also persisted to the DB so future app versions
     * can read "machine_tier" without re-computing, and so telemetry can be
     * segmented by device class when tuning watchdog intervals or poll rates.
     *
     * Thresholds (conservative — err toward Mid):
     *   Low  : ≤4 cores AND ≤8 GB RAM   → likely an older laptop/low-end PC
     *   High : ≥12 cores OR ≥24 GB RAM  → workstation / high-end gaming PC
     *   Mid  : everything else
     */
    private fun computeMachineTier(cores: Int, ramMb: Long): String {
        machineTierCached?.let { return it }
        val ramGb = ramMb / 1024L
        val tier  = when {
            cores <= 4 && ramGb <= 8  -> "Low"
            cores >= 12 || ramGb >= 24 -> "High"
            else                       -> "Mid"
        }
        machineTierCached = tier
        try { Database.setSetting("machine_tier", tier) } catch (_: Throwable) {}
        return tier
    }

    /**
     * Return the CPU model name, e.g. "Intel(R) Core(TM) i7-12700K CPU @ 3.60GHz".
     *
     * Strategy (Windows-first; FocusFlow is a Windows app):
     *  1. `wmic cpu get Name /value` — most reliable, returns the exact marketing name.
     *  2. PROCESSOR_IDENTIFIER env var — fast fallback, less friendly
     *     ("Intel64 Family 6 Model 97 Stepping 2, GenuineIntel").
     *  3. "unknown" — if both sources are unavailable (e.g. sandboxed environment).
     *
     * Result is cached after the first call so we never shell out more than once.
     */
    private fun computeCpuName(): String {
        cpuNameCached?.let { return it }
        val name = try {
            val proc = Runtime.getRuntime().exec(arrayOf("wmic", "cpu", "get", "Name", "/value"))
            val output = proc.inputStream.bufferedReader().readLines()
            proc.waitFor()
            output.firstOrNull { it.startsWith("Name=") }
                ?.removePrefix("Name=")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) { null }
            ?: System.getenv("PROCESSOR_IDENTIFIER")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "unknown"
        cpuNameCached = name
        return name
    }

    // ── Accumulation ───────────────────────────────────────────────────────────

    private fun accumulateSample(snap: ResourceSnapshot) {
        periodSampleCount++
        periodSumHeapMb += snap.heapUsedMb
        if (snap.heapPercent > periodPeakHeapPct) periodPeakHeapPct = snap.heapPercent
        if (snap.heapUsedMb  > periodPeakHeapMb)  periodPeakHeapMb  = snap.heapUsedMb
        if (snap.threadCount > periodPeakThreads)  periodPeakThreads = snap.threadCount
        if (snap.cpuProcessPct >= 0) {
            periodSumCpuPct += snap.cpuProcessPct
            periodCpuSamples++
            if (snap.cpuProcessPct > periodPeakCpuPct) periodPeakCpuPct = snap.cpuProcessPct
        }
    }

    private fun resetAccumulators() {
        periodSampleCount = 0
        periodSumHeapMb   = 0L
        periodPeakHeapPct = 0
        periodPeakHeapMb  = 0L
        periodPeakThreads = 0
        periodPeakCpuPct  = -1
        periodSumCpuPct   = 0L
        periodCpuSamples  = 0
    }

    // ── Threshold alerts ───────────────────────────────────────────────────────

    private fun checkThresholdAlerts(snap: ResourceSnapshot) {
        val now = System.currentTimeMillis()

        if (snap.heapPercent >= HEAP_CRITICAL_PERCENT) {
            if (now - lastHeapCriticalSentMs.get() > ALERT_COOLDOWN_MS) {
                lastHeapCriticalSentMs.set(now)
                sendAlert(
                    title       = "🔴 Heap Critical — v$appVersion",
                    color       = 16711680, // red
                    description = "Heap usage is at **${snap.heapPercent}%** of max (${snap.heapUsedMb} MB / ${snap.heapMaxMb} MB). " +
                                  "Risk of OutOfMemoryError.",
                    snap        = snap
                )
            }
        } else if (snap.heapPercent >= HEAP_WARNING_PERCENT) {
            if (now - lastHeapWarnSentMs.get() > ALERT_COOLDOWN_MS) {
                lastHeapWarnSentMs.set(now)
                sendAlert(
                    title       = "🟡 Heap Warning — v$appVersion",
                    color       = 16776960, // yellow
                    description = "Heap usage reached **${snap.heapPercent}%** of max (${snap.heapUsedMb} MB / ${snap.heapMaxMb} MB).",
                    snap        = snap
                )
            }
        }

        if (snap.threadCount >= THREAD_WARNING_COUNT) {
            if (now - lastThreadWarnSentMs.get() > ALERT_COOLDOWN_MS) {
                lastThreadWarnSentMs.set(now)
                sendAlert(
                    title       = "🟡 Thread Spike — v$appVersion",
                    color       = 16776960, // yellow
                    description = "Live thread count reached **${snap.threadCount}** (daemon: ${snap.daemonThreads}, peak: ${snap.peakThreads}).",
                    snap        = snap
                )
            }
        }

        // CPU spike — FocusFlow is a background enforcement app and should stay
        // near 0% CPU at idle.  A sustained spike above CPU_PROCESS_WARN_PERCENT
        // suggests a runaway loop, a hook timeout storm, or an unexpected workload.
        if (snap.cpuProcessPct >= CPU_PROCESS_WARN_PERCENT) {
            if (now - lastCpuWarnSentMs.get() > ALERT_COOLDOWN_MS) {
                lastCpuWarnSentMs.set(now)
                sendAlert(
                    title       = "🟡 CPU Spike — v$appVersion",
                    color       = 16776960, // yellow
                    description = "FocusFlow process CPU hit **${snap.cpuProcessPct}%** " +
                                  "(system: ${if (snap.cpuSystemPct >= 0) "${snap.cpuSystemPct}%" else "n/a"}, " +
                                  "machine tier: ${snap.machineTier}). " +
                                  "Expected near 0% at idle.",
                    snap        = snap
                )
            }
        }
    }

    // ── Discord payloads ───────────────────────────────────────────────────────

    /**
     * Periodic heartbeat — sent every [REPORT_INTERVAL_MS].
     * Includes the period's aggregated stats alongside the current snapshot.
     */
    private fun sendHeartbeat(snap: ResourceSnapshot) {
        val avgHeap   = if (periodSampleCount > 0) periodSumHeapMb / periodSampleCount else snap.heapUsedMb
        val avgCpuStr = if (periodCpuSamples > 0) "${periodSumCpuPct / periodCpuSamples}%" else "n/a"
        val peakCpuStr = if (periodPeakCpuPct >= 0) "${periodPeakCpuPct}%" else "n/a"
        val webhookUrl = DISCORD_WEBHOOK_URL.takeIf { it.isNotBlank() } ?: return

        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")

        val memoryBar = buildMemoryBar(snap.heapPercent)
        val cpuNowStr = if (snap.cpuProcessPct >= 0) "${snap.cpuProcessPct}%" else "n/a"
        val sysNowStr = if (snap.cpuSystemPct  >= 0) "${snap.cpuSystemPct}%"  else "n/a"

        val payload = buildPayload(
            title       = "📊 Resource Heartbeat — v$appVersion",
            color       = 3447003, // blue
            description = "Hourly JVM health snapshot. All data is anonymous — no PII collected.",
            fields      = listOf(
                field("Heap Now",      "${snap.heapUsedMb} MB / ${snap.heapMaxMb} MB (${snap.heapPercent}%)\\n$memoryBar", false),
                field("Heap (Period)", "Avg ${avgHeap} MB  ·  Peak ${periodPeakHeapMb} MB  ·  Peak ${periodPeakHeapPct}%", false),
                field("CPU Model",      snap.cpuName.esc(),                 false),
                field("CPU (Process)", "Now $cpuNowStr  ·  Avg $avgCpuStr  ·  Peak $peakCpuStr", false),
                field("CPU (System)",  "Now $sysNowStr",                   true),
                field("Machine Tier",  "${snap.machineTier} (${snap.cpuCores} cores, ${snap.physTotalMb / 1024} GB RAM)", true),
                field("Non-Heap",      "${snap.nonHeapUsedMb} MB",         true),
                field("Physical RAM",  "${snap.physFreeMb} MB free / ${snap.physTotalMb} MB", true),
                field("Threads",       "Live ${snap.threadCount}  ·  Daemon ${snap.daemonThreads}  ·  Peak ${snap.peakThreads}", true),
                field("GC",            "${snap.gcCollections} collections  ·  ${snap.gcTimeMs} ms total pause", true),
                field("OS",            snap.osName.esc(),                  true),
                field("Java",          snap.javaVersion.esc(),             true),
                field("Timestamp",     snap.timestamp,                     true)
            )
        )

        postToWebhook(webhookUrl, payload)
    }

    /**
     * Immediate threshold alert — fired when a metric crosses a warning level.
     */
    private fun sendAlert(title: String, color: Int, description: String, snap: ResourceSnapshot) {
        val webhookUrl = DISCORD_WEBHOOK_URL.takeIf { it.isNotBlank() } ?: return

        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")

        val memoryBar  = buildMemoryBar(snap.heapPercent)
        val cpuNowStr  = if (snap.cpuProcessPct >= 0) "${snap.cpuProcessPct}%" else "n/a"
        val sysNowStr  = if (snap.cpuSystemPct  >= 0) "${snap.cpuSystemPct}%"  else "n/a"

        val payload = buildPayload(
            title       = title,
            color       = color,
            description = "${description.replace("\"", "\\\"")}",
            fields      = listOf(
                field("Heap",          "${snap.heapUsedMb} MB / ${snap.heapMaxMb} MB (${snap.heapPercent}%)\\n$memoryBar", false),
                field("CPU Model",      snap.cpuName.esc(),                 false),
                field("CPU (Process)", cpuNowStr,                          true),
                field("CPU (System)",  sysNowStr,                          true),
                field("Machine Tier",  "${snap.machineTier} (${snap.cpuCores} cores)", true),
                field("Non-Heap",      "${snap.nonHeapUsedMb} MB",         true),
                field("Physical RAM",  "${snap.physFreeMb} MB free / ${snap.physTotalMb} MB", true),
                field("Threads",       "Live ${snap.threadCount}  ·  Daemon ${snap.daemonThreads}  ·  Peak ${snap.peakThreads}", true),
                field("GC",            "${snap.gcCollections} collections  ·  ${snap.gcTimeMs} ms total pause", true),
                field("OS",            snap.osName.esc(),                  true),
                field("Java",          snap.javaVersion.esc(),             true),
                field("Timestamp",     snap.timestamp,                     true)
            )
        )

        postToWebhook(webhookUrl, payload)
    }

    // ── Payload builder helpers ────────────────────────────────────────────────

    private fun field(name: String, value: String, inline: Boolean): String =
        """{ "name": "${name.replace("\"", "\\\"")}", "value": "${value.replace("\"", "\\\"")}", "inline": $inline }"""

    private fun buildPayload(title: String, color: Int, description: String, fields: List<String>): String {
        val fieldsJson = fields.joinToString(",\n          ")
        return """
            {
              "username": "FocusFlow Telemetry",
              "embeds": [{
                "title": "${title.replace("\"", "\\\"")}",
                "color": $color,
                "description": "${description.replace("\"", "\\\"")}",
                "fields": [
                  $fieldsJson
                ]
              }]
            }
        """.trimIndent()
    }

    /**
     * ASCII progress bar for heap fill percentage.
     * e.g. 65% → ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░ 65%
     */
    private fun buildMemoryBar(percent: Int): String {
        val clamped = percent.coerceIn(0, 100)
        val filled  = clamped / 5
        val empty   = 20 - filled
        return "▓".repeat(filled) + "░".repeat(empty) + " $clamped%"
    }

    // ── Public mode-event telemetry ────────────────────────────────────────────

    /**
     * Send a one-shot Discord embed when a significant enforcement mode changes
     * (Nuclear Mode enabled, Emergency Break activated, etc.).
     *
     * Fires on a daemon thread — caller is never blocked.
     * Silently no-ops if the user has opted out of anonymous diagnostics.
     *
     * @param title       Embed title shown in bold at the top of the Discord card.
     * @param description One-sentence summary of the event.
     * @param color       Discord embed side-bar colour as a decimal integer (e.g. 15158332 = red).
     * @param fields      Key→value pairs rendered as inline embed fields.
     */
    fun sendModeEvent(
        title: String,
        description: String,
        color: Int,
        fields: List<Pair<String, String>> = emptyList()
    ) {
        if (!isOptedIn()) return
        val webhookUrl = DISCORD_WEBHOOK_URL.takeIf { it.isNotBlank() } ?: return
        val ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val allFields = (fields + ("Timestamp" to ts) + ("Version" to appVersion))
            .map { (k, v) -> field(k, v, true) }
        val payload = buildPayload(title, color, description, allFields)
        postToWebhook(webhookUrl, payload)
    }

    // ── HTTP dispatch ──────────────────────────────────────────────────────────

    /**
     * Posts [payload] to [webhookUrl] on a daemon thread.
     * All exceptions are swallowed — telemetry must never cause secondary failures.
     */
    private fun postToWebhook(webhookUrl: String, payload: String) {
        Thread {
            try {
                val url  = java.net.URL(webhookUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.connectTimeout = 8_000
                conn.readTimeout    = 8_000
                conn.doOutput       = true
                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                conn.responseCode
            } catch (_: Throwable) {
                // Intentionally silent — telemetry must never cause secondary failures.
            }
        }.also { it.isDaemon = true; it.name = "focusflow-resource-telemetry" }.start()
    }

    // ── Privacy helpers ────────────────────────────────────────────────────────

    /** Returns true only if the user explicitly opted in via the consent dialog or Privacy setting. */
    private fun isOptedIn(): Boolean =
        try { Database.getSetting("crash_reports_enabled") == "true" } catch (_: Throwable) { false }

    private fun prop(key: String): String =
        try { System.getProperty(key) ?: "(not set)" } catch (_: Throwable) { "(error)" }
}
