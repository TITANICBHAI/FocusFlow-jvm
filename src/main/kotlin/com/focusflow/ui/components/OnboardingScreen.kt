package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val body: String
)

private val PAGES = listOf(
    OnboardingPage(
        icon = Icons.Default.Bolt,
        title = "Welcome to FocusFlow",
        subtitle = "Your real-enforcement focus companion",
        body = "FocusFlow helps you build laser-sharp focus habits by actually blocking distracting apps while you work — not just tracking them."
    ),
    OnboardingPage(
        icon = Icons.Default.Block,
        title = "Real App Blocking",
        subtitle = "Not just a reminder — enforcement",
        body = "Add apps like Chrome, Discord or Steam to your block list. During a focus session, FocusFlow will instantly kill those processes and show a block overlay so you stay on track."
    ),
    OnboardingPage(
        icon = Icons.Default.Timer,
        title = "Focus Sessions & Pomodoro",
        subtitle = "Work in structured sprints",
        body = "Start a timed focus session for any task. Enable Pomodoro mode for automatic 25/5-minute cycles with long breaks every 4 sessions. Your session history is saved to SQLite locally."
    ),
    OnboardingPage(
        icon = Icons.Default.Shield,
        title = "Schedules & Allowances",
        subtitle = "Block on a timetable, cap daily usage",
        body = "Set recurring block schedules (e.g. block social media 9am–5pm on weekdays). Set daily allowances to cap how many minutes per day an app can run before being blocked."
    ),
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Nuclear Mode & Session PIN",
        subtitle = "Maximum enforcement when you need it",
        body = "Nuclear Mode blocks escape routes like Task Manager and PowerShell. Set a SHA-256 session PIN so you can't end a focus session without the code — real commitment."
    ),
    OnboardingPage(
        icon = Icons.Default.BarChart,
        title = "Stats, Habits & Reports",
        subtitle = "Track your progress over time",
        body = "View daily focus minutes, streaks, temptation logs and weekly reports. Build habit chains with the habit tracker. All data is stored locally — no accounts, no cloud, no tracking."
    )
)

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val isLast = page == PAGES.size - 1

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Surface,
            modifier = Modifier.width(520.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState)
                            fadeIn() + slideInHorizontally { it / 4 } togetherWith fadeOut() + slideOutHorizontally { -it / 4 }
                        else
                            fadeIn() + slideInHorizontally { -it / 4 } togetherWith fadeOut() + slideOutHorizontally { it / 4 }
                    }
                ) { p ->
                    val pg = PAGES[p]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Purple80.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(pg.icon, null, tint = Purple80, modifier = Modifier.size(40.dp))
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            pg.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            pg.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Purple80,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            pg.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface2,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(PAGES.size) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == page) 20.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == page) Purple80 else Purple80.copy(alpha = 0.25f))
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page > 0) {
                        TextButton(onClick = { page-- }) {
                            Text("Back", color = OnSurface2)
                        }
                    } else {
                        Spacer(Modifier.width(80.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Skip", color = OnSurface2.copy(alpha = 0.6f), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (isLast) onDismiss() else page++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Text(if (isLast) "Get Started" else "Next")
                        if (!isLast) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
