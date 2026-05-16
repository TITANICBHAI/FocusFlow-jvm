package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focusflow.services.FocusLauncherApp
import com.focusflow.services.FocusLauncherService
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val BG        = Color(0xFF0C0B14)
private val CardBg    = Color(0xFF1A1826)
private val CardHover = Color(0xFF22213A)

@Composable
fun FocusLauncherOverlay() {
    val isActive       by FocusLauncherService.isActive.collectAsState()
    val breakActive    by FocusLauncherService.breakActive.collectAsState()

    if (!isActive || breakActive) return

    val sessionApps   by FocusLauncherService.sessionApps.collectAsState()
    val isHardLocked  by FocusLauncherService.isHardLocked.collectAsState()
    val sessionEndMs  by FocusLauncherService.sessionEndMs.collectAsState()

    var showExitPin   by remember { mutableStateOf(false) }
    var showBreakPin  by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    var clock   by remember { mutableStateOf(LocalTime.now()) }
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            clock   = LocalTime.now()
            elapsed = FocusLauncherService.elapsedSeconds()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(BG)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13121E))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier         = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .background(Purple80.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GridView, null, tint = Purple80, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        "FOCUS LAUNCHER",
                        color      = Purple80,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        letterSpacing = 2.sp
                    )
                    if (isHardLocked) {
                        Spacer(Modifier.width(6.dp))
                        Row(
                            modifier          = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(Error.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = Error, modifier = Modifier.size(11.dp))
                            Text("HARD LOCKED", color = Error, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TimerChip(clock = clock, sessionEndMs = sessionEndMs, elapsed = elapsed)
                }
            }

            // ── App grid ──────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns             = GridCells.Adaptive(minSize = 140.dp),
                modifier            = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sessionApps) { app ->
                    AppTile(app = app)
                }
            }

            // ── Bottom action bar ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13121E))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val canBreak = FocusLauncherService.canTakeBreak()

                // Hard lock toggle
                OutlinedButton(
                    onClick = {
                        if (isHardLocked) showLockConfirm = true
                        else FocusLauncherService.toggleHardLock()
                    },
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(if (isHardLocked) Error else Surface3, if (isHardLocked) Error else Surface3)
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        if (isHardLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        null,
                        tint     = if (isHardLocked) Error else OnSurface2,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isHardLocked) "Unlock Session" else "Lock Session",
                        color  = if (isHardLocked) Error else OnSurface2,
                        fontSize = 13.sp
                    )
                }

                // Take a break
                OutlinedButton(
                    onClick  = { showBreakPin = true },
                    enabled  = canBreak && !isHardLocked,
                    shape    = RoundedCornerShape(10.dp),
                    border   = ButtonDefaults.outlinedButtonBorder
                ) {
                    Icon(Icons.Default.Coffee, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (!canBreak) "Break used today" else "Take a Break  (5 min)",
                        fontSize = 13.sp
                    )
                }

                // Exit
                Button(
                    onClick = { if (isHardLocked) showExitPin = true else showExitPin = true },
                    shape   = RoundedCornerShape(10.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = Surface3)
                ) {
                    Icon(Icons.Default.ExitToApp, null, tint = OnSurface2, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exit", color = OnSurface2, fontSize = 13.sp)
                }
            }
        }
    }

    // ── PIN dialogs ───────────────────────────────────────────────────────────

    if (showExitPin) {
        PinGateDialog(
            title    = "Exit Focus Launcher",
            subtitle = "Enter your PIN to exit kiosk mode.",
            onSuccess = {
                showExitPin = false
                scope.launch(Dispatchers.IO) { FocusLauncherService.exit() }
            },
            onDismiss = { showExitPin = false }
        )
    }

    if (showBreakPin) {
        PinGateDialog(
            title    = "Take a 5-Minute Break",
            subtitle = "Enter your PIN to pause the launcher for 5 minutes.",
            onSuccess = {
                showBreakPin = false
                scope.launch(Dispatchers.IO) { FocusLauncherService.startBreak() }
            },
            onDismiss = { showBreakPin = false }
        )
    }

    if (showLockConfirm) {
        PinGateDialog(
            title    = "Disable Hard Lock",
            subtitle = "Enter your PIN to remove the hard lock.",
            onSuccess = {
                showLockConfirm = false
                FocusLauncherService.toggleHardLock()
            },
            onDismiss = { showLockConfirm = false }
        )
    }
}

