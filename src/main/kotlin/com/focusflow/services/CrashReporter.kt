package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CrashReporter — production-grade crash & diagnostic logging for FocusFlow.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  THREAD COVERAGE                                                        │
 * │  1. All Java/Kotlin threads    → Thread.setDefaultUncaughtExceptionHandler │
 * │  2. AWT Event Dispatch Thread  → sun.awt.exception.handler property     │
 * │  3. Kotlin coroutines          → fall-through to (1) via SupervisorJob  │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  EXCEPTION TYPES HANDLED                                                │
 * │  • NullPointerException, ClassCastException, IllegalStateException, …   │
 * │  • OutOfMemoryError   → pre-allocated 1 MB reserve freed before logging │
 * │  • StackOverflowError → handler avoids recursion; uses shallow path     │
 * │  • Full cause chain + suppressed exceptions + cycle detection           │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  REPORT DESTINATIONS (tried in order, first success wins)               │
 * │  1. ~/Desktop/FocusFlow-crash-<timestamp>.log                           │
 * │  2. ~/.focusflow/crash-<timestamp>.log                                  │
 * │  3. %TEMP% / java.io.tmpdir / FocusFlow-crash-<timestamp>.log           │
 * │  4. stderr (last resort — always attempted regardless)                  │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  SAFETY GUARANTEES                                                      │
 * │  • Anti-re-entrancy: AtomicBoolean prevents duplicate reports           │
 * │  • Double-fault: entire handler body wrapped in try/catch               │
 * │  • OOM-safe: 1 MB heap reserve freed as the FIRST action                │
 * │  • SO-safe: appendExceptionChain depth-capped at MAX_CAUSE_DEPTH        │
 * │  • Pre-crash cleanup: restores Windows state so PC is never left locked │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
object CrashReporter {

    // ── Public configuration ───────────────────────────────────────────────────
    /** Developer support email — pre-filled when the user clicks "Send Report". */
    const val SUPPORT_EMAIL = "support@tbtechs.dev"

    // ── Constants ─────────────────────────────────────────────────────────────
    private const val MAX_CAUSE_DEPTH   = 20
    private const val MAX_THREAD_FRAMES = 40
    private const val MAX_THREADS       = 200

    private val TIMESTAMP_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val TIMESTAMP_HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    // ── State ─────────────────────────────────────────────────────────────────

    /** App version injected at startup. */
    @Volatile private var appVersion = "1.0.5"

    /**
     * Pre-allocated 1 MB emergency heap reserve.
     * Nulled out at the very start of any crash handler so the logger has
     * memory available even when the crash was an OutOfMemoryError.
     */
    @Volatile private var oomReserve: ByteArray? = ByteArray(1024 * 1024)

    /**
     * Atomic re-entrancy guard.
     * Ensures only one crash report is ever generated, even if two threads
     * crash simultaneously (e.g. cascade failure where multiple coroutines
     * die from the same root cause).
     */
    private val crashHandled = AtomicBoolean(false)

    // ── AWT EDT exception handler ─────────────────────────────────────────────

    /**
     * Loaded by AWT via the `sun.awt.exception.handler` system property.
     * AWT instantiates this class by name and calls handle(Throwable) whenever
     * an uncaught exception propagates through the Event Dispatch Thread loop.
     *
     * Must be a non-inner class with a public no-arg constructor and a
     * public `handle(Throwable)` method — this is AWT's internal contract.
     */
    class AwtExceptionHandler {
        @Suppress("unused")
        fun handle(e: Throwable) {
            report(Thread.currentThread(), e, source = "AWT-EDT")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Install all crash handlers. Call this as the very FIRST statement in main()
     * so every subsequent failure — including Database.init() — is captured.
     *
     * @param version  The app version string written into every crash report.
     */
    fun install(version: String = "1.0.5") {
        appVersion = version

        // 1. All Java/Kotlin threads (and coroutines via thread fallthrough)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            report(thread, throwable, source = "Thread")
        }

        // 2. AWT Event Dispatch Thread
        //    "sun.awt.exception.handler" is an undocumented but stable internal
        //    hook present in all Oracle/OpenJDK/GraalVM JVMs since Java 5.
        //    AWT resets this property after each EDT exception, so we must
        //    re-install it from the handler itself to survive repeated EDT crashes.
        System.setProperty(
            "sun.awt.exception.handler",
            "com.focusflow.services.CrashReporter\$AwtExceptionHandler"
        )
    }

