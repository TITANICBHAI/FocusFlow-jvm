package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.enforcement.AppBlocker
import com.focusflow.ui.theme.*

/**
 * BlockOverlay
 *
 * Full-screen overlay shown when a blocked app is detected and killed.
 * Displayed as an always-on-top composable layer over the main window.
 *
 * JVM equivalent of Android's BlockOverlayActivity launched over the blocked app.
 * Limitation: on Android the overlay appears OVER the blocked app's window.
 * On desktop we can only overlay our own window (bring it to front + maximize).
 */
@Composable
fun BlockOverlay(
    visible: Boolean,
    appName: String,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE0D0C18)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(48.dp)
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.size(72.dp)
                )

                Text(
                    "$appName is blocked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    Database.getSetting("overlay_message")
                        ?: "Stay focused. You've got this.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface2,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "This window will close automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2.copy(alpha = 0.6f)
                )

                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = Purple60)
                }
            }
        }
    }
}
