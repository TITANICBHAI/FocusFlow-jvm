package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.*

/**
 * A small ℹ icon button that opens a plain-language explanation of how the
 * FocusFlow PIN system works. Drop it anywhere near a PIN-protected feature.
 */
@Composable
fun PinInfoButton(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }

    IconButton(onClick = { show = true }, modifier = modifier.size(32.dp)) {
        Icon(
            Icons.Default.Info,
            contentDescription = "PIN info",
            tint     = OnSurface2.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp)
        )
    }

    if (show) {
        PinInfoDialog(onDismiss = { show = false })
    }
}

@Composable
private fun PinInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Purple80.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint     = Purple80,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text("PIN System", color = OnSurface, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                InfoSection(
                    heading = "What is the PIN for?",
                    body    = "One shared PIN protects your enforcement settings so you can't accidentally (or impulsively) undo them mid-session."
                )

                InfoSection(
                    heading = "What it protects",
                    body    = "• Disabling Always-On Enforcement\n" +
                              "• Adding or deleting Daily Allowance rules\n" +
                              "• Ending a Focus Session early"
                )

                InfoSection(
                    heading = "Setting your PIN",
                    body    = "Global PIN (protects block rules & enforcement): set it in Settings → Security. Minimum 8 characters.\n\nSession PIN (required to end a session early): set it in Settings → Session PIN. Also minimum 8 characters."
                )

                InfoSection(
                    heading = "No PIN set?",
                    body    = "All protected actions pass through freely. The gates are there but unlocked — set a PIN to actually enforce them."
                )

                InfoSection(
                    heading = "Forgot your Global PIN?",
                    body    = "Enter the wrong PIN twice, then tap \"Forgot PIN?\". You'll need to type RESET to confirm — this clears the PIN entirely so you can set a new one."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Got it") }
        }
    )
}

@Composable
private fun InfoSection(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            heading,
            style      = MaterialTheme.typography.labelMedium,
            color      = Purple80,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp
        )
        Text(
            body,
            style     = MaterialTheme.typography.bodySmall,
            color     = OnSurface2,
            lineHeight = 17.sp
        )
    }
}