    /**
     * Manually report a caught-but-fatal throwable.
     * Use when you catch an exception but cannot meaningfully recover.
     */
    fun report(thread: Thread, throwable: Throwable, source: String = "Manual") {
        // Atomic check — only the first caller wins; all others return immediately.
        if (!crashHandled.compareAndSet(false, true)) return

        // Free OOM reserve FIRST — before any allocation — so we have heap.
        oomReserve = null

        // Re-install AWT handler (AWT clears it after each use)
        try {
            System.setProperty(
                "sun.awt.exception.handler",
                "com.focusflow.services.CrashReporter\$AwtExceptionHandler"
            )
        } catch (_: Throwable) {}

        try {
            val ts      = LocalDateTime.now()
            val tsFile  = ts.format(TIMESTAMP_FILE)
            val tsHuman = ts.format(TIMESTAMP_HUMAN)

            val reportText = buildReport(thread, throwable, source, tsHuman)
            val logFile    = writeLog(reportText, tsFile)

            // Always emit at least the exception type + message to stderr
            System.err.println("[FocusFlow] CRASH [$source] ${throwable.javaClass.name}: ${throwable.message}")
            logFile?.let { System.err.println("[FocusFlow] Log: ${it.absolutePath}") }

            // Safety cleanup: restore Windows state before we exit/notify
            safetyCleanup()

            // Notify user via Swing dialog (most reliable — works when Compose is dead)
            notifyUser(logFile, throwable, source)

        } catch (handlerEx: Throwable) {
            // ── Double-fault: crash handler itself crashed ────────────────────
            // This path is extremely unlikely but must be bulletproof.
            try {
                System.err.println("[FocusFlow] CRASH HANDLER FAULT: ${handlerEx.javaClass.name}: ${handlerEx.message}")
                System.err.println("[FocusFlow] Original: ${throwable.javaClass.name}: ${throwable.message}")
                throwable.printStackTrace(System.err)
                safetyCleanup()
            } catch (_: Throwable) {
                // Absolute last resort — cannot do anything more
            }
        }
    }

    // ── Report assembly ────────────────────────────────────────────────────────

