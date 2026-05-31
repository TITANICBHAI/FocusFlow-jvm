package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusflow.ui.theme.*

private const val URL_APPGALLERY  = "https://appgallery.huawei.com/app/C117761461"
private const val URL_GITHUB_APK  = "https://github.com/TITANICBHAI/FocusFlow/releases"
private const val URL_SAI_FDROID  = "https://f-droid.org/packages/com.aefyr.sai.fdroid/"
private const val URL_SAI_GITHUB  = "https://github.com/Aefyr/SAI/releases"

private enum class PromoStep { MAIN, SIDELOAD_GUIDE }

@Composable
fun AndroidPromoDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(PromoStep.MAIN) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape    = RoundedCornerShape(24.dp),
            color    = Surface,
            modifier = Modifier.width(480.dp)
        ) {
            AnimatedContent(
                targetState  = step,
                transitionSpec = {
                    if (targetState == PromoStep.SIDELOAD_GUIDE)
                        fadeIn() + slideInHorizontally { it / 6 } togetherWith
                        fadeOut() + slideOutHorizontally { -it / 6 }
                    else
                        fadeIn() + slideInHorizontally { -it / 6 } togetherWith
                        fadeOut() + slideOutHorizontally { it / 6 }
                }
            ) { current ->
                when (current) {
                    PromoStep.MAIN          -> MainPromoPage(
                        onDismiss      = onDismiss,
                        onAppGallery   = { openUrl(URL_APPGALLERY); onDismiss() },
                        onGithubApk    = { step = PromoStep.SIDELOAD_GUIDE }
                    )
                    PromoStep.SIDELOAD_GUIDE -> SideloadGuidePage(
                        onBack    = { step = PromoStep.MAIN },
                        onConfirm = { openUrl(URL_GITHUB_APK); onDismiss() },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPromoPage(
    onDismiss:    () -> Unit,
    onAppGallery: () -> Unit,
    onGithubApk:  () -> Unit
) {
    Column(
        modifier                = Modifier.padding(32.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(14.dp)
    ) {
        PromoIcon(Icons.Default.PhoneAndroid)

        Text(
            "Take FocusFlow Everywhere",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = OnSurface,
            textAlign  = TextAlign.Center
        )

        Text(
            "You're crushing it on PC — now block distractions on your phone too. Free, forever.",
            style      = MaterialTheme.typography.bodyMedium,
            color      = OnSurface2,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(2.dp))

        Button(
            onClick  = onAppGallery,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Purple80)
        ) {
            Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get it on AppGallery", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick  = onGithubApk,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface2)
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Download APK from GitHub", fontSize = 13.sp)
        }

        TextButton(onClick = onDismiss) {
            Text("Maybe later", color = OnSurface2.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SideloadGuidePage(
    onBack:    () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier                = Modifier.padding(32.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint     = OnSurface2,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        }

        PromoIcon(Icons.Default.InstallMobile)

        Text(
            "How to Install the APK",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = OnSurface,
            textAlign  = TextAlign.Center
        )

        Text(
            "FocusFlow isn't on the Play Store, so you'll need an APK installer. It only takes a minute.",
            style      = MaterialTheme.typography.bodyMedium,
            color      = OnSurface2,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(2.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepRow(
                number = "1",
                text   = "Install SAI (Split APKs Installer) — free on F-Droid or GitHub. Any standard APK installer also works."
            )
            StepRow(
                number = "2",
                text   = "Tap \"OK, Download APK\" below — it opens our GitHub Releases page."
            )
            StepRow(
                number = "3",
                text   = "Download the latest .apk file, then open it with SAI or your APK installer and tap Install."
            )
            StepRow(
                number = "4",
                text   = "When prompted, allow \"Install from unknown sources\" for your installer app — this is normal for sideloading."
            )
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick  = { openUrl(URL_SAI_FDROID) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface2),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text("Get SAI (F-Droid)", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick  = { openUrl(URL_SAI_GITHUB) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface2),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text("SAI on GitHub", fontSize = 12.sp)
            }
        }

        Button(
            onClick  = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Purple80)
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("OK, Download APK", fontWeight = FontWeight.SemiBold)
        }

        TextButton(onClick = onDismiss) {
            Text("Cancel", color = OnSurface2.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PromoIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Purple80.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Purple80, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier         = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Purple80.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style      = MaterialTheme.typography.labelSmall,
                color      = Purple80,
                fontWeight = FontWeight.Bold,
                fontSize   = 11.sp
            )
        }
        Text(
            text,
            style      = MaterialTheme.typography.bodySmall,
            color      = OnSurface2,
            lineHeight = 17.sp,
            modifier   = Modifier.weight(1f)
        )
    }
}

@Composable
fun ReviewPromptDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape    = RoundedCornerShape(24.dp),
            color    = Surface,
            modifier = Modifier.width(400.dp)
        ) {
            Column(
                modifier            = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PromoIcon(Icons.Default.Star)

                Text(
                    "Enjoying FocusFlow?",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = OnSurface,
                    textAlign  = TextAlign.Center
                )

                Text(
                    "A quick review on the Microsoft Store helps others discover FocusFlow. It only takes a moment!",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = OnSurface2,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick  = { openUrl("https://apps.microsoft.com/detail/9NJN9FPRQ7T1"); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rate on Microsoft Store", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick  = { openUrl("https://github.com/TITANICBHAI/FocusFlow-jvm"); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface2)
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Star on GitHub", fontSize = 13.sp)
                }

                TextButton(onClick = onDismiss) {
                    Text("No thanks", color = OnSurface2.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
        }
    }
}

fun openUrl(url: String) {
    try {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
            desktop.browse(java.net.URI(url))
        }
    } catch (_: Throwable) {}
}
