package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusflow.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UPI_ID = "himanshu00@upi"

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var upiCopied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape    = RoundedCornerShape(24.dp),
            color    = Surface,
            modifier = Modifier.width(440.dp)
        ) {
            Column(
                modifier            = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE91E63).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint     = Color(0xFFE91E63),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    "Support FocusFlow",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = OnSurface,
                    textAlign  = TextAlign.Center
                )

                Text(
                    "FocusFlow is free and always will be. If it's helped you focus, a small donation keeps development going.",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = OnSurface2,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // ── India / UPI section ──────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇮🇳", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "India — UPI",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // UPI ID chip — tap to copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (upiCopied) Color(0xFFE91E63).copy(alpha = 0.10f)
                                else Surface3.copy(alpha = 0.6f)
                            )
                            .border(
                                width = if (upiCopied) 1.5.dp else 0.dp,
                                color = if (upiCopied) Color(0xFFE91E63) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                copyToClipboard(UPI_ID)
                                upiCopied = true
                                scope.launch {
                                    delay(2000)
                                    upiCopied = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            if (upiCopied) Icons.Default.Check else Icons.Default.QrCode,
                            contentDescription = null,
                            tint     = if (upiCopied) Color(0xFFE91E63) else OnSurface2,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (upiCopied) "Copied!" else UPI_ID,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color      = if (upiCopied) Color(0xFFE91E63) else OnSurface,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            tint     = OnSurface2.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        "Open any UPI app (GPay, PhonePe, Paytm…), tap Pay, and paste the ID above.",
                        style      = MaterialTheme.typography.bodySmall,
                        color      = OnSurface2,
                        lineHeight = 16.sp
                    )
                }

                // ── International section ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌍", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Outside India",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IntlStep(
                        emoji = "⭐",
                        text  = "Star the repo on GitHub — it's free and helps a lot."
                    )
                    IntlStep(
                        emoji = "📢",
                        text  = "Share FocusFlow with someone who needs it."
                    )
                    IntlStep(
                        emoji = "💬",
                        text  = "Leave a review on the Microsoft Store."
                    )

                    // GitHub link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface3.copy(alpha = 0.6f))
                            .clickable { openUrl("https://github.com/TITANICBHAI/FocusFlow-jvm") }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint     = OnSurface2,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Open GitHub →",
                            style      = MaterialTheme.typography.bodySmall,
                            color      = Purple80,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        "Maybe later",
                        color    = OnSurface2.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun IntlStep(emoji: String, text: String) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(emoji, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
        Text(
            text,
            style      = MaterialTheme.typography.bodySmall,
            color      = OnSurface2,
            lineHeight = 16.sp,
            modifier   = Modifier.weight(1f)
        )
    }
}
