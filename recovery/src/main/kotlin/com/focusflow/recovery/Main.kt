package com.focusflow.recovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colour palette (matches FocusFlow dark theme) ─────────────────────────────

private val BgDeep       = Color(0xFF0C0B14)
private val BgSurface    = Color(0xFF16141F)
private val BgCard       = Color(0xFF1E1B2E)
private val BgCardAlt    = Color(0xFF211E31)
private val AccentPurple = Color(0xFF7C5CBF)
private val AccentGreen  = Color(0xFF4CAF82)
private val AccentRed    = Color(0xFFE05252)
private val AccentAmber  = Color(0xFFD4A017)
private val AccentCyan   = Color(0xFF4FC3F7)
private val TextPrimary   = Color(0xFFE8E4F0)
private val TextSecondary = Color(0xFF9B96B0)
private val DividerColor  = Color(0xFF2A2640)

// ── Pre-flight checks (run at startup, Windows-only) ─────────────────────────

private fun detectIsAdmin(): Boolean = try {
    val script = "[Security.Principal.WindowsPrincipal]" +
        "[Security.Principal.WindowsIdentity]::GetCurrent()" +
        ".IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)"
    val proc = ProcessBuilder(
        "powershell", "-NonInteractive", "-NoProfile",
        "-ExecutionPolicy", "Bypass", "-Command", script
    ).redirectErrorStream(true).start()
    val out = proc.inputStream.bufferedReader().readText().trim()
    proc.waitFor()
    out.equals("True", ignoreCase = true)
} catch (_: Exception) { false }

private fun detectFocusFlowRunning(): Boolean = try {
    ProcessHandle.allProcesses().anyMatch { ph ->
        ph.info().command().orElse("").lowercase().contains("focusflow")
    }
} catch (_: Exception) { false }

private fun restartWindows() {
    try { Runtime.getRuntime().exec(arrayOf("shutdown", "/r", "/t", "30")) }
    catch (_: Exception) { }
}

private fun cancelRestart() {
    try { Runtime.getRuntime().exec(arrayOf("shutdown", "/a")) }
    catch (_: Exception) { }
}

/**
 * Re-launches this EXE with elevated (Administrator) privileges using
 * PowerShell Start-Process -Verb RunAs, then exits the current instance.
 * No-ops when the current process path cannot be determined (e.g. IDE run).
 */
private fun relaunchAsAdmin(onExit: () -> Unit) {
    val exe = ProcessHandle.current().info().command().orElse(null) ?: return
    // Only attempt elevation when running as a packaged EXE, not from Gradle/IDE
    if (!exe.lowercase().endsWith(".exe")) return
    try {
        ProcessBuilder(
            "powershell", "-NonInteractive", "-NoProfile",
            "-ExecutionPolicy", "Bypass", "-Command",
            "Start-Process -FilePath '${exe.replace("'", "''")}' -Verb RunAs"
        ).start()
        onExit()
    } catch (_: Exception) { }
}

// ── Entry point ───────────────────────────────────────────────────────────────

