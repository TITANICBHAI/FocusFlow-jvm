package com.focusflow.enforcement

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import kotlinx.coroutines.*
import java.awt.Toolkit

/**
 * AppBlocker
 *
 * Shows an always-on-top block overlay window when a blocked app is detected.
 * The overlay covers the full screen with a dark background and motivational message.
 *
 * JVM equivalent of Android's BlockOverlayActivity launched from WindowManager.
 */
object AppBlocker {

    private var overlayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onOverlayShow: ((String) -> Unit)? = null
    var onOverlayHide: (() -> Unit)? = null

    fun showOverlay(appName: String) {
        onOverlayShow?.invoke(appName)

        // Auto-dismiss after 4 seconds
        overlayJob?.cancel()
        overlayJob = scope.launch {
            delay(4000)
            hideOverlay()
        }
    }

    fun hideOverlay() {
        overlayJob?.cancel()
        onOverlayHide?.invoke()
    }
}
