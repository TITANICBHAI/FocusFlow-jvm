package com.focusflow.services

import com.focusflow.data.*
import com.focusflow.data.models.DailyAllowance
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.getForegroundProcessName
import com.focusflow.enforcement.isWindows
import com.focusflow.enforcement.killProcessByName
import kotlinx.coroutines.*
import java.awt.TrayIcon
import java.time.LocalDate

/**
 * DailyAllowanceTracker
 *
 * Polls running processes every 10 s and accumulates per-app foreground time.
 * When an app exceeds its daily allowance it is killed and added to a "blocked today"
 * set so it stays blocked for the rest of the day.
 * The ProcessMonitor.dailyAllowanceBlockedProcesses field is kept in sync so
 * WinEventHook enforcement also covers allowance-blocked apps instantly.
 * The usage map resets at midnight automatically.
 *
 * Process killing uses taskkill on Windows (via killProcessByName from WinApiBindings)
 * to avoid the "destroy of current process not allowed" IllegalStateException that
 * ProcessHandle.destroyForcibly() throws when the JVM's own PID appears in allProcesses().
 */
object DailyAllowanceTracker {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // @Volatile: start() writes on the Compose application thread; stop() reads on the
    // "FocusFlow-Shutdown" daemon thread (Main.kt). Without @Volatile the shutdown
    // thread may see a stale null and skip the cancel, leaving the loop running.
    @Volatile private var job: Job? = null

    private val usageSeconds = mutableMapOf<String, Long>()
    private val blockedToday = mutableSetOf<String>()

    @Volatile private var allowances: List<DailyAllowance> = emptyList()
    @Volatile private var trackingDate: LocalDate = LocalDate.now()

    private val ownPid: Long = ProcessHandle.current().pid()

    // Counts tick() calls; usage is flushed to DB every 6 ticks (~60 seconds).
    private var persistTick = 0

    // Wall-clock timestamp of the previous tick (milliseconds).
    // Using this instead of a hard-coded "+10L" eliminates usage drift:
    // ProcessHandle.allProcesses() on Windows can take 100–500 ms, so each
    // loop cycle is 10 000 ms + execution time.  Without correction, a user
    // with a 30-minute allowance effectively gets ~31 minutes before being
    // blocked.  Tracking the real elapsed interval fixes that.
    private var lastTickMs: Long = 0L

    /** Exposed to UI for display. */
    val blockedProcesses: Set<String> get() = synchronized(blockedToday) { blockedToday.toSet() }