    private fun buildReport(
        thread: Thread,
        throwable: Throwable,
        source: String,
        tsHuman: String
    ): String {
        val sb  = StringBuilder(8192)
        val SEP = "═".repeat(72)
        val DIV = "─".repeat(72)

        // ── Header ────────────────────────────────────────────────────────────
        sb.ln(SEP)
        sb.ln("  FOCUSFLOW CRASH REPORT")
        sb.ln(SEP)
        sb.ln("Timestamp    : $tsHuman")
        sb.ln("App Version  : $appVersion")
        sb.ln("Crash Source : $source")
        sb.ln()

        // ── System ────────────────────────────────────────────────────────────
        sb.ln("── SYSTEM $DIV".take(72))
        sb.ln("OS Name      : ${prop("os.name")}")
        sb.ln("OS Version   : ${prop("os.version")}")
        sb.ln("OS Arch      : ${prop("os.arch")}")
        sb.ln("JVM Name     : ${prop("java.vm.name")}")
        sb.ln("JVM Version  : ${prop("java.vm.version")}")
        sb.ln("JVM Vendor   : ${prop("java.vendor")}")
        sb.ln("Java Version : ${prop("java.version")}")
        sb.ln("User Home    : ${prop("user.home")}")
        sb.ln("Working Dir  : ${prop("user.dir")}")
        sb.ln("File Enc     : ${prop("file.encoding")}")
        sb.ln("Render API   : ${prop("skiko.renderApi")}")
        sb.ln()

        // ── Memory ────────────────────────────────────────────────────────────
        sb.ln("── MEMORY $DIV".take(72))
        val rt   = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
        val total = rt.totalMemory() / 1_048_576L
        val max   = rt.maxMemory()   / 1_048_576L
        sb.ln("Heap Used    : ${used} MB  (of ${total} MB allocated, ${max} MB max)")
        try {
            val mx = ManagementFactory.getMemoryMXBean()
            val nh = mx.nonHeapMemoryUsage
            val nhUsed = nh.used / 1_048_576L
            val nhMax  = if (nh.max < 0) "?" else "${nh.max / 1_048_576L} MB"
            sb.ln("Non-Heap     : ${nhUsed} MB used / $nhMax max")
        } catch (_: Throwable) {}
        try {
            val osMx = ManagementFactory.getOperatingSystemMXBean()
            // Use reflection to avoid importing the internal com.sun.management package
            val getTotalPhys = osMx.javaClass.getMethod("getTotalPhysicalMemorySize")
            val getFreePhys  = osMx.javaClass.getMethod("getFreePhysicalMemorySize")
            val totalPhys = (getTotalPhys.invoke(osMx) as Long) / 1_048_576L
            val freePhys  = (getFreePhys.invoke(osMx) as Long) / 1_048_576L
            sb.ln("Physical RAM : ${freePhys} MB free / ${totalPhys} MB total")
        } catch (_: Throwable) {}
        sb.ln("CPU Cores    : ${rt.availableProcessors()}")
        try {
            val threadMx = ManagementFactory.getThreadMXBean()
            sb.ln("Thread Count : ${threadMx.threadCount} live, ${threadMx.daemonThreadCount} daemon")
            sb.ln("Peak Threads : ${threadMx.peakThreadCount}")
            sb.ln("Total Starts : ${threadMx.totalStartedThreadCount}")
        } catch (_: Throwable) {}
        sb.ln()

        // ── Crash thread ──────────────────────────────────────────────────────
        sb.ln("── CRASH THREAD $DIV".take(72))
        sb.ln("Thread Name  : ${thread.name}")
        sb.ln("Thread ID    : ${thread.id}")
        sb.ln("Daemon       : ${thread.isDaemon}")
        sb.ln("Priority     : ${thread.priority}")
        sb.ln("State        : ${thread.state}")
        sb.ln("Thread Group : ${thread.threadGroup?.name ?: "?"}")
        sb.ln()

        // ── Exception chain ───────────────────────────────────────────────────
        sb.ln("── EXCEPTION CHAIN $DIV".take(72))
        val isOom = throwable is OutOfMemoryError
        val isSo  = throwable is StackOverflowError
        when {
            isOom -> sb.ln("! OutOfMemoryError — heap was exhausted when this crash occurred.")
            isSo  -> sb.ln("! StackOverflowError — stack trace may be truncated to protect the handler.")
        }
        appendExceptionChain(sb, throwable, depth = 0, seen = mutableSetOf())
        sb.ln()

        // ── FocusFlow enforcement state ───────────────────────────────────────
        sb.ln("── FOCUSFLOW STATE $DIV".take(72))
        appendFocusFlowState(sb)
        sb.ln()

        // ── Database file info ────────────────────────────────────────────────
        sb.ln("── DATABASE $DIV".take(72))
        appendDatabaseInfo(sb)
        sb.ln()

        // ── Thread dump ───────────────────────────────────────────────────────
        sb.ln("── ALL LIVE THREADS $DIV".take(72))
        appendThreadDump(sb, isSo)
        sb.ln()

        // ── GC info ───────────────────────────────────────────────────────────
        sb.ln("── GC ACTIVITY $DIV".take(72))
        appendGcInfo(sb)
        sb.ln()

        // ── Footer ────────────────────────────────────────────────────────────
        sb.ln(SEP)
        sb.ln("  END OF REPORT — FocusFlow $appVersion")
        sb.ln(SEP)

        return sb.toString()
    }

    // ── Section builders ───────────────────────────────────────────────────────

