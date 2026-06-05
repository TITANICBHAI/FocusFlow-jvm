package com.focusflow.ui.screens

import com.focusflow.ui.components.FfVerticalScrollbar
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
        version    = "1.0.9",
        date       = "June 2026",
        badge      = "LATEST",
        badgeColor = Success,
        changes    = listOf(
            "NEW" to "Resource Monitor Service — anonymous JVM / OS health telemetry sent to a private developer Discord channel on an hourly heartbeat",
            "NEW" to "Heap threshold alerts — instant Discord notification when heap exceeds 75 % (warning) or 90 % (critical) of max, with 30-minute cooldown to prevent flooding",
            "NEW" to "Thread spike alert — fires when live thread count surpasses 150, helping catch runaway executor leaks early",
            "NEW" to "Resource metrics collected: heap used / total / max MB, non-heap, physical RAM, CPU cores, live / daemon / peak thread counts, GC collections and pause time",
            "IMP" to "Privacy section in Settings updated — crash reports and resource telemetry share a single 'Send anonymous diagnostics' toggle with a clear description of what is and is not collected",
            "IMP" to "Zero PII guarantee — no usernames, file paths, IP addresses or app content are ever included in any telemetry payload",
            "FIX" to "Missing @Volatile on background Job references in 8 services (DailyAllowanceTracker, AutoBackupService, RecurringTaskService, TaskAlarmService, WeeklyReportService, BlockScheduleService, HostsBlocker, FocusSessionService) — shutdown thread could read a stale null and silently skip cancel(), leaving loops running after teardown",
            "FIX" to "StandaloneBlockService build error — missing import kotlinx.coroutines.flow.update caused compilation failure"
        )
    ),
    ChangelogEntry(
        version    = "1.0.8",
        date       = "June 2026",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "FIX"  to "OutOfMemoryError on startup — tray icon load now catches Throwable (not just Exception), falling back to the programmatic icon gracefully on low-memory machines",
            "FIX"  to "Onboarding crash on rapid Back clicks — 300 ms debounce prevents 'mutex not locked' error caused by Compose gesture cleanup racing AnimatedContent exit animations",
            "IMP"  to "Scrollbars always visible at rest (subtle purple track) across all 17 scrollable screens — hover near the right edge to light it up, no more invisible guessing",
            "IMP"  to "Scrollbar thumb widened to 8 dp and native Compose Desktop hover colour now works correctly (alpha modifier that was overriding it removed)",
            "NEW"  to "Android app sidebar button now opens a popup with App Gallery and GitHub APK options — GitHub path includes a SAI sideload guide explaining restricted-settings lift",
            "NEW"  to "Keyboard Shortcuts page added to onboarding flow (page 5, after Permissions) — users see all shortcuts before finishing setup",
            "NEW"  to "Keyboard Shortcuts section added to How to Use screen — Ctrl+1–5, Ctrl+,, Ctrl+N, Ctrl+F, Ctrl+P, Ctrl+Enter all documented with context notes",
            "IMP"  to "MSIX store logos now scaled from the real focusflow_256.png source — preserves rounded-corner artwork instead of flat GDI+ redraw",
            "IMP"  to "MSIX StoreLogo.png (50×50) added — was missing, causing Store listing to show a generic placeholder icon",
            "IMP"  to "Build workflow reads app version dynamically from build.gradle.kts — bumping version in one place updates manifest, artifact filename and all CI outputs automatically"
        )
    ),
    ChangelogEntry(
        version    = "1.0.7",
        date       = "May 2026",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "FIX"  to "Daily allowances — usage tracking and quota enforcement now work correctly",
            "NEW"  to "ShortcutTooltip + AppLocals components — reusable tooltip and app-wide DI primitives",
            "NEW"  to "Global Ctrl+1–5, Ctrl+, keyboard shortcuts for instant screen navigation",
            "NEW"  to "Screen-specific shortcuts: Dashboard, Tasks and Focus shortcuts documented in tooltips",
            "NEW"  to "Sound volume slider in Settings — live-adjust aversion-tone volume (0–100%)",
            "NEW"  to "Block Overlay Duration slider in Settings — set dismiss time from 2 s to 15 s",
            "NEW"  to "Task deletion with Undo snackbar — accidental deletes are fully recoverable",
            "NEW"  to "Keyword Blocker 'Clear All' now shows a confirmation dialog before wiping the list",
            "IMP"  to "Block Defense screen — 'Edit in App Blocker →' button opens the blocker directly",
            "IMP"  to "App Blocker manual-entry errors are now field-specific (duplicate / empty / invalid)",
            "IMP"  to "Focus screen shows helper hint when Focus Mode is disabled",
            "IMP"  to "Stats (Yesterday / Week) and Reports (Sessions) tabs show loading spinners",
            "IMP"  to "Empty-state cards in App Blocker (Always-Block tab), Stats and Reports screens"
        )
    ),
    ChangelogEntry(
        version    = "1.0.5",
        date       = "May 2026",
        badge      = "",
        badgeColor = Color.Transparent,
        changes    = listOf(
            "NEW"  to "Keyword Blocker — block browser tabs matching custom words or presets",
            "NEW"  to "Block Defense screen — all enforcement layers in one place",
            "NEW"  to "Active/Live status screen — real-time view of every running block",
            "NEW"  to "How to Use guide — accordion help sections for every feature",
            "NEW"  to "Changelog screen (you're reading it!)",
            "NEW"  to "Emergency Break — 5-minute daily kill switch in the tray menu; pauses all enforcement layers instantly",
            "NEW"  to "Watchdog auto-restart — FocusFlow relaunches itself every 2 minutes via Task Scheduler if it ever gets killed or crashes",
            "NEW"  to "Standalone blocker: Date Range scheduling — pick exact start and end date/time for a block",
            "IMP"  to "SideNav now shows Android-style grouped sections",
            "IMP"  to "FocusScreen Enforcement panel expanded with sub-rows",
            "IMP"  to "Dashboard header shows X/Y tasks done subtitle",
            "IMP"  to "Tray menu shows live countdown of remaining break budget (resets at midnight)",
            "IMP"  to "Kill switch respects Nuclear Mode — even escape-process blocking is paused during the grace period",
            "IMP"  to "Scheduled blocks show a live countdown until the block begins, then switch to remaining time",
            "FIX"  to "CMD, PowerShell and other shells now killed during any enforcement — Nuclear Mode no longer required",
            "FIX"  to "App crash on process kill permission denied on non-Windows OS"
        )
    ),
    ChangelogEntry(
        version    = "1.0.4",
        date       = "April 2026",
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
        version    = "1.0.3",
        date       = "March 2026",
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
        version    = "1.0.2",
        date       = "February 2026",
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
        version    = "1.0.1",
        date       = "January 2026",
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
        date       = "December 2025",
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

                    HorizontalDivider(color = Surface3, thickness = 1.dp)

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

        FfVerticalScrollbar(
            scrollState = scrollState,
            modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }
}
