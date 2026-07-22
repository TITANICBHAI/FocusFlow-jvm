package com.focusflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Persistent inline banner showing whether enforcement is currently active.
 *
 * • Green pill  → Always-On Enforcement or a Focus Session is running; rules are live.
 * • Orange card → Neither is active; rules exist but won't block anything right now.
 *                 Tells the user exactly what to do to fix it.
 *
 * Polls [ProcessMonitor] every 2 s so it stays in sync without a StateFlow.
 *
 * @param modifier         Applied to the outermost element.
 * @param showWhenActive   When true (default) shows a compact green status pill even
 *   when enforcement is already on. Pass false to hide entirely when active (useful on
 *   very information-dense screens).
 */
@Composable
fun EnforcementStatusBanner(
    modifier: Modifier = Modifier,
    showWhenActive: Boolean = true,
) {
    var alwaysOn      by remember { mutableStateOf(ProcessMonitor.alwaysOnEnabled) }
    var sessionActive by remember { mutableStateOf(ProcessMonitor.sessionActive) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            alwaysOn      = ProcessMonitor.alwaysOnEnabled
            sessionActive = ProcessMonitor.sessionActive
        }
    }

    val isActive = alwaysOn || sessionActive

    if (!isActive || showWhenActive) {
        AnimatedContent(
            targetState   = isActive,
            modifier      = modifier.fillMaxWidth(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label         = "enforcement-status"
        ) { active ->
            if (active) {
                // ── Active: compact green status pill ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Success.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Success))
                    Text(
                        text = when {
                            alwaysOn && sessionActive -> "Enforcement active · Always-On + Focus Session"
                            alwaysOn                  -> "Enforcement active · Always-On is enabled"
                            else                      -> "Enforcement active · Focus Session is running"
                        },
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = Success,
                        modifier   = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Shield, null, tint = Success, modifier = Modifier.size(14.dp))
                }
            } else {
                // ── Inactive: orange warning card ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Warning.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint     = Warning,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text       = "Enforcement is off — these rules won't block anything right now",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Warning
                        )
                        Text(
                            text = "Enable Always-On Enforcement in Block Defense → System Protection, " +
                                   "or start a Focus Session, to make these rules take effect.",
                            fontSize   = 11.sp,
                            color      = OnSurface2,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