fun main() = application {
    val windowState = rememberWindowState(
        width    = 560.dp,
        height   = 760.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title          = "FocusFlow Emergency Recovery",
        state          = windowState,
        resizable      = false
    ) {
        RecoveryApp(onExit = ::exitApplication)
    }
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun RecoveryApp(onExit: () -> Unit = {}) {
    val scope = rememberCoroutineScope()

    val stepStatuses = remember {
        mutableStateListOf<StepResult?>().apply {
            repeat(RecoveryEngine.steps.size) { add(null) }
        }
    }
    var isRunning  by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }
    var anyFailed  by remember { mutableStateOf(false) }

    // Pre-flight check state: null = still loading, true/false = result
    var isAdmin           by remember { mutableStateOf<Boolean?>(null) }
    var focusFlowRunning  by remember { mutableStateOf<Boolean?>(null) }

    // Restart state
    var restartPending    by remember { mutableStateOf(false) }
    var restartCountdown  by remember { mutableStateOf(30) }

    // Log file path shown after completion
    var logPath by remember { mutableStateOf("") }

    // Current enforcement state — null = still scanning
    var enforcementState by remember { mutableStateOf<EnforcementState?>(null) }

    // Run pre-flight checks and enforcement scan in background on startup
    LaunchedEffect(Unit) {
        isAdmin          = detectIsAdmin()
        focusFlowRunning = detectFocusFlowRunning()
        enforcementState = RecoveryEngine.detectCurrentState()
    }

    // Countdown ticker after restart is triggered
    LaunchedEffect(restartPending) {
        if (restartPending) {
            restartCountdown = 30
            while (restartCountdown > 0) {
                delay(1000)
                restartCountdown--
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background   = BgDeep,
            surface      = BgSurface,
            primary      = AccentPurple,
            onBackground = TextPrimary,
            onSurface    = TextPrimary
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header ────────────────────────────────────────────────────
                HeaderSection()

                Spacer(Modifier.height(20.dp))

                // ── Pre-flight checks ─────────────────────────────────────────
                PreflightSection(
                    isAdmin           = isAdmin,
                    focusFlowRunning  = focusFlowRunning,
                    isRunning         = isRunning,
                    isComplete        = isComplete,
                    onRelaunchAsAdmin = { relaunchAsAdmin(onExit) }
                )

                Spacer(Modifier.height(20.dp))

                // ── Current enforcement state ─────────────────────────────────
                // Only shown before recovery runs; stale after completion
                if (!isComplete) {
                    CurrentStatePanel(state = enforcementState)
                    Spacer(Modifier.height(20.dp))
                }

                // ── Step list ─────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgCard)
                ) {
                    RecoveryEngine.steps.forEachIndexed { index, step ->
                        StepRow(
                            number = index + 1,
                            step   = step,
                            result = stepStatuses[index],
                            isLast = index == RecoveryEngine.steps.lastIndex
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Completion result ─────────────────────────────────────────
                if (isComplete) {
                    CompletionBanner(anyFailed = anyFailed)
                    Spacer(Modifier.height(16.dp))
                }

                // ── Restart section (success only) ────────────────────────────
                if (isComplete && !anyFailed) {
                    RestartSection(
                        restartPending   = restartPending,
                        restartCountdown = restartCountdown,
                        onRestartClick   = {
                            restartPending = true
                            restartWindows()
                        },
                        onCancelClick    = {
                            restartPending = false
                            cancelRestart()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Log path note ─────────────────────────────────────────────
                if (isComplete && logPath.isNotBlank()) {
                    LogPathNote(logPath)
                    Spacer(Modifier.height(12.dp))
                }

                // ── Main action button ────────────────────────────────────────
                Button(
                    onClick = {
                        if (!isRunning && !isComplete) {
                            isRunning = true
                            scope.launch {
                                RecoveryLogger.start(
                                    isAdmin            = isAdmin ?: false,
                                    focusFlowWasRunning = focusFlowRunning ?: false
                                )
                                for (i in RecoveryEngine.steps.indices) {
                                    RecoveryEngine.runStep(i) { result ->
                                        stepStatuses[i] = result
                                        RecoveryLogger.logStep(i + 1, RecoveryEngine.steps[i], result)
                                    }
                                    delay(130)
                                }
                                anyFailed = stepStatuses.any { it?.status == StepStatus.FAILED }
                                logPath   = RecoveryLogger.finish(anyFailed)
                                isRunning  = false
                                isComplete = true
                            }
                        }
                    },
                    enabled  = !isRunning && !isComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = AccentPurple,
                        disabledContainerColor = BgCard
                    )
                ) {
                    when {
                        isRunning  -> {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                color       = TextPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Running recovery…",
                                color      = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        isComplete -> Text(
                            "Recovery complete",
                            color      = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        else       -> Text(
                            "Run Recovery Now",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }

                if (isComplete && (anyFailed || restartPending)) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onExit) {
                        Text("Close without restarting", color = TextSecondary, fontSize = 13.sp)
                    }
                } else if (isComplete) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onExit) {
                        Text("Close", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.15f))
                .border(1.dp, AccentPurple.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🛟", fontSize = 28.sp)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text       = "Emergency Recovery",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "FocusFlow • Standalone rescue tool",
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text       = "Use this if FocusFlow left your PC in a locked state. It restores\nyour taskbar, clears enforcement flags, removes firewall rules,\nand cleans the hosts file — all in one click.",
            fontSize   = 13.sp,
            color      = TextSecondary,
            lineHeight = 20.sp,
            textAlign  = TextAlign.Center
        )
    }
}

// ── Pre-flight checks ─────────────────────────────────────────────────────────

@Composable
private fun PreflightSection(
    isAdmin: Boolean?,
    focusFlowRunning: Boolean?,
    isRunning: Boolean,
    isComplete: Boolean,
    onRelaunchAsAdmin: () -> Unit
) {
    val adminLoaded = isAdmin != null
    val adminOk     = isAdmin == true
    val ffLoaded    = focusFlowRunning != null
    val ffClosed    = focusFlowRunning == false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Before you start",
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = TextSecondary
        )

        // Admin check
        PreflightRow(
            loaded       = adminLoaded,
            ok           = adminOk,
            label        = if (adminOk) "Running as Administrator" else "Not running as Administrator",
            sublabel     = if (!adminLoaded) "Checking…"
                           else if (adminOk) "Firewall rules and hosts file edits will work"
                           else              "Firewall and hosts file steps will fail — re-launch with admin rights for full recovery",
            warnIfFailed = true
        )

        // One-click elevation button — shown only when not admin and not yet running
        if (adminLoaded && !adminOk && !isRunning && !isComplete) {
            Button(
                onClick  = onRelaunchAsAdmin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape  = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentAmber.copy(alpha = 0.18f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    "🔑  Relaunch as Administrator",
                    color      = AccentAmber,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // FocusFlow running check
        PreflightRow(
            loaded       = ffLoaded,
            ok           = ffClosed,
            label        = if (ffClosed) "FocusFlow is not running" else "FocusFlow appears to be running",
            sublabel     = if (!ffLoaded) "Checking…"
                           else if (ffClosed) "Safe to proceed"
                           else               "Close FocusFlow first — otherwise it may re-apply locks immediately after recovery",
            warnIfFailed = true
        )
    }
}

@Composable
private fun PreflightRow(
    loaded: Boolean,
    ok: Boolean,
    label: String,
    sublabel: String,
    warnIfFailed: Boolean
) {
    val dotColor = when {
        !loaded       -> TextSecondary.copy(alpha = 0.35f)
        ok            -> AccentGreen
        warnIfFailed  -> AccentAmber
        else          -> AccentRed
    }
    val labelColor   = if (!loaded || ok) TextPrimary else AccentAmber
    val sublabelColor = if (!loaded || ok) TextSecondary else AccentAmber.copy(alpha = 0.75f)

    Row(verticalAlignment = Alignment.Top) {
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = labelColor)
            Text(sublabel, fontSize = 12.sp, color = sublabelColor, lineHeight = 17.sp)
        }
    }
}

// ── Step list ─────────────────────────────────────────────────────────────────

@Composable
private fun StepRow(number: Int, step: RecoveryStep, result: StepResult?, isLast: Boolean) {
    val status = result?.status ?: StepStatus.PENDING

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            StepStatus.SUCCESS -> AccentGreen.copy(alpha = 0.15f)
                            StepStatus.FAILED  -> AccentRed.copy(alpha = 0.12f)
                            StepStatus.RUNNING -> AccentPurple.copy(alpha = 0.20f)
                            else               -> BgCardAlt
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (status == StepStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        color       = AccentPurple,
                        strokeWidth = 2.dp
                    )
                } else {
                    StatusIcon(status = status, stepNumber = number)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = step.name,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = when (status) {
                        StepStatus.SUCCESS -> TextPrimary
                        StepStatus.FAILED  -> AccentRed
                        StepStatus.RUNNING -> AccentPurple
                        else               -> TextSecondary
                    }
                )
                val detail = when {
                    status == StepStatus.RUNNING            -> "Working…"
                    result?.detail?.isNotBlank() == true    -> result.detail
                    else                                    -> step.description
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = detail,
                    fontSize   = 12.sp,
                    color      = if (status == StepStatus.FAILED) AccentRed.copy(alpha = 0.8f) else TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        if (!isLast) {
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 18.dp),
                color     = DividerColor,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun StatusIcon(status: StepStatus, stepNumber: Int) {
    when (status) {
        StepStatus.PENDING -> Text(
            text  = stepNumber.toString(),
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = TextSecondary.copy(alpha = 0.5f)
        )
        StepStatus.SUCCESS -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Done",
            tint     = AccentGreen,
            modifier = Modifier.size(16.dp)
        )
        StepStatus.FAILED  -> Icon(
            Icons.Filled.Error,
            contentDescription = "Failed",
            tint     = AccentRed,
            modifier = Modifier.size(16.dp)
        )
        StepStatus.SKIPPED -> Icon(
            Icons.Filled.RemoveCircleOutline,
            contentDescription = "Skipped",
            tint     = TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        StepStatus.RUNNING -> Unit // handled by caller (spinner)
    }
}

// ── Completion banner ─────────────────────────────────────────────────────────

@Composable
private fun CompletionBanner(anyFailed: Boolean) {
    val bg      = if (anyFailed) AccentRed.copy(alpha = 0.10f)   else AccentGreen.copy(alpha = 0.10f)
    val textCol = if (anyFailed) AccentRed                        else AccentGreen
    val emoji   = if (anyFailed) "⚠️" else "✅"
    val heading = if (anyFailed) "Partial recovery" else "Recovery complete"
    val body    = if (anyFailed)
        "One or more steps failed. If firewall or hosts steps failed, close this tool and re-run it as Administrator (right-click the EXE). The database flags were likely cleared successfully."
    else
        "All enforcement has been cleared. A Windows restart is recommended to ensure firewall rule changes take full effect."

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(heading, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textCol)
            Spacer(Modifier.height(3.dp))
            Text(body, fontSize = 12.sp, color = textCol.copy(alpha = 0.82f), lineHeight = 17.sp)
        }
    }
}

// ── Current enforcement state panel ───────────────────────────────────────────

@Composable
private fun CurrentStatePanel(state: EnforcementState?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Current enforcement state",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextSecondary
            )
            if (state != null && state.confirmedIssueCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentRed.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${state.confirmedIssueCount} issue${if (state.confirmedIssueCount == 1) "" else "s"} found",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = AccentRed
                    )
                }
            }
        }

        when {
            state == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(12.dp),
                        color       = TextSecondary,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning…", fontSize = 12.sp, color = TextSecondary)
                }
            }
            !state.dbFound -> {
                StateRow(ok = true,  label = "No FocusFlow database found",
                    sub = "FocusFlow may not have been installed yet")
            }
            !state.hasAnyIssue -> {
                StateRow(ok = true, label = "Nothing locked — PC appears clean",
                    sub = "No active enforcement flags, firewall rules, or hosts entries found")
            }
            else -> {
                if (state.crashGuard)
                    StateRow(ok = false, label = "Crash guard is active",
                        sub  = "launcher_crash_guard = true")
                if (state.hardLocked)
                    StateRow(ok = false, label = "Hard lock is active",
                        sub  = "launcher_hard_locked = true — all exit paths blocked")
                if (state.nuclearMode)
                    StateRow(ok = false, label = "Nuclear Mode is active",
                        sub  = "nuclear_mode = true — website and app blocking fully engaged")
                if (state.killSwitchCapped)
                    StateRow(ok = false, label = "Kill Switch budget has been used today",
                        sub  = "killswitch_remaining_today < 300")
                if (state.firewallRuleCount > 0)
                    StateRow(ok = false, label = "${state.firewallRuleCount} firewall rule(s) are blocking apps",
                        sub  = "FocusFlow_Block_* rules present in Windows Firewall")
                if (state.firewallRuleCount == -1)
                    StateRow(ok = null, label = "Firewall rules — could not check",
                        sub  = "Run as Administrator to enable firewall inspection")
                if (state.hostsEntryCount > 0)
                    StateRow(ok = false, label = "${state.hostsEntryCount} website(s) blocked in hosts file",
                        sub  = "# FocusFlow entries present in System32\\drivers\\etc\\hosts")
                if (state.hostsEntryCount == -1)
                    StateRow(ok = null, label = "Hosts file — could not check",
                        sub  = "Run as Administrator to enable hosts file inspection")
            }
        }
    }
}

