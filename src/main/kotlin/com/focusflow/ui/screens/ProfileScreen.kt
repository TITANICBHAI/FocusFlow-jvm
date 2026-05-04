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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
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
import com.focusflow.data.Database
import com.focusflow.services.BackupService
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.services.WeeklyReportService
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen() {
    val scope = rememberCoroutineScope()

    var userName        by remember { mutableStateOf("") }
    var dailyGoal       by remember { mutableStateOf(120) }
    var saved           by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var exportMsg       by remember { mutableStateOf("") }

    // All-time stats
    var allTimeMins by remember { mutableStateOf(0) }
    var allTimeSess by remember { mutableStateOf(0) }
    var bestStreak  by remember { mutableStateOf(0) }
    var curStreak   by remember { mutableStateOf(0) }
    var totalTasks  by remember { mutableStateOf(0) }
    var last14Days  by remember { mutableStateOf(listOf<Pair<java.time.LocalDate, Boolean>>()) }

    LaunchedEffect(Unit) {
        userName  = withContext(Dispatchers.IO) { Database.getSetting("user_name") ?: "" }
        val goal  = withContext(Dispatchers.IO) { Database.getSetting("daily_focus_goal")?.toIntOrNull() ?: 120 }
        dailyGoal = goal

        allTimeMins = withContext(Dispatchers.IO) { Database.getAllTimeFocusMinutes() }
        allTimeSess = withContext(Dispatchers.IO) { Database.getAllTimeFocusSessions() }
        bestStreak  = withContext(Dispatchers.IO) { Database.getBestStreak() }
        curStreak   = withContext(Dispatchers.IO) { Database.getCurrentStreak() }
        totalTasks  = withContext(Dispatchers.IO) { Database.getTasks().count { t -> t.completed } }

        val today   = java.time.LocalDate.now()
        val start14 = today.minusDays(13)
        val sessions14 = withContext(Dispatchers.IO) {
            Database.getSessionsInDateRange(start14, today)
        }
        val byDate = sessions14.groupBy { it.startTime.toLocalDate() }
        last14Days = (0..13).map { offset ->
            val d = start14.plusDays(offset.toLong())
            val mins = byDate[d]?.sumOf { it.actualMinutes } ?: 0
            d to (mins >= goal)
        }
    }

    val profileScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().background(Surface)
            .verticalScroll(profileScrollState).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Profile & Data", style = MaterialTheme.typography.headlineLarge, color = OnSurface)

        // ── Avatar + name ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Purple80.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (userName.isNotBlank()) userName.first().uppercaseChar().toString() else "?",
                    fontSize = 32.sp, color = Purple80, fontWeight = FontWeight.Bold
                )
            }
            OutlinedTextField(
                value = userName, onValueChange = { userName = it; saved = false },
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
            )
        }

        // ── Daily goal ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Daily Focus Goal", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text("Minimum focus minutes per day to maintain your streak.",
                style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = dailyGoal.toFloat(),
                    onValueChange = { dailyGoal = it.toInt(); saved = false },
                    valueRange = 15f..480f,
                    steps = 30,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Purple80, activeTrackColor = Purple80)
                )
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Purple80.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val h = dailyGoal / 60; val m = dailyGoal % 60
                    Text(if (h > 0) "${h}h ${m}m" else "${m}m", color = Purple80, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30m", 60 to "1h", 90 to "90m", 120 to "2h", 180 to "3h", 240 to "4h").forEach { (g, label) ->
                    FilterChip(selected = dailyGoal == g, onClick = { dailyGoal = g; saved = false }, label = { Text(label) })
                }
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val name = userName.trim()
                    val goal = dailyGoal
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            Database.setSetting("user_name",        name)
                            Database.setSetting("daily_focus_goal", goal.toString())
                        }
                        saved = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Profile")
            }
            if (saved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Saved", color = Success, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── All-time summary ──────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("All-Time Summary", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            val h = allTimeMins / 60; val m = allTimeMins % 60
            ProfileStatRow(Icons.Default.Timer,        "Total focus time", if (h > 0) "${h}h ${m}m" else "${m}m", Purple80)
            ProfileStatRow(Icons.Default.RadioButtonChecked, "Total sessions",  "$allTimeSess sessions",           Purple60)
            ProfileStatRow(Icons.Default.CheckCircle,  "Tasks completed",  "$totalTasks tasks",                   Success)
            ProfileStatRow(Icons.Default.Star,         "Best streak",      "$bestStreak days",                    Warning)
            ProfileStatRow(Icons.AutoMirrored.Filled.TrendingUp, "Current streak", "$curStreak days", if (curStreak > 0) Success else OnSurface2)
        }

        // ── 14-day focus calendar ────────────────────────────────────────────
        if (last14Days.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("14-Day Focus Calendar", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Success.copy(alpha = 0.85f)))
                        Text("Goal met", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Error.copy(alpha = 0.3f)))
                        Text("Missed", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    last14Days.forEach { (date, met) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.height(28.dp).fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            met  -> Success.copy(alpha = 0.85f)
                                            date.isAfter(java.time.LocalDate.now()) -> Surface3
                                            else -> Error.copy(alpha = 0.28f)
                                        }
                                    )
                            )
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface2,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Weekly report ─────────────────────────────────────────────────────
        val report = WeeklyReportService.latestReport
        if (report != null) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last Weekly Report", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    if (WeeklyReportService.hasNewReport) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(Purple80.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("NEW", style = MaterialTheme.typography.bodySmall, color = Purple80, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(report.weekLabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                ProfileStatRow(Icons.Default.Timer,        "Focus time",       report.hoursFormatted,              Purple80)
                ProfileStatRow(Icons.Default.RadioButtonChecked, "Sessions",   "${report.sessionsCompleted}",       Purple60)
                ProfileStatRow(Icons.Default.CheckCircle,  "Tasks done",       "${report.tasksCompleted}",          Success)
                ProfileStatRow(Icons.Default.Block,        "Blocked attempts", "${report.blockedAttempts}",         Error.copy(alpha = 0.8f))
                ProfileStatRow(Icons.Default.Star,         "Streak at end",    "${report.currentStreakDays} days",  Warning)
                TextButton(onClick = { WeeklyReportService.dismissNewReportBadge() }) {
                    Text("Dismiss", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Today's app usage (daily allowances) ─────────────────────────────
        val usageSummary = DailyAllowanceTracker.getUsageSummary()
        if (usageSummary.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Today's App Usage", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                Text("Measured since the app was last started. Resets at midnight.",
                    style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                usageSummary.forEach { (allowance, usedMins) ->
                    val pct       = (usedMins.toFloat() / allowance.allowanceMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val isBlocked = allowance.processName.lowercase() in DailyAllowanceTracker.blockedProcesses
                    val barColor: Color = when {
                        isBlocked   -> Error.copy(alpha = 0.8f)
                        pct > 0.75f -> Warning
                        else        -> Purple80
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(allowance.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isBlocked) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                        .background(Error.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)) {
                                        Text("BLOCKED", style = MaterialTheme.typography.bodySmall,
                                            color = Error, fontSize = 9.sp)
                                    }
                                }
                                val remaining = DailyAllowanceTracker.getRemainingMinutes(allowance)
                                Text("${usedMins}m used · ${remaining}m left",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Surface3)) {
                            Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp)).background(barColor))
                        }
                    }
                }
            }
        }

        // ── Export ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export Data", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text("Export your sessions or tasks as CSV files for external analysis.",
                style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { BackupService.exportToCsv() }
                        exportMsg = if (path != null) "Sessions saved to $path" else "Export cancelled"
                    }
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Export Sessions")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { BackupService.exportTasksToCsv() }
                        exportMsg = if (path != null) "Tasks saved to $path" else "Export cancelled"
                    }
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Export Tasks")
                }
            }
            if (exportMsg.isNotBlank()) {
                Text(exportMsg, style = MaterialTheme.typography.bodySmall, color = Success)
            }
        }

        // ── Danger zone ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Error.copy(alpha = 0.06f)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Danger Zone", style = MaterialTheme.typography.headlineSmall, color = Error)
            Text("Permanently delete all stored data. This cannot be undone.",
                style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("Clear All Data")
            }
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(profileScrollState)
    )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = Surface2,
            title            = { Text("Clear All Data?", color = Error) },
            text             = {
                Text("This will permanently delete all sessions, tasks, notes, and settings. This cannot be undone.",
                    color = OnSurface2)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                Database.clearAllSessions()
                                Database.clearAllTasks()
                                Database.clearNotes()
                                Database.clearTemptationLog()
                            }
                            exportMsg = "All data cleared."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = OnSurface2) }
            }
        )
    }
}

@Composable
private fun ProfileStatRow(icon: ImageVector, label: String, value: String, color: Color = OnSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}
