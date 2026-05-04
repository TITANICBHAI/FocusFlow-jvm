package com.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.enforcement.isWindows
import com.focusflow.ui.theme.*

@Composable
fun OsBanner() {
    if (isWindows) return

    var visible by remember { mutableStateOf(true) }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Warning.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enforcement inactive on this platform",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Warning
                )
                Text(
                    "FocusFlow's app blocking, network rules and process monitoring only work on Windows. The UI and data features are fully functional.",
                    fontSize = 12.sp,
                    color = OnSurface2
                )
            }
            IconButton(onClick = { visible = false }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = OnSurface2, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Returns true when the app is running without administrator privileges on Windows. */
fun isRunningAsAdmin(): Boolean {
    if (!isWindows) return true
    return try {
        val pb = ProcessBuilder("net", "session")
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val code = proc.waitFor()
        code == 0
    } catch (_: Exception) {
        false
    }
}

@Composable
fun AdminBanner(showWhen: Boolean) {
    if (!showWhen || !isWindows) return

    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val isAdmin = remember { isRunningAsAdmin() }
    if (isAdmin) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Error.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = Error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Administrator privileges required",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Error
            )
            Text(
                "Network blocking and Nuclear Mode require admin rights. Right-click FocusFlow.exe → Run as administrator.",
                fontSize = 12.sp,
                color = OnSurface2
            )
        }
        IconButton(onClick = { dismissed = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Dismiss", tint = OnSurface2, modifier = Modifier.size(16.dp))
        }
    }
}
