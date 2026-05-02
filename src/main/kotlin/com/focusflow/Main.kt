package com.focusflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.focusflow.data.Database
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.WindowsStartupManager
import com.focusflow.services.FocusSessionService
import com.focusflow.services.SoundAversion
import com.focusflow.services.WeeklyReportService

fun main() = application {
    // 1. Database — must be first
    Database.init()

    // 2. Restore persisted settings
    ProcessMonitor.alwaysOnEnabled = Database.getSetting("always_on_enforcement") == "true"
    SoundAversion.isEnabled        = Database.getSetting("sound_aversion") != "false"

    // 3. Enforcement (starts hook + polling loop)
    ProcessMonitor.start()

    // 4. Weekly report scheduler (checks once on startup, then every 24h)
    WeeklyReportService.startScheduler()

    val windowState = rememberWindowState(
        width     = 1100.dp,
        height    = 720.dp,
        placement = WindowPlacement.Floating
    )

    Window(
        onCloseRequest = {
            FocusSessionService.end(completed = false)
            WeeklyReportService.stopScheduler()
            ProcessMonitor.dispose()
            exitApplication()
        },
        state  = windowState,
        title  = "FocusFlow",
        alwaysOnTop = false
    ) {
        App()
    }
}
