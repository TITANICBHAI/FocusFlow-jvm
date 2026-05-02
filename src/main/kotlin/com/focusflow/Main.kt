package com.focusflow

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.focusflow.data.Database
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.services.*

fun main() = application {
    // Global crash handler — log uncaught exceptions instead of silently dying
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        val logFile = java.io.File(System.getProperty("user.home") + "/.focusflow/crash.log")
        logFile.parentFile.mkdirs()
        logFile.appendText("[${java.time.LocalDateTime.now()}] CRASH on thread ${t.name}:\n${e.stackTraceToString()}\n\n")
        System.err.println("[FocusFlow] Uncaught exception on ${t.name}: ${e.message}")
    }

    Database.init()

    ProcessMonitor.alwaysOnEnabled   = Database.getSetting("always_on_enforcement") == "true"
    SoundAversion.isEnabled          = Database.getSetting("sound_aversion") != "false"
    FocusSessionService.pomodoroMode = Database.getSetting("pomodoro_mode") == "true"

    ProcessMonitor.start()

    BreakEnforcer.loadSettings()
    NuclearMode.loadFromDb()
    TaskAlarmService.start()

    // Recurring tasks — auto-generate daily/weekday/weekly copies each morning
    RecurringTaskService.start()

    // Block schedules — recurring time-window enforcement
    BlockScheduleService.start()

    // Standalone block — restore a block that survived a restart
    StandaloneBlockService.loadFromDb()

    // Daily allowances — per-app usage caps that reset at midnight
    DailyAllowanceTracker.start()

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

    if (SystemTrayManager.isSupported) {
        SystemTrayManager.install(
            SystemTrayManager.TrayCallbacks(
                onRestore = { windowVisible = true },
                onQuit = {
                    FocusSessionService.end(completed = false)
                    WeeklyReportService.stopScheduler()
                    TaskAlarmService.stop()
                    RecurringTaskService.stop()
                    BlockScheduleService.stop()
                    DailyAllowanceTracker.stop()
                    NuclearMode.disable()
                    ProcessMonitor.dispose()
                    SystemTrayManager.remove()
                    exitApplication()
                },
                onToggleBlocking = {
                    ProcessMonitor.alwaysOnEnabled = !ProcessMonitor.alwaysOnEnabled
                    Database.setSetting(
                        "always_on_enforcement",
                        ProcessMonitor.alwaysOnEnabled.toString()
                    )
                    val status = if (ProcessMonitor.alwaysOnEnabled) "ON" else "OFF"
                    SystemTrayManager.showNotification(
                        "FocusFlow Blocking $status",
                        "Always-on enforcement is now $status"
                    )
                }
            )
        )
    }

    if (windowVisible) {
        Window(
            onCloseRequest = {
                if (SystemTrayManager.isSupported) {
                    windowVisible = false
                    SystemTrayManager.showNotification(
                        "FocusFlow is still running",
                        "Blocking stays active. Right-click the tray icon to quit."
                    )
                } else {
                    FocusSessionService.end(completed = false)
                    WeeklyReportService.stopScheduler()
                    TaskAlarmService.stop()
                    RecurringTaskService.stop()
                    BlockScheduleService.stop()
                    DailyAllowanceTracker.stop()
                    NuclearMode.disable()
                    ProcessMonitor.dispose()
                    SystemTrayManager.remove()
                    exitApplication()
                }
            },
            state       = windowState,
            title       = "FocusFlow",
            alwaysOnTop = false
        ) {
            App()
        }
    }
}