    /**
     * Recursively appends the full exception chain.
     * Handles:
     *   - Cause chains (getCause) — depth-capped at MAX_CAUSE_DEPTH
     *   - Suppressed exceptions (addSuppressed / try-with-resources)
     *   - Circular cause references (via identity set)
     *   - StackOverflowError (stack capped at MAX_THREAD_FRAMES to avoid re-overflow)
     */
    private fun appendExceptionChain(
        sb: StringBuilder,
        t: Throwable,
        depth: Int,
        seen: MutableSet<Int>   // IdentityHashCode, not equals() — avoids allocating IdentityHashMap
    ) {
        if (depth > MAX_CAUSE_DEPTH) {
            sb.ln("  [cause chain truncated at depth $MAX_CAUSE_DEPTH]")
            return
        }
        val id = System.identityHashCode(t)
        if (!seen.add(id)) {
            sb.ln("  [CIRCULAR CAUSE REFERENCE — stopping]")
            return
        }

        val indent  = "  ".repeat(depth)
        val label   = when (depth) { 0 -> "" else -> "${indent}Caused by: " }
        val isOOM   = t is OutOfMemoryError
        val isSO    = t is StackOverflowError

        sb.ln("$label${t.javaClass.name}: ${t.message ?: "(no message)"}")

        // Stack frames — capped for StackOverflowError to avoid re-overflowing
        val stack = t.stackTrace
        val cap   = if (isSO || isOOM) minOf(MAX_THREAD_FRAMES, stack.size) else stack.size
        for (i in 0 until cap) {
            sb.ln("$indent    at ${stack[i]}")
        }
        if (cap < stack.size) {
            sb.ln("$indent    ... ${stack.size - cap} more frames (truncated)")
        }

        // Suppressed exceptions (from try-with-resources / coroutine cancellation)
        for (sup in t.suppressed) {
            sb.ln("${indent}  Suppressed: ${sup.javaClass.name}: ${sup.message ?: "(no message)"}")
            val supStack = sup.stackTrace.take(10)
            for (f in supStack) sb.ln("${indent}      at $f")
            if (sup.stackTrace.size > 10) sb.ln("${indent}      ... ${sup.stackTrace.size - 10} more")
        }

        // Cause chain
        t.cause?.let { cause ->
            appendExceptionChain(sb, cause, depth + 1, seen)
        }
    }

    private fun appendFocusFlowState(sb: StringBuilder) {
        try {
            val sess = FocusSessionService.state.value
            sb.ln("Session Active     : ${sess.isActive}")
            sb.ln("Session Paused     : ${sess.isPaused}")
            sb.ln("Session Task       : ${sess.taskName.ifBlank { "(none)" }}")
            sb.ln("Session Elapsed    : ${sess.elapsedSeconds}s / ${sess.totalSeconds}s")
            sb.ln("Blocked Procs (s)  : ${sess.blockedProcesses.size}")
        } catch (_: Throwable) { sb.ln("  (session state unavailable)") }

        try {
            sb.ln("Always-On Block    : ${ProcessMonitor.alwaysOnEnabled}")
            sb.ln("Session Blocking   : ${ProcessMonitor.sessionActive}")
            sb.ln("Kill Switch        : ${ProcessMonitor.killSwitchActive}")
            sb.ln("Launcher Active    : ${FocusLauncherService.isActive.value}")
            sb.ln("Launcher Break     : ${FocusLauncherService.breakActive.value}")
            sb.ln("Schedule Blocked   : ${ProcessMonitor.scheduleBlockedProcesses.size} processes")
            sb.ln("Standalone Blocked : ${ProcessMonitor.standaloneBlockedProcesses.size} processes")
            sb.ln("Daily Cap Blocked  : ${ProcessMonitor.dailyAllowanceBlockedProcesses.size} processes")
            sb.ln("Launcher Allowed   : ${ProcessMonitor.launcherAllowedProcesses.size} processes")
            sb.ln("Nuclear Mode       : ${NuclearMode.isActive}")
        } catch (_: Throwable) { sb.ln("  (enforcement state unavailable)") }

        try {
            sb.ln("Sound Aversion     : ${SoundAversion.isEnabled}")
        } catch (_: Throwable) {}
    }

