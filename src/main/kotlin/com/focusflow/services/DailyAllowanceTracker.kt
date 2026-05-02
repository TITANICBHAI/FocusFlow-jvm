package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.DailyAllowance
import com.focusflow.enforcement.ProcessMonitor
import kotlinx.coroutines.*
import java.awt.TrayIcon
import java.time.LocalDate

/**
 * DailyAllowanceTracker
 *
 * Polls running processes every 10 s and accumulates per-app foreground time.
 * When an app exceeds its daily allowance it is killed via ProcessHandle and
 * added to a "blocked today" set so it stays blocked for the rest of the day.
 * The ProcessMonitor.dailyAllowanceBlockedProcesses field is kept in sync so
 * WinEventHook enforcement also covers allowance-blocked apps instantly.
 * The usage map resets at midnight automatically.
 */
object DailyAllowanceTracker {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val usageSeconds = mutableMapOf<String, Long>()
    private val blockedToday = mutableSetOf<String>()

    @Volatile private var allowances: List<DailyAllowance> = emptyList()
    @Volatile private var trackingDate: LocalDate = LocalDate.now()

    /** Exposed to UI for display. */
    val blockedProcesses: Set<String> get() = synchronized(blockedToday) { blockedToday.toSet() }

    fun start() {
        if (job?.isActive == true) return
        allowances = Database.getDailyAllowances()
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
        ProcessMonitor.dailyAllowanceBlockedProcesses = emptySet()
    }

    fun reload() {
        allowances = Database.getDailyAllowances()
    }

    private fun tick() {
        val today = LocalDate.now()
        if (today != trackingDate) {
            synchronized(usageSeconds) { usageSeconds.clear() }
            synchronized(blockedToday) { blockedToday.clear() }
            ProcessMonitor.dailyAllowanceBlockedProcesses = emptySet()
            trackingDate = today
        }

        if (allowances.isEmpty()) return

        val processHandles: List<ProcessHandle> = try {
            ProcessHandle.allProcesses().toList()
        } catch (_: Exception) { return }

        val runningMap: Map<String, List<ProcessHandle>> = processHandles
            .filter { ph -> ph.info().command().isPresent }
            .groupBy { ph -> java.io.File(ph.info().command().get()).name.lowercase() }

        for (allowance in allowances) {
            val proc = allowance.processName.lowercase()
            val isRunning = runningMap.containsKey(proc)

            val alreadyBlocked = synchronized(blockedToday) { proc in blockedToday }
            if (alreadyBlocked) {
                if (isRunning) runningMap[proc]?.forEach { ph -> runCatching { ph.destroyForcibly() } }
                continue
            }

            if (isRunning) {
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
                    runningMap[proc]?.forEach { ph -> runCatching { ph.destroyForcibly() } }
                    SystemTrayManager.showNotification(
                        "Daily Limit Reached",
                        "${allowance.displayName} has used all ${allowance.allowanceMinutes}m today. Blocked until midnight.",
                        TrayIcon.MessageType.WARNING
                    )
                }
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
