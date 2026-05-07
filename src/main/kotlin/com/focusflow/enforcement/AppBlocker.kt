package com.focusflow.enforcement

import kotlinx.coroutines.*

/**
 * AppBlocker
 *
 * Coordinates showing the block overlay when a blocked app is detected.
 * The actual overlay UI is a Compose composable (BlockOverlay.kt) rendered
 * inside the main window. This singleton fires callbacks that the App.kt
 * composable listens to in order to show/hide the overlay.
 *
 * JVM equivalent of Android's BlockOverlayActivity launched from WindowManager.
 */
object AppBlocker {

    private var overlayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Called by App.kt — invoked with the blocked app name when overlay should appear. */
    var onOverlayShow: ((String) -> Unit)? = null

    /** Called by App.kt — invoked when overlay should disappear. */
    var onOverlayHide: (() -> Unit)? = null

    /**
     * Show the block overlay for [appName].
     * Auto-dismisses after 4 seconds.
     * Safe to call from any thread — dispatches to Main.
     */
    fun showOverlay(appName: String) {
        scope.launch {
            onOverlayShow?.invoke(appName)
            overlayJob?.cancel()
            overlayJob = launch {
                delay(4_000)
                hideOverlay()
            }
        }
    }

    fun hideOverlay() {
        // Always dispatch to Main so Compose state is touched on the correct thread,
        // regardless of what thread the caller is on.
        scope.launch {
            overlayJob?.cancel()
            onOverlayHide?.invoke()
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
