package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.TemptationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * ProcessMonitor
 *
 * Dual-mode enforcement engine:
 *   1. WinEventHook (instant) — EVENT_SYSTEM_FOREGROUND fires on every foreground change.
 *      Zero delay between app switch and kill. Equivalent to Android's AccessibilityService.
 *   2. Polling fallback (500ms) — catches processes that don't own a top-level window
 *      or cases where WinEventHook registration fails.
 *
 * Block sources (union of all sets, evaluated on every event/poll):
 *   - alwaysOnEnabled / sessionActive    — all block_rules with enabled=1
 *   - scheduleBlockedProcesses           — injected by BlockScheduleService (time-window)
 *   - standaloneBlockedProcesses         — injected by StandaloneBlockService (timed block)
 *   - dailyAllowanceBlockedProcesses     — injected by DailyAllowanceTracker (usage cap)
 */
object ProcessMonitor {

    private const val POLL_MS     = 500L
    private const val COOLDOWN_MS = 3000L

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private val _blockedAttempts = MutableStateFlow(0)
    val blockedAttempts: StateFlow<Int> = _blockedAttempts

    private val _lastBlockedApp = MutableStateFlow<String?>(null)
    val lastBlockedApp: StateFlow<String?> = _lastBlockedApp

    private val cooldowns = ConcurrentHashMap<String, Long>()

    var sessionActive:   Boolean = false
    var alwaysOnEnabled: Boolean = false

    /** Injected by BlockScheduleService — processes blocked by recurring schedule right now. */
    @Volatile var scheduleBlockedProcesses: Set<String> = emptySet()

    /** Injected by StandaloneBlockService — processes blocked by timed standalone block. */
    @Volatile var standaloneBlockedProcesses: Set<String> = emptySet()

    /** Injected by DailyAllowanceTracker — processes whose daily cap has been exceeded. */
    @Volatile var dailyAllowanceBlockedProcesses: Set<String> = emptySet()

    /** Called from WinEventHook callback for instant (zero-delay) enforcement. */
    fun onForegroundChanged(processName: String) {
        if (!isAnyEnforcementActive()) return
        scope.launch { checkProcess(processName) }
    }

    private fun isAnyEnforcementActive(): Boolean =
        sessionActive || alwaysOnEnabled ||
        scheduleBlockedProcesses.isNotEmpty() ||
        standaloneBlockedProcesses.isNotEmpty() ||
        dailyAllowanceBlockedProcesses.isNotEmpty()

    fun start() {
        if (monitorJob?.isActive == true) return

        if (isWindows) {
            WinEventHook.start { pName -> onForegroundChanged(pName) }
        }

        monitorJob = scope.launch {
            while (isActive) {
                if (isAnyEnforcementActive()) tickPoll()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        WinEventHook.stop()
    }

    private suspend fun tickPoll() {
        if (!isWindows) return
        val processName = getForegroundProcessName() ?: return
        checkProcess(processName)
    }

    private suspend fun checkProcess(processName: String) {
        val lower = processName.lowercase()

        // Build union of all currently active block sets
        val blocked = buildSet<String> {
            if (alwaysOnEnabled || sessionActive) addAll(Database.getEnabledBlockProcesses())
            addAll(scheduleBlockedProcesses)
            addAll(standaloneBlockedProcesses)
            addAll(dailyAllowanceBlockedProcesses)
        }

        if (blocked.none { lower == it.lowercase() }) return

        val now     = System.currentTimeMillis()
        val lastHit = cooldowns[lower] ?: 0L
        if (now - lastHit < COOLDOWN_MS) return
        cooldowns[lower] = now

        killProcessByName(processName)
        SoundAversion.playBlockAlert()

        val displayName = processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        TemptationLogger.log(processName, displayName)
        Database.logTemptation(processName, displayName)

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }

        val rule = Database.getBlockRules().find { it.processName.equals(lower, ignoreCase = true) }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        WinEventHook.stop()
        scope.cancel()
    }
}
