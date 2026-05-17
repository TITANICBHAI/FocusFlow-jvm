package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focusflow.enforcement.AppIconExtractor
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.services.FocusLauncherApp
import com.focusflow.services.FocusLauncherService
import com.focusflow.services.GlobalPin
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val canBreak      by FocusLauncherService.canTakeBreak.collectAsState()

    var showExitPin     by remember { mutableStateOf(false) }
    var showBreakPin    by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Pulsing alpha for the HARD LOCKED badge — draws attention without being distracting
    val infiniteTransition = rememberInfiniteTransition(label = "hardlock")
    val hardLockPulse by infiniteTransition.animateFloat(
        initialValue  = 0.12f,
        targetValue   = 0.32f,
        animationSpec = infiniteRepeatable(
            animation  = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hardlock_alpha"
    )

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
                                .background(Error.copy(alpha = hardLockPulse))
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
            val gridState = rememberLazyGridState()
            Box(modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp)) {
                LazyVerticalGrid(
                    columns               = GridCells.Adaptive(minSize = 140.dp),
                    state                 = gridState,
                    modifier              = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(sessionApps) { app ->
                        AppTile(app = app)
                    }
                }
                VerticalScrollbar(
                    adapter  = rememberScrollbarAdapter(gridState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style    = ScrollbarStyle(
                        minimalHeight       = 48.dp,
                        thickness           = 6.dp,
                        shape               = RoundedCornerShape(3.dp),
                        hoverDurationMillis = 200,
                        unhoverColor        = Color(0xFF3A384F),
                        hoverColor          = Purple80.copy(alpha = 0.7f)
                    )
                )
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
                // Hard lock toggle
                OutlinedButton(
                    onClick = {
                        if (isHardLocked) showLockConfirm = true
                        else scope.launch(Dispatchers.IO) { FocusLauncherService.toggleHardLock() }
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
                    Icon(Icons.Default.Pause, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (!canBreak) "Break used today" else "Take a Break  (5 min)",
                        fontSize = 13.sp
                    )
                }

                // Exit — PIN required only when hard-locked; direct exit otherwise
                Button(
                    onClick = {
                        if (isHardLocked) {
                            showExitPin = true
                        } else {
                            scope.launch(Dispatchers.IO) { FocusLauncherService.exit() }
                        }
                    },
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
                scope.launch(Dispatchers.IO) { FocusLauncherService.toggleHardLock() }
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered           by interactionSource.collectIsHoveredAsState()
    val scope             = rememberCoroutineScope()

    var icon        by remember(app.exePath) { mutableStateOf<ImageBitmap?>(null) }
    var launching   by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf(false) }

    LaunchedEffect(app.exePath) {
        if (app.exePath != null) {
            icon = withContext(Dispatchers.IO) {
                AppIconExtractor.extractIcon(app.exePath)
            }
        }
    }

    // Auto-clear error state after 3 seconds so the tile recovers gracefully
    LaunchedEffect(launchError) {
        if (launchError) {
            kotlinx.coroutines.delay(3_000)
            launchError = false
        }
    }

    val borderColor = when {
        launchError -> Error.copy(alpha = 0.7f)
        hovered     -> Purple80.copy(alpha = 0.4f)
        else        -> Color(0xFF252436)
    }
    val bgColor = when {
        launchError -> Error.copy(alpha = 0.08f)
        hovered     -> CardHover
        else        -> CardBg
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                enabled           = !launching
            ) {
                launching = true
                launchError = false
                scope.launch(Dispatchers.IO) {
                    val ok = launchApp(app)
                    withContext(Dispatchers.Main) {
                        launching   = false
                        launchError = !ok
                    }
                }
            }
            .padding(16.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(
                    if (launchError) Error.copy(alpha = 0.15f) else Purple80.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                launching -> CircularProgressIndicator(
                    color    = Purple80,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                launchError -> Icon(
                    Icons.Default.Warning, null,
                    tint     = Error,
                    modifier = Modifier.size(26.dp)
                )
                icon != null -> Image(
                    bitmap             = icon!!,
                    contentDescription = app.displayName,
                    modifier           = Modifier.size(36.dp)
                )
                else -> Icon(Icons.Default.Apps, null, tint = Purple80, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (launchError) "Failed to launch" else app.displayName,
            color      = if (launchError) Error else OnSurface,
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

/**
 * Returns true when the process was spawned successfully, false on any failure.
 * Callers must handle the false case — in kiosk mode the user has no other way
 * to diagnose a failed launch.
 */
private fun launchApp(app: FocusLauncherApp): Boolean {
    return try {
        val exePath = app.exePath
            ?: com.focusflow.enforcement.InstalledAppsScanner.getExePathFor(app.processName)
        if (exePath != null) {
            // Set working directory to the exe's own folder — many apps fail without it
            val workDir = java.io.File(exePath).parentFile
            ProcessBuilder(exePath).apply {
                inheritIO()
                redirectErrorStream(true)
                if (workDir?.exists() == true) directory(workDir)
            }.start()
            return true
        }
        // Fallback: try ShellExecute semantics via explorer — works for Store apps and
        // protocol URIs as well as .exe names that aren't in PATH.
        ProcessBuilder("cmd", "/c", "start", "", "/b", app.processName)
            .redirectErrorStream(true)
            .start()
        true
    } catch (_: Exception) {
        false
    }
}

