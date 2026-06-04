package com.focusflow.services

import com.focusflow.data.Database
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
    private var job: Job? = null

    private val usageSeconds = mutableMapOf<String, Long>()
    private val blockedToday = mutableSetOf<String>()

    @Volatile private var allowances: List<DailyAllowance> = emptyList()
    @Volatile private var trackingDate: LocalDate = LocalDate.now()

    private val ownPid: Long = ProcessHandle.current().pid()

    // Counts tick() calls; usage is flushed to DB every 6 ticks (~60 seconds).
    private var persistTick = 0

    /** Exposed to UI for display. */
    val blockedProcesses: Set<String> get() = synchronized(blockedToday) { blockedToday.toSet() }

    fun start() {
        if (job?.isActive == true) return
        allowances = Database.getDailyAllowances()

        // Restore today's usage from DB so a reboot doesn't reset the counters.
        val today = LocalDate.now()
        val persisted = Database.getDailyUsage(today)
        synchronized(usageSeconds) { usageSeconds.putAll(persisted) }

        // Re-populate blockedToday for any process that already hit its limit.
        val loadedAllowances = allowances
        synchronized(blockedToday) {
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

        if (allowances.isEmpty()) return

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
                val next: Long
                synchronized(usageSeconds) {
                    val prev = usageSeconds.getOrDefault(proc, 0L)
                    next = prev + 10L
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
