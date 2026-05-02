package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.TemptationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ProcessMonitor
 *
 * Dual-mode enforcement engine:
 *   1. WinEventHook (instant) — EVENT_SYSTEM_FOREGROUND fires on every foreground change.
 *      Zero delay between app switch and kill. Equivalent to Android's AccessibilityService.
 *   2. Polling fallback (500ms) — catches processes that don't own a top-level window
 *      or cases where WinEventHook registration fails.
 *
 * On block detection:
 *   1. Kills the process via ProcessHandle + taskkill fallback
 *   2. Plays aversion tone (SoundAversion)
 *   3. Shows BlockOverlay composable
 *   4. Logs the attempt to temptation log + SQLite
 *   5. Applies Windows Firewall rule if configured for that process
 */
object ProcessMonitor {

    private const val POLL_MS    = 500L
    private const val COOLDOWN_MS = 3000L

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private val _blockedAttempts  = MutableStateFlow(0)
    val blockedAttempts: StateFlow<Int> = _blockedAttempts

    private val _lastBlockedApp   = MutableStateFlow<String?>(null)
    val lastBlockedApp: StateFlow<String?> = _lastBlockedApp

    private val cooldowns = mutableMapOf<String, Long>()

    var sessionActive:    Boolean = false
    var alwaysOnEnabled: Boolean = false

    /** Called from WinEventHook callback for instant (zero-delay) enforcement. */
    fun onForegroundChanged(processName: String) {
        if (!sessionActive && !alwaysOnEnabled) return
        scope.launch { checkProcess(processName) }
    }

    fun start() {
        if (monitorJob?.isActive == true) return

        // Hook-based instant detection
        if (isWindows) {
            WinEventHook.start { pName -> onForegroundChanged(pName) }
        }

        // Polling loop (fallback / processes without windows)
        monitorJob = scope.launch {
            while (isActive) {
                if (sessionActive || alwaysOnEnabled) {
                    tickPoll()
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        WinEventHook.stop()
    }

    /** Polling tick — same logic as hook but driven by timer. */
    private suspend fun tickPoll() {
        if (!isWindows) return
        val processName = getForegroundProcessName() ?: return
        checkProcess(processName)
    }

    private suspend fun checkProcess(processName: String) {
        val blocked = Database.getEnabledBlockProcesses()
        if (!blocked.any { processName.equals(it, ignoreCase = true) }) return

        val now     = System.currentTimeMillis()
        val lastHit = cooldowns[processName] ?: 0L
        if (now - lastHit < COOLDOWN_MS) return
        cooldowns[processName] = now

        // Kill immediately
        killProcessByName(processName)

        // Sound aversion feedback
        SoundAversion.playBlockAlert()

        // Logging
        val displayName = processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        TemptationLogger.log(processName, displayName)
        Database.logTemptation(processName, displayName)

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        // Overlay — must run on Compose main thread
        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }

        // Firewall rule if configured
        val rule = Database.getBlockRules().find {
            it.processName.equals(processName, ignoreCase = true)
        }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        WinEventHook.stop()
        scope.cancel()
    }
}
