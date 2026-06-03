package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.i18n.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.focusflow.enforcement.AppBlocker
import com.focusflow.ui.theme.*

@Composable
fun BlockOverlay(
    visible: Boolean,
    appName: String,
    onDismiss: () -> Unit
) {
    val s = LocalizationManager.strings
    var overlayMessage by remember { mutableStateOf("Stay focused. You've got this.") }
    LaunchedEffect(Unit) {
        val msg = withContext(Dispatchers.IO) { Database.getSetting("overlay_message") }
        if (msg != null) overlayMessage = msg
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 8 },
        exit  = fadeOut(tween(220)) + slideOutVertically(tween(250)) { it / 8 }
    ) {
        val iconPulse = rememberInfiniteTransition(label = "blockIconPulse")
        val iconScale by iconPulse.animateFloat(
            initialValue  = 1.00f,
            targetValue   = 1.10f,
            animationSpec = infiniteRepeatable(
                animation  = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blockIconScale"
        )
        val msgAlpha by iconPulse.animateFloat(
            initialValue  = 0.70f,
            targetValue   = 1.00f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blockMsgAlpha"
        )

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
                    tint     = Error,
                    modifier = Modifier.size(72.dp).scale(iconScale)
                )

                Text(
                    "$appName ${s.overlayIsBlocked}",
                    style     = MaterialTheme.typography.headlineMedium,
                    color     = OnSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    overlayMessage,
                    style     = MaterialTheme.typography.bodyLarge,
                    color     = OnSurface2.copy(alpha = msgAlpha),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    s.overlayAutoClose,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2.copy(alpha = 0.6f)
                )

                TextButton(onClick = onDismiss) {
                    Text(s.overlayDismiss, color = Purple60)
                }
            }
        }
    }
}