    private fun appendDatabaseInfo(sb: StringBuilder) {
        try {
            val dbDir  = File(System.getProperty("user.home") + "/.focusflow")
            val dbFile = File(dbDir, "focusflow.db")
            sb.ln("DB Path      : ${dbFile.absolutePath}")
            sb.ln("DB Exists    : ${dbFile.exists()}")
            if (dbFile.exists()) {
                sb.ln("DB Size      : ${dbFile.length() / 1024L} KB")
                sb.ln("DB Modified  : ${java.util.Date(dbFile.lastModified())}")
            }
            // Check for WAL / SHM files (indicates a transaction was open at crash time)
            val wal = File(dbDir, "focusflow.db-wal")
            val shm = File(dbDir, "focusflow.db-shm")
            if (wal.exists()) sb.ln("WAL File     : EXISTS (${wal.length() / 1024L} KB) — open transaction at crash?")
            if (shm.exists()) sb.ln("SHM File     : EXISTS")
            // List other focusflow dir contents
            val others = dbDir.listFiles()?.filter {
                it.name != "focusflow.db" && it.name != "focusflow.db-wal" && it.name != "focusflow.db-shm"
            }?.map { "${it.name} (${it.length() / 1024L} KB)" } ?: emptyList()
            if (others.isNotEmpty()) sb.ln("Other files  : ${others.joinToString(", ")}")
        } catch (_: Throwable) { sb.ln("  (database info unavailable)") }
    }

    /**
     * Full thread dump — all live threads with stack traces.
     * Capped at MAX_THREADS to avoid gigantic logs from thread pool explosions.
     * For StackOverflowError, each thread's frames are also capped.
     */
    private fun appendThreadDump(sb: StringBuilder, isSo: Boolean) {
        try {
            val all     = Thread.getAllStackTraces()
            val sorted  = all.entries
                .sortedWith(compareBy({ it.key.isDaemon }, { it.key.name }))
                .take(MAX_THREADS)

            sb.ln("Total threads captured: ${all.size}${if (all.size > MAX_THREADS) " (showing first $MAX_THREADS)" else ""}")
            sb.ln()

            for ((t, stack) in sorted) {
                sb.ln("Thread \"${t.name}\" [id=${t.id} state=${t.state} daemon=${t.isDaemon} priority=${t.priority}]")
                if (stack.isEmpty()) {
                    sb.ln("  (no Java frames — thread in native code)")
                } else {
                    val cap = if (isSo) minOf(MAX_THREAD_FRAMES, stack.size) else stack.size
                    for (i in 0 until cap) sb.ln("  at ${stack[i]}")
                    if (cap < stack.size) sb.ln("  ... ${stack.size - cap} more frames")
                }
                sb.ln()
            }
        } catch (_: Throwable) { sb.ln("  (thread dump unavailable)") }
    }

    private fun appendGcInfo(sb: StringBuilder) {
        try {
            var totalGcCount = 0L
            var totalGcTimeMs = 0L
            for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
                val count = gc.collectionCount
                val time  = gc.collectionTime
                sb.ln("${gc.name.padEnd(30)} : ${if (count < 0) "?" else "$count collections"}, ${if (time < 0) "?" else "${time} ms"}")
                if (count > 0) totalGcCount += count
                if (time  > 0) totalGcTimeMs += time
            }
            if (totalGcCount > 0) sb.ln("Total GC     : $totalGcCount collections, ${totalGcTimeMs} ms cumulative pause")
        } catch (_: Throwable) { sb.ln("  (GC info unavailable)") }
    }

    // ── Log file writing ───────────────────────────────────────────────────────

