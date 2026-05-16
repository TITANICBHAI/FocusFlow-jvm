package com.focusflow.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colour palette (matches FocusFlow dark theme) ─────────────────────────────

private val BgDeep      = Color(0xFF0C0B14)
private val BgSurface   = Color(0xFF16141F)
private val BgCard      = Color(0xFF1E1B2E)
private val AccentPurple = Color(0xFF7C5CBF)
private val AccentGreen  = Color(0xFF4CAF82)
private val AccentRed    = Color(0xFFE05252)
private val AccentAmber  = Color(0xFFD4A017)
private val TextPrimary   = Color(0xFFE8E4F0)
private val TextSecondary = Color(0xFF9B96B0)
private val DividerColor  = Color(0xFF2A2640)

fun main() = application {
    val windowState = rememberWindowState(
        width  = 560.dp,
        height = 700.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title          = "FocusFlow Emergency Recovery",
        state          = windowState,
        resizable      = false
    ) {
        RecoveryApp()
    }
}

@Composable
fun RecoveryApp() {
    val scope         = rememberCoroutineScope()
    val stepStatuses  = remember { mutableStateListOf<StepResult?>().apply { repeat(RecoveryEngine.steps.size) { add(null) } } }
    var isRunning     by remember { mutableStateOf(false) }
    var isComplete    by remember { mutableStateOf(false) }
    var anyFailed     by remember { mutableStateOf(false) }

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
                // ── Header ──────────────────────────────────────────────────
                HeaderSection()

                Spacer(Modifier.height(28.dp))

                // ── Warning banner ──────────────────────────────────────────
                if (!isRunning && !isComplete) {
                    WarningBanner()
                    Spacer(Modifier.height(24.dp))
                }

                // ── Step list ───────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgCard),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    RecoveryEngine.steps.forEachIndexed { index, step ->
                        StepRow(
                            step   = step,
                            result = stepStatuses[index],
                            isLast = index == RecoveryEngine.steps.lastIndex
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Completion message ──────────────────────────────────────
                if (isComplete) {
                    CompletionBanner(anyFailed = anyFailed)
                    Spacer(Modifier.height(20.dp))
                }

                // ── Action button ───────────────────────────────────────────
                Button(
                    onClick = {
                        if (!isRunning && !isComplete) {
                            isRunning = true
                            scope.launch {
                                for (i in RecoveryEngine.steps.indices) {
                                    RecoveryEngine.runStep(i) { result ->
                                        stepStatuses[i] = result
                                    }
                                    // Small visual pause between steps
                                    delay(120)
                                }
                                anyFailed = stepStatuses.any { it?.status == StepStatus.FAILED }
                                isRunning = false
                                isComplete = true
                            }
                        }
                    },
                    enabled  = !isRunning && !isComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = AccentPurple,
                        disabledContainerColor = BgCard
                    )
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(20.dp),
                            color     = TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Running recovery…", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    } else if (isComplete) {
                        Text("Recovery complete", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Run Recovery Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (isComplete) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = ::exitApplication) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.18f)),
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
            text     = "FocusFlow • Last-resort rescue tool",
            fontSize = 13.sp,
            color    = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "Restores your taskbar and clears all enforcement locks\nso your PC returns to normal operation.",
            fontSize = 13.sp,
            color    = TextSecondary,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun WarningBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentAmber.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚠️", fontSize = 16.sp, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Run as Administrator for full recovery",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = AccentAmber
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Removing firewall rules and editing the hosts file requires admin rights. " +
                "Right-click the EXE → \"Run as administrator\" before clicking the button.",
                fontSize = 12.sp,
                color    = AccentAmber.copy(alpha = 0.8f),
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun StepRow(step: RecoveryStep, result: StepResult?, isLast: Boolean) {
    val status = result?.status ?: StepStatus.PENDING

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(status)
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
                val detailText = when {
                    status == StepStatus.RUNNING  -> "Working…"
                    result?.detail?.isNotBlank() == true -> result.detail
                    else -> step.description
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = detailText,
                    fontSize = 12.sp,
                    color    = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp),
                color     = DividerColor,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun StatusIcon(status: StepStatus) {
    val (icon, tint): Pair<ImageVector, Color> = when (status) {
        StepStatus.PENDING -> Icons.Filled.RadioButtonUnchecked to TextSecondary.copy(alpha = 0.4f)
        StepStatus.RUNNING -> Icons.Filled.HourglassEmpty       to AccentPurple
        StepStatus.SUCCESS -> Icons.Filled.CheckCircle          to AccentGreen
        StepStatus.FAILED  -> Icons.Filled.Error                to AccentRed
        StepStatus.SKIPPED -> Icons.Filled.RemoveCircleOutline  to TextSecondary.copy(alpha = 0.5f)
    }
    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun CompletionBanner(anyFailed: Boolean) {
    val bg      : Color
    val textCol : Color
    val emoji   : String
    val heading : String
    val body    : String

    if (anyFailed) {
        bg      = AccentRed.copy(alpha = 0.10f)
        textCol = AccentRed
        emoji   = "⚠️"
        heading = "Partial recovery"
        body    = "Some steps failed. Re-run as Administrator, or manually perform the failed steps."
    } else {
        bg      = AccentGreen.copy(alpha = 0.10f)
        textCol = AccentGreen
        emoji   = "✅"
        heading = "Recovery complete"
        body    = "All enforcement has been cleared. You can safely close this tool and restart your PC."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(heading, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textCol)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 12.sp, color = textCol.copy(alpha = 0.8f), lineHeight = 17.sp)
        }
    }
}
