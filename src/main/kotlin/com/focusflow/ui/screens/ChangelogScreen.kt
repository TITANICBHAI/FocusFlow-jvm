package com.focusflow.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.*

private data class ChangelogEntry(
    val version: String,
    val date: String,
    val badge: String,
    val badgeColor: Color,
    val changes: List<Pair<String, String>>
)

private val CHANGELOG = listOf(
    ChangelogEntry(
        version    = "1.5.0",
        date       = "May 2025",
        badge      = "LATEST",
        badgeColor = Success,
        changes    = listOf(
            "NEW"  to "Keyword Blocker — block browser tabs matching custom words or presets",
            "NEW"  to "Block Defense screen — all enforcement layers in one place",
            "NEW"  to "Active/Live status screen — real-time view of every running block",
            "NEW"  to "How to Use guide — accordion help sections for every feature",
            "NEW"  to "Changelog screen (you're reading it!)",
            "IMP"  to "SideNav now shows Android-style grouped sections",
            "IMP"  to "FocusScreen Enforcement panel expanded with sub-rows",
            "IMP"  to "Dashboard header shows X/Y tasks done subtitle",
            "FIX"  to "App crash on process kill permission denied on non-Windows OS"
        )
    ),
    ChangelogEntry(
        version    = "1.4.0",
        date       = "April 2025",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "NEW"  to "Habits tracker with emoji support and calendar heatmap",
            "NEW"  to "Reports screen — weekly and all-time productivity summaries",
            "NEW"  to "Daily Notes with mood selector",
            "NEW"  to "Always-On enforcement toggle in Settings",
            "NEW"  to "Onboarding dialog on first launch",
            "IMP"  to "StatsScreen redesigned with Yesterday / Today / Week / All-Time tabs",
            "IMP"  to "Streak banner and bitter truth card in Stats",
            "FIX"  to "Block schedules not reloading after changes"
        )
    ),
    ChangelogEntry(
        version    = "1.3.0",
        date       = "March 2025",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "NEW"  to "Block Schedules — define recurring time windows for app blocking",
            "NEW"  to "Daily Allowances — allow apps for a limited daily usage time",
            "NEW"  to "Session PIN — require a PIN to end a focus session early",
            "NEW"  to "Sound Aversion toggle in Settings",
            "IMP"  to "AppBlockerScreen rewritten with pick-from-running-apps support",
            "IMP"  to "Standalone block panel added to Focus tab",
            "FIX"  to "Network block (Windows Firewall rule) not applied consistently"
        )
    ),
    ChangelogEntry(
        version    = "1.2.0",
        date       = "February 2025",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "NEW"  to "Pomodoro mode with configurable work / break / long-break intervals",
            "NEW"  to "Session notes field during focus session",
            "NEW"  to "Distraction counter during session",
            "NEW"  to "TasksScreen with priority, tags, recurring task support",
            "IMP"  to "DashboardScreen shows live session countdown",
            "FIX"  to "Recurring tasks not generating correctly on weekly cadence"
        )
    ),
    ChangelogEntry(
        version    = "1.1.0",
        date       = "January 2025",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "NEW"  to "App Blocker with process-kill enforcement",
            "NEW"  to "Focus session timer with pause / resume",
            "NEW"  to "Block overlay shown when blocked app is detected",
            "NEW"  to "Temptation log — records every blocked-app attempt",
            "IMP"  to "SQLite database for persistent storage",
            "FIX"  to "Window not restoring from system tray on some machines"
        )
    ),
    ChangelogEntry(
        version    = "1.0.0",
        date       = "December 2024",
        badge      = "INITIAL",
        badgeColor = OnSurface2,
        changes    = listOf(
            "NEW"  to "Initial release of FocusFlow JVM desktop app",
            "NEW"  to "Dashboard, Tasks, Focus, Stats screens",
            "NEW"  to "Profile & Settings screens",
            "NEW"  to "Privacy & Permissions screen",
            "NEW"  to "TBTechs branding and FocusFlow logo"
        )
    )
)

private val BADGE_COLOR = mapOf(
    "NEW" to Purple80,
    "IMP" to Warning,
    "FIX" to Error
)

@Composable
fun ChangelogScreen() {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = Purple80, modifier = Modifier.size(28.dp))
                Text("Changelog", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            }
            Text("What's new in FocusFlow JVM by TBTechs", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

            CHANGELOG.forEach { entry ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "v${entry.version}",
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (entry.badge.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(entry.badgeColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    entry.badge,
                                    color = entry.badgeColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(entry.date, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    }

                    Divider(color = Surface3, thickness = 1.dp)

                    entry.changes.forEach { (tag, text) ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background((BADGE_COLOR[tag] ?: OnSurface2).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tag,
                                    color = BADGE_COLOR[tag] ?: OnSurface2,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                text,
                                color = OnSurface2,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }
}
