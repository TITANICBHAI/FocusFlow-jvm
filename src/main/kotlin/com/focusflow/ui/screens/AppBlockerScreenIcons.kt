package com.focusflow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.enforcement.AppIconExtractor
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.ui.theme.Purple80
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Brand colors for known apps ────────────────────────────────────────────────

private val appBrandColors = mapOf(
    "chrome.exe"            to Color(0xFF4285F4),
    "firefox.exe"           to Color(0xFFFF6611),
    "msedge.exe"            to Color(0xFF0078D7),
    "opera.exe"             to Color(0xFFCC1A22),
    "brave.exe"             to Color(0xFFFF3800),
    "discord.exe"           to Color(0xFF5865F2),
    "slack.exe"             to Color(0xFF4A154B),
    "teams.exe"             to Color(0xFF6264A7),
    "zoom.exe"              to Color(0xFF2196F3),
    "telegram.exe"          to Color(0xFF2AABEE),
    "whatsapp.exe"          to Color(0xFF25D366),
    "signal.exe"            to Color(0xFF3A76F0),
    "spotify.exe"           to Color(0xFF1DB954),
    "steam.exe"             to Color(0xFF1B2838),
    "epicgameslauncher.exe" to Color(0xFF2C2C2C),
    "origin.exe"            to Color(0xFFF56C2D),
    "battle.net.exe"        to Color(0xFF148EFF),
    "leagueclient.exe"      to Color(0xFFC89B3C),
    "twitch.exe"            to Color(0xFF9147FF),
    "obs64.exe"             to Color(0xFF302E31),
    "tiktok.exe"            to Color(0xFF010101),
    "netflix.exe"           to Color(0xFFE50914),
    "vlc.exe"               to Color(0xFFFF8800),
    "wmplayer.exe"          to Color(0xFF005A9E),
    "outlook.exe"           to Color(0xFF0078D4),
    "winword.exe"           to Color(0xFF2B579A),
    "excel.exe"             to Color(0xFF217346),
    "powerpnt.exe"          to Color(0xFFB7472A),
    "notepad++.exe"         to Color(0xFF81BF43),
    "code.exe"              to Color(0xFF007ACC),
    "devenv.exe"            to Color(0xFF68217A),
    "idea64.exe"            to Color(0xFFFF318C),
    "pycharm64.exe"         to Color(0xFF21D789),
    "webstorm64.exe"        to Color(0xFF00CDD7),
    "studio64.exe"          to Color(0xFF3DDC84)
)

@Composable
fun AppIcon(
    processName: String,
    displayName: String,
    size: Int = 38,
    exePath: String? = null
) {
    val key    = processName.lowercase()
    val brand  = appBrandColors[key]
    val color  = brand ?: Purple80.copy(alpha = 0.7f)
    val letter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    // Resolve exe path: prefer explicit arg, then scanner cache
    val resolvedPath = remember(processName, exePath) {
        exePath ?: InstalledAppsScanner.getExePathFor(processName)
    }

    // Async icon loading — re-runs whenever the resolved path changes
    var iconBitmap by remember(resolvedPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(resolvedPath) {
        if (resolvedPath != null) {
            iconBitmap = withContext(Dispatchers.IO) {
                AppIconExtractor.extractIcon(resolvedPath)
            }
        }
    }

    val shape = RoundedCornerShape((size * 0.28f).dp)

    // Capture to a local val so the null-check and the use refer to the exact
    // same object — avoids a potential NPE if Compose recomposition races with
    // the LaunchedEffect that writes iconBitmap.
    val bitmap = iconBitmap

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(if (bitmap != null) Color.Transparent else color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap,
                contentDescription = displayName,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(size.dp).clip(shape)
            )
        } else {
            Text(
                text       = letter,
                color      = color,
                fontSize   = (size * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        }
    }
}