    /**
     * Tries three log destinations in order; returns the first that succeeds.
     * Also always writes the first ~4 KB of the report to stderr so there's
     * always a record even if all file paths fail.
     */
    private fun writeLog(content: String, tsFile: String): File? {
        val filename = "FocusFlow-crash-$tsFile.log"

        // Always write header to stderr regardless of file success
        content.lines().take(30).forEach { System.err.println(it) }

        val candidates = buildList<File> {
            // 1. Desktop
            try {
                val desktop = File(System.getProperty("user.home"), "Desktop")
                if (desktop.isDirectory) add(File(desktop, filename))
            } catch (_: Throwable) {}

            // 2. ~/.focusflow/
            try {
                add(File(System.getProperty("user.home") + "/.focusflow/$filename"))
            } catch (_: Throwable) {}

            // 3. System temp
            try {
                add(File(System.getProperty("java.io.tmpdir", System.getProperty("user.home")), filename))
            } catch (_: Throwable) {}
        }

        for (candidate in candidates) {
            try {
                candidate.parentFile?.mkdirs()
                candidate.writeText(content, Charsets.UTF_8)
                return candidate
            } catch (_: Throwable) {
                // Try next destination
            }
        }

        // All destinations failed — full content to stderr
        System.err.println("[FocusFlow] All log destinations failed. Full crash report:")
        System.err.println(content)
        return null
    }

    // ── Safety cleanup ─────────────────────────────────────────────────────────

    /**
     * Best-effort pre-crash cleanup.
     * Every call is individually guarded — one failure must not block the others.
     * Goal: leave Windows in a usable state if the app dies mid-session.
     */
    private fun safetyCleanup() {
        // Order matters: restore taskbar/windows first, then disable enforcement
        try { FocusLauncherService.emergencyRestoreWindows() } catch (_: Throwable) {}
        try { NuclearMode.disable()                          } catch (_: Throwable) {}
        try { ProcessMonitor.sessionActive   = false         } catch (_: Throwable) {}
        try { ProcessMonitor.alwaysOnEnabled = false         } catch (_: Throwable) {}
        try { ProcessMonitor.scheduleBlockedProcesses        = emptySet() } catch (_: Throwable) {}
        try { ProcessMonitor.standaloneBlockedProcesses      = emptySet() } catch (_: Throwable) {}
        try { ProcessMonitor.dailyAllowanceBlockedProcesses  = emptySet() } catch (_: Throwable) {}
        try { ProcessMonitor.launcherAllowedProcesses        = emptySet() } catch (_: Throwable) {}
        try { FocusSessionService.end(completed = false)     } catch (_: Throwable) {}
        // Mark in DB that a crash occurred — useful for next-launch recovery dialogs
        try { Database.setSetting("last_crash_version", appVersion) } catch (_: Throwable) {}
        try { Database.setSetting("last_crash_ts", LocalDateTime.now().toString()) } catch (_: Throwable) {}
    }

    // ── User notification ──────────────────────────────────────────────────────

    private fun notifyUser(logFile: File?, throwable: Throwable, source: String) {
        val exType  = throwable.javaClass.simpleName
        val exMsg   = throwable.message?.take(160) ?: "(no message)"
        val logPath = logFile?.absolutePath ?: "(log could not be written — check stderr)"

        val isOom   = throwable is OutOfMemoryError
        val isSo    = throwable is StackOverflowError

        val title = when {
            isOom -> "FocusFlow — Out of Memory"
            isSo  -> "FocusFlow — Stack Overflow"
            else  -> "FocusFlow — Unexpected Error"
        }

        val message = buildString {
            appendLine("FocusFlow has encountered an unexpected error and needs to close.")
            appendLine()
            appendLine("Type   : $exType")
            if (exMsg.isNotBlank()) appendLine("Detail : $exMsg")
            appendLine("Source : $source")
            appendLine()
            appendLine("A crash report has been saved to:")
            appendLine(logPath)
            appendLine()
            when {
                isOom -> appendLine("Tip: This was an out-of-memory error. FocusFlow's default heap is 512 MB. If this recurs, try closing other apps or reducing the number of blocked apps loaded at once.")
                isSo  -> appendLine("Tip: This was a stack overflow — typically caused by infinite recursion in a recent enforcement change.")
                else  -> appendLine("Please share the crash log when reporting this issue.")
            }
            appendLine()
            appendLine("Windows enforcement has been disabled and your taskbar has been restored.")
        }

        // Swing JOptionPane is most reliable — works even when Compose is dead
        try {
            val options = arrayOf("Send Report via Email", "Open Log Folder", "OK")
            val showDialog: () -> Unit = {
                val choice = javax.swing.JOptionPane.showOptionDialog(
                    null,
                    message,
                    title,
                    javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.ERROR_MESSAGE,
                    null,
                    options,
                    options[2]
                )
                when (choice) {
                    0 -> sendReportEmail(logFile, throwable, source)
                    1 -> openLogFolder(logFile)
                }
            }

            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                showDialog()
            } else {
                val latch = java.util.concurrent.CountDownLatch(1)
                javax.swing.SwingUtilities.invokeLater {
                    try { showDialog() } finally { latch.countDown() }
                }
                latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
            // Swing is also broken — try system tray as last notification attempt
            try {
                SystemTrayManager.showNotification(title, "Crash log: $logPath")
                Thread.sleep(2000)
            } catch (_: Throwable) {}
        }
    }

