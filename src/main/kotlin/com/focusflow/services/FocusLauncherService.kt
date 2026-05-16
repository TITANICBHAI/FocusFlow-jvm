package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.User32Extra
import com.focusflow.enforcement.isWindows
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.TrayIcon

data class FocusLauncherApp(
    val processName: String,
    val displayName: String,
    val exePath: String? = null
)

object FocusLauncherService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isActive             = MutableStateFlow(false)
    val isActive: StateFlow<Boolean>  = _isActive

    private val _isHardLocked              = MutableStateFlow(false)
    val isHardLocked: StateFlow<Boolean>   = _isHardLocked

    private val _sessionApps                       = MutableStateFlow<List<FocusLauncherApp>>(emptyList())
    val sessionApps: StateFlow<List<FocusLauncherApp>> = _sessionApps

    private val _breakActive              = MutableStateFlow(false)
    val breakActive: StateFlow<Boolean>   = _breakActive

    private val _breakRemainingSeconds      = MutableStateFlow(0)
    val breakRemainingSeconds: StateFlow<Int> = _breakRemainingSeconds

    private val _sessionEndMs           = MutableStateFlow(0L)
    val sessionEndMs: StateFlow<Long>   = _sessionEndMs

    private val _sessionStartMs         = MutableStateFlow(0L)
    val sessionStartMs: StateFlow<Long> = _sessionStartMs

    private var breakJob:        Job? = null
    private var sessionTimerJob: Job? = null

    private const val BREAK_USED_KEY  = "launcher_break_used_date"
    private const val CRASH_GUARD_KEY = "launcher_crash_guard"
    private const val HARD_LOCK_KEY   = "launcher_hard_locked"

    // ── Public API ─────────────────────────────────────────────────────────

    fun canTakeBreak(): Boolean {
        val usedDate = Database.getSetting(BREAK_USED_KEY) ?: return true
        return usedDate != java.time.LocalDate.now().toString()
    }

    fun enter(apps: List<FocusLauncherApp>, durationMinutes: Int?) {
        _isActive.value      = true
        _sessionApps.value   = apps
        _sessionStartMs.value = System.currentTimeMillis()
        _sessionEndMs.value  = if (durationMinutes != null)
            System.currentTimeMillis() + durationMinutes * 60_000L
        else 0L

        Database.setSetting(CRASH_GUARD_KEY, "true")

        val allowedSet = apps.map { it.processName.lowercase() }.toSet()
        ProcessMonitor.launcherAllowedProcesses = allowedSet

        NuclearMode.enable()
        hideTaskbar()

        if (durationMinutes != null) startSessionTimer()

        SystemTrayManager.showNotification(
            "Focus Launcher Active",
            "Kiosk mode enabled. ${apps.size} app(s) available.",
            TrayIcon.MessageType.INFO
        )
    }

    fun exit() {
        _isActive.value      = false
        _isHardLocked.value  = false
        _breakActive.value   = false
        _sessionApps.value   = emptyList()
        _sessionEndMs.value  = 0L
        _sessionStartMs.value = 0L

        breakJob?.cancel()
        sessionTimerJob?.cancel()
        breakJob        = null
        sessionTimerJob = null

        Database.setSetting(CRASH_GUARD_KEY, "false")
        Database.setSetting(HARD_LOCK_KEY, "false")

        ProcessMonitor.launcherAllowedProcesses = emptySet()

        if (NuclearMode.isActive) NuclearMode.disable()
        showTaskbar()

        SystemTrayManager.updateTooltip("FocusFlow — Ready")
    }

    fun toggleHardLock() {
        val newValue = !_isHardLocked.value
        _isHardLocked.value = newValue
        Database.setSetting(HARD_LOCK_KEY, newValue.toString())
    }

    /**
     * Start a 5-minute break — call this only after PIN has been verified by the UI.
     * Restores the taskbar, clears the launcher allowed-list (so normal Windows is
     * accessible), and starts a countdown that re-engages the launcher automatically.
     */
    fun startBreak() {
        if (_breakActive.value) return
        _breakActive.value        = true
        _breakRemainingSeconds.value = BREAK_SECONDS

        Database.setSetting(BREAK_USED_KEY, java.time.LocalDate.now().toString())

        ProcessMonitor.launcherAllowedProcesses = emptySet()
        if (NuclearMode.isActive) NuclearMode.disable()
        showTaskbar()

        breakJob = scope.launch {
            var remaining = BREAK_SECONDS
            while (remaining > 0 && _breakActive.value) {
                delay(1_000)
                remaining--
                _breakRemainingSeconds.value = remaining
            }
            if (_breakActive.value) endBreak()
        }

        SystemTrayManager.showNotification(
            "5-Minute Break",
            "Focus Launcher will re-engage in 5 minutes.",
            TrayIcon.MessageType.INFO
        )
    }

    fun endBreak() {
        if (!_breakActive.value) return
        breakJob?.cancel()
        breakJob = null
        _breakActive.value           = false
        _breakRemainingSeconds.value = 0

        if (_isActive.value) {
            val allowedSet = _sessionApps.value.map { it.processName.lowercase() }.toSet()
            ProcessMonitor.launcherAllowedProcesses = allowedSet
            NuclearMode.enable()
            hideTaskbar()
            SystemTrayManager.showNotification(
                "Focus Launcher Resumed",
                "Kiosk mode re-engaged. Stay focused.",
                TrayIcon.MessageType.WARNING
            )
        }
    }

    // ── Crash recovery ─────────────────────────────────────────────────────

    /**
     * Called at startup. If the app crashed while the launcher was active,
     * the crash guard will still be set — restore Windows to normal (safety-first).
     */
    fun loadFromDb() {
        val crashed = Database.getSetting(CRASH_GUARD_KEY) == "true"
        if (crashed) {
            showTaskbar()
            ProcessMonitor.launcherAllowedProcesses = emptySet()
            if (NuclearMode.isActive) NuclearMode.disable()
            Database.setSetting(CRASH_GUARD_KEY, "false")
            Database.setSetting(HARD_LOCK_KEY, "false")
        }
    }

    // ── Session elapsed time helper ─────────────────────────────────────────

    fun elapsedSeconds(): Long {
        val start = _sessionStartMs.value
        if (start == 0L) return 0L
        return (System.currentTimeMillis() - start) / 1000L
    }

    fun remainingSeconds(): Long {
        val endMs = _sessionEndMs.value
        if (endMs == 0L) return -1L
        return maxOf(0L, (endMs - System.currentTimeMillis()) / 1000L)
    }

    // ── OS-level taskbar control ────────────────────────────────────────────

    private fun hideTaskbar() {
        if (!isWindows) return
        try {
            val u32 = User32Extra.INSTANCE
            val taskbar = u32.FindWindowW("Shell_TrayWnd", null)
            if (taskbar != null) u32.ShowWindow(taskbar, SW_HIDE)
            val secondary = u32.FindWindowW("Shell_SecondaryTrayWnd", null)
            if (secondary != null) u32.ShowWindow(secondary, SW_HIDE)
        } catch (_: Exception) {}
    }

    private fun showTaskbar() {
        if (!isWindows) return
        try {
            val u32 = User32Extra.INSTANCE
            val taskbar = u32.FindWindowW("Shell_TrayWnd", null)
            if (taskbar != null) u32.ShowWindow(taskbar, SW_SHOW)
            val secondary = u32.FindWindowW("Shell_SecondaryTrayWnd", null)
            if (secondary != null) u32.ShowWindow(secondary, SW_SHOW)
        } catch (_: Exception) {}
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            while (_isActive.value) {
                delay(1_000)
                val endMs = _sessionEndMs.value
                if (endMs > 0L && System.currentTimeMillis() >= endMs) {
                    exit()
                    SystemTrayManager.showNotification(
                        "Focus Launcher Ended",
                        "Your session is complete. Well done!",
                        TrayIcon.MessageType.INFO
                    )
                    return@launch
                }
            }
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────

    private const val SW_HIDE = 0
    private const val SW_SHOW = 5
    private const val BREAK_SECONDS = 5 * 60
}