    fun start() {
        if (job?.isActive == true) return
        allowances = Database.getDailyAllowances()

        // Restore today's usage from DB so a reboot doesn't reset the counters.
        val today = LocalDate.now()
        val persisted = Database.getDailyUsage(today)
        synchronized(usageSeconds) {
            usageSeconds.clear()  // Discard stale in-memory data before reloading from DB.
            usageSeconds.putAll(persisted)
        }

        // Re-populate blockedToday for any process that already hit its limit.
        val loadedAllowances = allowances
        synchronized(blockedToday) {
            blockedToday.clear()  // Reset before re-evaluating; prevents stale entries accumulating.
            for (a in loadedAllowances) {
                val usedSecs = synchronized(usageSeconds) {
                    usageSeconds.getOrDefault(a.processName.lowercase(), 0L)
                }
                if (usedSecs / 60 >= a.allowanceMinutes) {
                    blockedToday.add(a.processName.lowercase())
                }
            }
            ProcessMonitor.dailyAllowanceBlockedProcesses = blockedToday.toSet()
        }

        lastTickMs = System.currentTimeMillis()
        job = scope.launch {
            while (isActive) {
                tick()
                delay(10_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        flushUsageToDB(trackingDate)
        ProcessMonitor.dailyAllowanceBlockedProcesses = emptySet()
    }

    /**
     * Cancels the background job and wipes all in-memory state.
     * For unit tests ONLY — ensures each test starts with a clean tracker.
     */
    internal fun clearStateForTesting() {
        job?.cancel()
        job = null
        synchronized(usageSeconds) { usageSeconds.clear() }
        synchronized(blockedToday) { blockedToday.clear() }
        allowances = emptyList()
        ProcessMonitor.dailyAllowanceBlockedProcesses = emptySet()
    }

    fun reload() {
        allowances = Database.getDailyAllowances()
    }

    private fun flushUsageToDB(date: LocalDate) {
        val snapshot = synchronized(usageSeconds) { usageSeconds.toMap() }
        snapshot.forEach { (proc, secs) ->
            Database.upsertDailyUsage(date, proc, secs)
        }
    }

    private fun tick() {
        val today = LocalDate.now()
        if (today != trackingDate) {
            // Flush usage accumulated since the last periodic write before clearing —
            // prevents losing up to 10 s of foreground time at midnight rollover.
            flushUsageToDB(trackingDate)
            // Clean up yesterday's persisted rows; keep only today's.
            Database.deleteDailyUsageBefore(today)
            synchronized(usageSeconds) { usageSeconds.clear() }
            synchronized(blockedToday) { blockedToday.clear() }
            ProcessMonitor.dailyAllowanceBlockedProcesses = emptySet()
            trackingDate = today
            persistTick = 0
        }

        // Flush usage to DB every 6 ticks (~60 s) so it survives a crash or reboot.
        persistTick++
        if (persistTick >= 6) {
            persistTick = 0
            flushUsageToDB(today)
        }

        if (allowances.isEmpty()) {
            // Still advance lastTickMs so that when an allowance is added mid-run,
            // the next tick measures elapsed time from NOW — not from the stale
            // startup timestamp.  Without this, a long idle period (all ticks
            // returning early here) followed by the user adding an allowance would
            // cause the next tick to compute a huge elapsed delta and instantly
            // exhaust the new limit in one shot.
            lastTickMs = System.currentTimeMillis()
            return
        }

        // Only the foreground process counts toward its allowance.
        // Background processes running silently should not consume the user's quota.
        val foregroundProcess = if (isWindows) getForegroundProcessName()?.lowercase() else null

        val processHandles: List<ProcessHandle> = try {
            ProcessHandle.allProcesses().toList()
        } catch (_: Exception) { return }

        val runningMap: Map<String, List<ProcessHandle>> = processHandles
            .filter { ph -> ph.pid() != ownPid }
            .mapNotNull { ph ->
                val cmd = ph.info().command().orElse(null) ?: return@mapNotNull null
                java.io.File(cmd).name.lowercase() to ph
            }
            .groupBy({ it.first }, { it.second })

        for (allowance in allowances) {
            val proc = allowance.processName.lowercase()
            val isRunning = runningMap.containsKey(proc)

            val alreadyBlocked = synchronized(blockedToday) { proc in blockedToday }
            if (alreadyBlocked) {
                if (isRunning) killProcess(allowance.processName, runningMap[proc])
                continue
            }

            // On Windows, only accumulate time when this app is the foreground process.
            // On non-Windows (no Win32 foreground API), fall back to running-process check.
            val isForeground = if (isWindows) {
                foregroundProcess != null && foregroundProcess == proc
            } else {
                isRunning
            }

            if (isForeground) {
                // Calculate actual elapsed seconds since the previous tick rather than
                // assuming exactly 10 s.  tick() itself can take 100–500 ms on Windows
                // (ProcessHandle.allProcesses is the bottleneck), so each real cycle is
                // 10 000 ms + execution time.  Using a hard-coded "10" causes the daily
                // allowance to drain slower than real time — the user gets more screen
                // time than their configured limit.
                //
                // lastTickMs is updated once per tick (at the END of tick(), below)
                // so every process checked in this loop uses the same elapsed value,
                // which is correct: they were all checked in the same tick window.
                val nowMs = System.currentTimeMillis()
                val elapsedSecs = ((nowMs - lastTickMs) / 1000L).coerceAtLeast(1L)
                val next: Long
                synchronized(usageSeconds) {
                    val prev = usageSeconds.getOrDefault(proc, 0L)
                    next = prev + elapsedSecs
                    usageSeconds[proc] = next
                }
                val usedMinutes = next / 60
                if (usedMinutes >= allowance.allowanceMinutes) {
                    synchronized(blockedToday) { blockedToday.add(proc) }
                    ProcessMonitor.dailyAllowanceBlockedProcesses =
                        synchronized(blockedToday) { blockedToday.toSet() }
                    killProcess(allowance.processName, runningMap[proc])
                    SystemTrayManager.showNotification(
                        "Daily Limit Reached",
                        "${allowance.displayName} has used all ${allowance.allowanceMinutes}m today. Blocked until midnight.",
                        TrayIcon.MessageType.WARNING
                    )
                }
            }
        }

        // Update lastTickMs at the end of tick so the next cycle measures
        // the interval from when THIS tick completed, not when it started.
        // This means the elapsed delta includes tick() execution time, which
        // is exactly what we want: if the user's app was foreground during
        // a 10.3 s cycle, we credit 10.3 s (not 10 s) of usage.
        lastTickMs = System.currentTimeMillis()
    }

    /**
     * Kill a process by name. On Windows, delegates to taskkill (avoids
     * ProcessHandle.destroyForcibly() "destroy of current process" crash).
     * On other platforms, uses ProcessHandle with own-PID filter.
     */
    private fun killProcess(processName: String, handles: List<ProcessHandle>?) {
        if (isWindows) {
            killProcessByName(processName)
        } else {
            handles?.forEach { ph ->
                if (ph.pid() != ownPid) runCatching { ph.destroyForcibly() }
            }
        }
    }

    fun getUsageMinutes(processName: String): Long =
        synchronized(usageSeconds) { usageSeconds.getOrDefault(processName.lowercase(), 0L) / 60 }

    fun getRemainingMinutes(allowance: DailyAllowance): Long =
        maxOf(0L, allowance.allowanceMinutes.toLong() - getUsageMinutes(allowance.processName))

    fun getUsageSummary(): List<Pair<DailyAllowance, Long>> =
        allowances.map { a -> a to getUsageMinutes(a.processName) }
}