    private fun sendReportEmail(logFile: File?, throwable: Throwable, source: String) {
        try {
            val subject = "FocusFlow Crash Report – v$appVersion – ${throwable.javaClass.simpleName}"
            val body = buildString {
                appendLine("Hi FocusFlow Support,")
                appendLine()
                appendLine("I experienced a crash and would like to report it.")
                appendLine()
                appendLine("Exception : ${throwable.javaClass.name}")
                appendLine("Message   : ${throwable.message ?: "(none)"}")
                appendLine("Source    : $source")
                appendLine("Version   : $appVersion")
                appendLine("OS        : ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
                appendLine()
                if (logFile != null) {
                    appendLine("Log file  : ${logFile.absolutePath}")
                    appendLine("(Please attach the log file above to this email.)")
                    appendLine()
                    // Include the first portion of the log in the body for convenience
                    val preview = try { logFile.readText(Charsets.UTF_8).take(4000) } catch (_: Throwable) { "" }
                    if (preview.isNotBlank()) {
                        appendLine("──── Crash log preview ────")
                        appendLine(preview)
                        if (preview.length >= 4000) appendLine("\n[...truncated — see attached file for full report]")
                    }
                } else {
                    appendLine("Note: The crash log could not be written to disk. Please describe what you were doing when the crash occurred.")
                }
            }

            val subjectEnc = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
            val bodyEnc    = java.net.URLEncoder.encode(body,    "UTF-8").replace("+", "%20")
            val uri        = java.net.URI("mailto:$SUPPORT_EMAIL?subject=$subjectEnc&body=$bodyEnc")

            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.MAIL)) {
                desktop.mail(uri)
            } else {
                // Fallback: open browser with mailto link
                desktop.browse(uri)
            }
        } catch (_: Throwable) {
            // Last resort: show the email address so user can copy it
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Please email your crash log manually to: $SUPPORT_EMAIL\nLog file: ${logFile?.absolutePath ?: "(unavailable)"}",
                "Send Report",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun openLogFolder(logFile: File?) {
        try {
            val folder = logFile?.parentFile ?: File(System.getProperty("user.home"), "Desktop")
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(folder)
            }
        } catch (_: Throwable) {}
    }

    // ── Public utilities ───────────────────────────────────────────────────────

    /**
     * Returns all FocusFlow crash log files found on this machine, newest first.
     * Searches: ~/Desktop, ~/.focusflow/, and %TEMP%.
     */
    fun findCrashLogs(): List<File> {
        val home = System.getProperty("user.home", "")
        val searchDirs = listOf(
            File(home, "Desktop"),
            File("$home/.focusflow"),
            File(System.getProperty("java.io.tmpdir", home))
        )
        return searchDirs
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles { f -> f.isFile && f.name.startsWith("FocusFlow-crash-") && f.name.endsWith(".log") }
                    ?.toList() ?: emptyList()
            }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun prop(key: String): String =
        try { System.getProperty(key) ?: "(not set)" } catch (_: Throwable) { "(error reading property)" }

    private fun StringBuilder.ln(s: String = "") {
        append(s)
        append('\n')
    }
}