// ── Break countdown overlay ────────────────────────────────────────────────────

@Composable
fun FocusLauncherBreakBanner() {
    val breakActive          by FocusLauncherService.breakActive.collectAsState()
    val breakRemainingSeconds by FocusLauncherService.breakRemainingSeconds.collectAsState()

    AnimatedVisibility(
        visible = breakActive,
        enter   = slideInVertically { -it } + fadeIn(),
        exit    = slideOutVertically { -it } + fadeOut()
    ) {
        val mins = breakRemainingSeconds / 60
        val secs = breakRemainingSeconds % 60
        val scope = rememberCoroutineScope()

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Warning.copy(alpha = 0.12f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Warning))
                Text(
                    "Focus Launcher resumes in %02d:%02d".format(mins, secs),
                    color      = Warning,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = { scope.launch(Dispatchers.IO) { FocusLauncherService.endBreak() } },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("End Break Early", color = Warning, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── App tile ───────────────────────────────────────────────────────────────────

@Composable
private fun AppTile(app: FocusLauncherApp) {
    var hovered by remember { mutableStateOf(false) }
    val scope   = rememberCoroutineScope()

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (hovered) CardHover else CardBg)
            .border(1.dp, if (hovered) Purple80.copy(alpha = 0.4f) else Color(0xFF252436), RoundedCornerShape(18.dp))
            .clickable {
                scope.launch(Dispatchers.IO) { launchApp(app) }
            }
            .padding(16.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(Purple80.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Apps, null, tint = Purple80, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            app.displayName,
            color      = OnSurface,
            fontWeight = FontWeight.Medium,
            fontSize   = 13.sp,
            maxLines   = 2,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── Timer chip ────────────────────────────────────────────────────────────────

@Composable
private fun TimerChip(clock: LocalTime, sessionEndMs: Long, elapsed: Long) {
    val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Live clock
        Text(
            clock.format(clockFmt),
            color      = OnSurface,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            letterSpacing = 1.sp
        )

        // Session timer
        if (sessionEndMs > 0L) {
            val remaining = maxOf(0L, (sessionEndMs - System.currentTimeMillis()) / 1000L)
            val rMins     = remaining / 60
            val rSecs     = remaining % 60
            Row(
                modifier          = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (remaining < 300L) Error.copy(alpha = 0.15f) else Surface3)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Timer, null,
                    tint = if (remaining < 300L) Error else OnSurface2,
                    modifier = Modifier.size(13.dp))
                Text(
                    "%02d:%02d".format(rMins, rSecs),
                    color      = if (remaining < 300L) Error else OnSurface2,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            val eMins = elapsed / 60
            val eSecs = elapsed % 60
            Row(
                modifier          = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Surface3)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Timer, null, tint = OnSurface2, modifier = Modifier.size(13.dp))
                Text("%02d:%02d".format(eMins, eSecs),
                    color = OnSurface2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── App launcher helper ────────────────────────────────────────────────────────

private fun launchApp(app: FocusLauncherApp) {
    try {
        val exePath = app.exePath
            ?: com.focusflow.enforcement.InstalledAppsScanner.getExePathFor(app.processName)
        if (exePath != null) {
            ProcessBuilder(exePath).apply {
                inheritIO()
                redirectErrorStream(true)
            }.start()
            return
        }
        // Fallback: ask Windows to find and launch by process name via start command
        ProcessBuilder("cmd", "/c", "start", "", app.processName)
            .redirectErrorStream(true)
            .start()
    } catch (_: Exception) {}
}
