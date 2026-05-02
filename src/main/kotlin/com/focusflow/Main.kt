package com.focusflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.focusflow.data.Database
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.services.FocusSessionService

fun main() = application {
    // Initialise database
    Database.init()

    // Start enforcement monitor (only activates during sessions or always-on mode)
    ProcessMonitor.start()

    val windowState = rememberWindowState(
        width = 1100.dp,
        height = 720.dp,
        placement = WindowPlacement.Floating
    )

    Window(
        onCloseRequest = {
            FocusSessionService.end(completed = false)
            ProcessMonitor.dispose()
            exitApplication()
        },
        state = windowState,
        title = "FocusFlow",
        alwaysOnTop = false
    ) {
        App()
    }
}