/**
 * A single row inside [CurrentStatePanel].
 * @param ok  true = green (clean), false = red (issue), null = amber (unknown)
 */
@Composable
private fun StateRow(ok: Boolean?, label: String, sub: String) {
    val dotColor = when (ok) {
        true  -> AccentGreen
        false -> AccentRed
        null  -> AccentAmber
    }
    val labelColor = when (ok) {
        true  -> TextPrimary
        false -> AccentRed
        null  -> AccentAmber
    }
    val subColor = when (ok) {
        true  -> TextSecondary
        false -> AccentRed.copy(alpha = 0.75f)
        null  -> AccentAmber.copy(alpha = 0.75f)
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = labelColor)
            Text(sub,   fontSize = 11.sp, color = subColor, lineHeight = 15.sp)
        }
    }
}

// ── Log path note ─────────────────────────────────────────────────────────────

@Composable
private fun LogPathNote(path: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📄", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Recovery log saved",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary
            )
            Text(
                path,
                fontSize   = 11.sp,
                color      = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

// ── Restart section ───────────────────────────────────────────────────────────

@Composable
private fun RestartSection(
    restartPending: Boolean,
    restartCountdown: Int,
    onRestartClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!restartPending) {
            Text(
                "Restart recommended",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "A restart ensures firewall rule removals take full effect and\nWindows can rebuild any cached network state.",
                fontSize  = 12.sp,
                color     = TextSecondary,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick  = onRestartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.85f))
            ) {
                Text(
                    "Restart Windows Now",
                    color      = Color(0xFF0C0B14),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }
        } else {
            Text(
                "Restarting in $restartCountdown seconds…",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = AccentCyan
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Save any open work before the countdown reaches zero.",
                fontSize  = 12.sp,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick  = onCancelClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
            ) {
                Text("Cancel Restart", fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
