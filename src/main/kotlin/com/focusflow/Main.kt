package com.focusflow

import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.focusflow.data.Database
import com.focusflow.enforcement.KillSwitchService
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.WatchdogInstaller
import com.focusflow.services.*
import com.focusflow.services.FocusLauncherService

fun main() = application {
    // Global crash handler — log uncaught exceptions instead of silently dying.
    // Also unconditionally restores the Windows taskbar so a crash mid-kiosk-session
    // never leaves the user with a permanently hidden taskbar.
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        val logFile = java.io.File(System.getProperty("user.home") + "/.focusflow/crash.log")
        logFile.parentFile.mkdirs()
        logFile.appendText("[${java.time.LocalDateTime.now()}] CRASH on thread ${t.name}:\n${e.stackTraceToString()}\n\n")
        System.err.println("[FocusFlow] Uncaught exception on ${t.name}: ${e.message}")
        // Safety: restore Windows state immediately on any unhandled crash
        try { FocusLauncherService.emergencyRestoreWindows() } catch (_: Throwable) {}
    }

    try {
        Database.init()
    } catch (e: Exception) {
        // init() already has internal recovery — this is the final safety net
        val logFile = java.io.File(System.getProperty("user.home") + "/.focusflow/crash.log")
        logFile.parentFile?.mkdirs()
        logFile.appendText("[${java.time.LocalDateTime.now()}] FATAL: Database.init() threw: ${e.stackTraceToString()}\n\n")
    }

    // Auto-backup: daily rolling backup of SQLite database
    AutoBackupService.start()

    ProcessMonitor.alwaysOnEnabled   = Database.getSetting("always_on_enforcement") == "true"
    SoundAversion.isEnabled          = Database.getSetting("sound_aversion") != "false"
    FocusSessionService.pomodoroMode = Database.getSetting("pomodoro_mode") == "true"

    ProcessMonitor.start()

    BreakEnforcer.loadSettings()
    NuclearMode.loadFromDb()
    TaskAlarmService.start()

    // Kill switch — restore today's remaining budget from the DB
    KillSwitchService.loadFromDb()

    // Watchdog — register (or overwrite) the Task Scheduler entry that relaunches
    // FocusFlow every 2 minutes if it isn't running. No admin rights required.
    WatchdogInstaller.install()

    // Recurring tasks — auto-generate daily/weekday/weekly copies each morning
    RecurringTaskService.start()

    // Block schedules — recurring time-window enforcement
    BlockScheduleService.start()

    // Standalone block — restore a block that survived a restart
    StandaloneBlockService.loadFromDb()

    // Focus Launcher — restore taskbar and clear crash guard if we crashed while locked
    FocusLauncherService.loadFromDb()

    // Daily allowances — per-app usage caps that reset at midnight
    DailyAllowanceTracker.start()

    // Sync existing FocusFlow firewall rules from Windows Firewall on startup
    // so rules created in a previous session are recognised without re-applying.
    NetworkBlocker.syncFromFirewall()

    // Start the hosts-file integrity monitor — re-applies blocks if an external
    // tool (antivirus, etc.) removes our entries while the app is running.
    if (HostsBlocker.getBlockedDomains().isNotEmpty()) {
        HostsBlocker.startMonitor()
    }

    WeeklyReportService.onReportReady = { report ->
        NotificationService.weeklyReport(report)
    }
    WeeklyReportService.startScheduler()

    var windowVisible by remember { mutableStateOf(true) }

    val windowState = rememberWindowState(
        width     = 1100.dp,
        height    = 720.dp,
        placement = WindowPlacement.Floating
    )

    val launcherActive   by FocusLauncherService.isActive.collectAsState()
    val launcherBreak    by FocusLauncherService.breakActive.collectAsState()
    val isKioskMode      = launcherActive && !launcherBreak

    LaunchedEffect(isKioskMode) {
        if (isKioskMode) {
            windowVisible = true
            windowState.placement = WindowPlacement.Fullscreen
        } else if (!launcherActive) {
            windowState.placement = WindowPlacement.Floating
        }
    }

    // Dynamic title: tracks active session countdown in the OS window title bar
    val sessionState by FocusSessionService.state.collectAsState()
    val windowTitle = when {
        sessionState.isActive && sessionState.isPaused ->
            "FocusFlow — ${sessionState.taskName} (paused)"
        sessionState.isActive -> {
            val remSec = (sessionState.totalSeconds - sessionState.elapsedSeconds).coerceAtLeast(0)
            val remMin = remSec / 60
            val remS   = remSec % 60
            "FocusFlow — ${sessionState.taskName} (${remMin}m ${remS.toString().padStart(2,'0')}s left)"
        }
        else -> "FocusFlow"
    }

    // Keep the kill-switch tray menu item label in sync with the live countdown
    val ksRemaining by KillSwitchService.remainingSecondsToday.collectAsState()
    val ksActive    by KillSwitchService.isActive.collectAsState()
    LaunchedEffect(ksRemaining, ksActive) {
        val m = ksRemaining / 60
        val s = (ksRemaining % 60).toString().padStart(2, '0')
        val label = when {
            ksRemaining <= 0 -> "Emergency Break (exhausted for today)"
            ksActive         -> "Stop Break — ${m}m ${s}s remaining today"
            else             -> "Emergency Break (${m}m ${s}s left today)"
        }
        SystemTrayManager.updateKillSwitchItem(label)
    }

    if (SystemTrayManager.isSupported) {
        SystemTrayManager.install(
            SystemTrayManager.TrayCallbacks(
                onRestore = { windowVisible = true },
                onQuit = {
                    FocusLauncherService.exit()
                    KillSwitchService.deactivate()
                    FocusSessionService.end(completed = false)
                    WeeklyReportService.stopScheduler()
                    TaskAlarmService.stop()
                    RecurringTaskService.stop()
                    BlockScheduleService.stop()
                    DailyAllowanceTracker.stop()
                    AutoBackupService.stop()
                    NuclearMode.disable()
                    ProcessMonitor.dispose()
                    SystemTrayManager.remove()
                    exitApplication()
                },
                onToggleBlocking = {
                    val newState = !ProcessMonitor.alwaysOnEnabled
                    // Disabling enforcement requires the GlobalPin if one is set
                    if (!newState && GlobalPin.isSet()) {
                        SystemTrayManager.showNotification(
                            "PIN Required",
                            "Open FocusFlow to disable enforcement — a PIN is required."
                        )
                        return@TrayCallbacks
                    }
                    ProcessMonitor.alwaysOnEnabled = newState
                    Database.setSetting("always_on_enforcement", newState.toString())
                    val status = if (newState) "ON" else "OFF"
                    SystemTrayManager.showNotification(
                        "FocusFlow Blocking $status",
                        "Always-on enforcement is now $status"
                    )
                },
                onKillSwitch = {
                    val activated = KillSwitchService.toggle()
                    when {
                        !activated -> SystemTrayManager.showNotification(
                            "Emergency Break Exhausted",
                            "You've used your 5-minute daily break budget. Resets at midnight."
                        )
                        KillSwitchService.isActive.value -> {
                            val secs = KillSwitchService.remainingSecondsToday.value
                            val m = secs / 60
                            val s = (secs % 60).toString().padStart(2, '0')
                            SystemTrayManager.showNotification(
                                "Emergency Break — Enforcement Paused",
                                "${m}m ${s}s remaining in your daily budget."
                            )
                        }
                        else -> {
                            val secs = KillSwitchService.remainingSecondsToday.value
                            val m = secs / 60
                            val s = (secs % 60).toString().padStart(2, '0')
                            SystemTrayManager.showNotification(
                                "Enforcement Resumed",
                                "${m}m ${s}s of emergency break budget remaining today."
                            )
                        }
                    }
                }
            )
        )
    }

    val appIcon = painterResource("focusflow_256.png")

    if (windowVisible) {
        Window(
            onCloseRequest = {
                if (isKioskMode) return@Window  // Cannot close window during kiosk mode
                if (SystemTrayManager.isSupported) {
                    windowVisible = false
                    SystemTrayManager.showNotification(
                        "FocusFlow is still running",
                        "Blocking stays active. Right-click the tray icon to quit."
                    )
                } else {
                    FocusLauncherService.exit()
                    KillSwitchService.deactivate()
                    FocusSessionService.end(completed = false)
                    WeeklyReportService.stopScheduler()
                    TaskAlarmService.stop()
                    RecurringTaskService.stop()
                    BlockScheduleService.stop()
                    DailyAllowanceTracker.stop()
                    AutoBackupService.stop()
                    NuclearMode.disable()
                    ProcessMonitor.dispose()
                    SystemTrayManager.remove()
                    exitApplication()
                }
            },
            state       = windowState,
            title       = if (isKioskMode) "FocusFlow — Kiosk Mode" else windowTitle,
            icon        = appIcon,
            alwaysOnTop = isKioskMode
        ) {
            App()
        }
    }
}
