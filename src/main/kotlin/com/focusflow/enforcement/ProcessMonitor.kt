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
 *
 * Keyword enforcement:
 *   When keywordBlockerEnabled is true, the foreground window title is also checked against
 *   the blocked-keyword list (Database.getBlockedKeywords()). A keyword match kills the
 *   foreground process and logs a temptation. Keyword checking uses GetWindowTextW via JNA.
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
        dailyAllowanceBlockedProcesses.isNotEmpty() ||
        Database.isKeywordBlockerEnabled()

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
        val now   = System.currentTimeMillis()

        // ── 1. Process-name blocking (app list, schedules, standalone, allowances) ──────
        val blocked = buildSet<String> {
            if (alwaysOnEnabled || sessionActive) addAll(Database.getEnabledBlockProcesses())
            addAll(scheduleBlockedProcesses)
            addAll(standaloneBlockedProcesses)
            addAll(dailyAllowanceBlockedProcesses)
        }

        if (blocked.any { lower == it.lowercase() }) {
            val lastHit = cooldowns[lower] ?: 0L
            if (now - lastHit >= COOLDOWN_MS) {
                cooldowns[lower] = now
                enforceBlock(processName)
            }
            return  // Already handling this process — skip keyword check
        }

        // ── 2. Keyword blocking (foreground window title) ────────────────────────────────
        if (!Database.isKeywordBlockerEnabled()) return
        val keywords = Database.getBlockedKeywords()
        if (keywords.isEmpty()) return

        val title = getForegroundWindowTitle() ?: return
        val titleLower = title.lowercase()
        val matchedKeyword = keywords.firstOrNull { kw -> titleLower.contains(kw.lowercase()) }
            ?: return

        // Cooldown key for keyword hits: "kw:<processName>"
        val kwKey = "kw:$lower"
        val lastKwHit = cooldowns[kwKey] ?: 0L
        if (now - lastKwHit < COOLDOWN_MS) return
        cooldowns[kwKey] = now

        killProcessByName(processName)
        SoundAversion.playBlockAlert()

        val displayName = processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        val reason = "Keyword: \"$matchedKeyword\" in title: \"${title.take(60)}\""
        TemptationLogger.log(processName, "$displayName ($reason)")
        Database.logTemptation(processName, displayName)

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }
    }

    /** Shared kill + log + notify path for process-name block triggers. */
    private suspend fun enforceBlock(processName: String) {
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

        val rule = Database.getBlockRules().find { it.processName.equals(processName, ignoreCase = true) }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        WinEventHook.stop()
        scope.cancel()
    }
}
