package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.TemptationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ProcessMonitor
 *
 * Polls the foreground window every POLL_MS milliseconds.
 * When a blocked process is detected during an active session:
 *   1. Kills the process immediately
 *   2. Shows the block overlay window
 *   3. Logs the attempt to the temptation log
 *   4. Applies network block rule (if configured for this process)
 *
 * This is the JVM equivalent of Android's AppBlockerAccessibilityService.
 * Limitation vs Android: AccessibilityService fires on every window event
 * (instant). This monitor polls every 500ms — up to 500ms delay before kill.
 */
object ProcessMonitor {

    private const val POLL_MS = 500L
    private const val COOLDOWN_MS = 3000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private val _blockedAttempts = MutableStateFlow(0)
    val blockedAttempts: StateFlow<Int> = _blockedAttempts

    private val _lastBlockedApp = MutableStateFlow<String?>(null)
    val lastBlockedApp: StateFlow<String?> = _lastBlockedApp

    private val cooldowns = mutableMapOf<String, Long>()

    var sessionActive: Boolean = false
    var alwaysOnEnabled: Boolean = false

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                if (sessionActive || alwaysOnEnabled) {
                    tick()
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun tick() {
        if (!isWindows) return

        val processName = getForegroundProcessName() ?: return
        val blocked = Database.getEnabledBlockProcesses()

        if (!blocked.any { processName.equals(it, ignoreCase = true) }) return

        val now = System.currentTimeMillis()
        val lastHit = cooldowns[processName] ?: 0L
        if (now - lastHit < COOLDOWN_MS) return
        cooldowns[processName] = now

        // Kill the process
        killProcessByName(processName)

        // Log the attempt
        val displayName = processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        TemptationLogger.log(processName, displayName)
        Database.logTemptation(processName, displayName)

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        // Show overlay on the UI thread
        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }

        // Apply network rule if configured
        val rule = Database.getBlockRules().find {
            it.processName.equals(processName, ignoreCase = true)
        }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
