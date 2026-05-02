package com.focusflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.DailyAllowance
import com.focusflow.data.models.Task
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.services.FocusSessionService
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun DashboardScreen(onStartFocus: (Task) -> Unit, onNavigateTasks: () -> Unit) {
    val today   = LocalDate.now()
    val session by FocusSessionService.state.collectAsState()

    var tasks            by remember { mutableStateOf(listOf<Task>()) }
    var streak           by remember { mutableStateOf(0) }
    var focusToday       by remember { mutableStateOf(0) }
    var completedToday   by remember { mutableStateOf(0) }
    var dailyGoal        by remember { mutableStateOf(120) }
    var showQuickAdd     by remember { mutableStateOf(false) }
    var userName         by remember { mutableStateOf("") }
    var allowances       by remember { mutableStateOf(listOf<DailyAllowance>()) }
    var blockedAttempts  by remember { mutableStateOf(0) }

    fun reload() {
        tasks           = Database.getTasksForDate(today)
        streak          = Database.getCurrentStreak()
        focusToday      = Database.getTotalFocusMinutesToday()
        completedToday  = tasks.count { it.completed }
        dailyGoal       = Database.getSetting("daily_focus_goal")?.toIntOrNull() ?: 120
        userName        = Database.getSetting("user_name") ?: ""
        allowances      = Database.getDailyAllowances()
        blockedAttempts = Database.getTemptationsInRange(today.toString(), today.toString())
    }

    LaunchedEffect(Unit) { reload() }

    // Derive a simple focus score (0–100)
    val goalPct    = (focusToday.toFloat() / dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val taskPct    = if (tasks.isNotEmpty()) completedToday.toFloat() / tasks.size else 0f
    val focusScore = ((goalPct * 60f) + (taskPct * 30f) + (if (streak > 0) 10f else 0f)).toInt().coerceIn(0, 100)
    val scoreColor = when {
        focusScore >= 80 -> Success
        focusScore >= 50 -> Warning
        else             -> Error.copy(alpha = 0.8f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.bodyMedium, color = OnSurface2
                    )
                    Text(
                        if (userName.isNotBlank()) "Hey, $userName" else "Good day",
                        style = MaterialTheme.typography.headlineLarge, color = OnSurface
                    )
                }
                IconButton(onClick = { showQuickAdd = true },
                    modifier = Modifier.clip(CircleShape).background(Purple80).size(44.dp)) {
                    Icon(Icons.Default.Add, "Quick Add",
                        tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // ── Active session banner ─────────────────────────────────────────
            AnimatedVisibility(visible = session.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Purple80.copy(alpha = 0.15f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Purple80))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NOW", style = MaterialTheme.typography.bodySmall,
                            color = Purple60, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(session.taskName, style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface, fontWeight = FontWeight.SemiBold)
                        val remaining = session.totalSeconds - session.elapsedSeconds
                        Text("${remaining / 60}m ${remaining % 60}s remaining",
                            style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                    OutlinedButton(
                        onClick = { FocusSessionService.end(completed = false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("End", style = MaterialTheme.typography.bodySmall) }
                }
            }

            // ── Daily focus goal bar ──────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Surface2).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily Focus Goal", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text("${focusToday}m / ${dailyGoal}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (goalPct >= 1f) Success else Purple60)
                }
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface3)) {
                    Box(modifier = Modifier.fillMaxWidth(goalPct).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (goalPct >= 1f) Success else Purple80))
                }
                if (goalPct >= 1f) Text("Goal reached! Great work.",
                    color = Success, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            // ── Stat cards ───────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Streak",       "$streak d",         Purple80,  Modifier.weight(1f))
                StatCard("Done",         "$completedToday",   Success,   Modifier.weight(1f))
                StatCard("Blocked hits", "$blockedAttempts",  Error.copy(alpha=0.8f), Modifier.weight(1f))
                StatCard("Focus score",  "$focusScore",       scoreColor, Modifier.weight(1f))
            }

            // ── Daily allowances usage ────────────────────────────────────────
            if (allowances.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's App Allowances", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                    allowances.forEach { a ->
                        val usedMins  = DailyAllowanceTracker.getUsageMinutes(a.processName).toInt()
                        val pct       = (usedMins.toFloat() / a.allowanceMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val isBlocked = a.processName.lowercase() in DailyAllowanceTracker.blockedProcesses
                        val barColor  = when {
                            isBlocked   -> Error.copy(alpha = 0.8f)
                            pct > 0.75f -> Warning
                            else        -> Purple80
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(a.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isBlocked) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                            .background(Error.copy(alpha=0.15f))
                                            .padding(horizontal=4.dp, vertical=1.dp)) {
                                            Text("blocked", style = MaterialTheme.typography.bodySmall, color = Error, fontSize = 9.sp)
                                        }
                                    }
                                    Text("${usedMins}m / ${a.allowanceMinutes}m",
                                        style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Surface3)) {
                                Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(barColor))
                            }
                        }
                    }
                }
            }

            // ── Today's tasks ─────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today's Tasks", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                TextButton(onClick = onNavigateTasks) { Text("View All", color = Purple80) }
            }

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Surface2).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarToday, null, tint = OnSurface2, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No tasks scheduled for today", color = OnSurface2)
                        TextButton(onClick = { showQuickAdd = true }) { Text("Add a task", color = Purple80) }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasks.take(6).forEach { task ->
                        TaskCard(
                            task = task,
                            onComplete   = { Database.completeTask(task.id); reload() },
                            onDelete     = { Database.deleteTask(task.id); reload() },
                            onStartFocus = { onStartFocus(task) }
                        )
                    }
                    if (tasks.size > 6) {
                        TextButton(onClick = onNavigateTasks, modifier = Modifier.align(Alignment.End)) {
                            Text("${tasks.size - 6} more tasks…", color = Purple80)
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showQuickAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = Purple80,
            contentColor = androidx.compose.ui.graphics.Color.White,
            shape = CircleShape
        ) { Icon(Icons.Default.Add, "Add Task", modifier = Modifier.size(24.dp)) }
    }

    if (showQuickAdd) {
        QuickAddDialog(
            onDismiss = { showQuickAdd = false },
            onSave = { task ->
                Database.upsertTask(task)
                reload()
                showQuickAdd = false
            }
        )
    }
}

// ── Dialogs + helpers ─────────────────────────────────────────────────────────

@Composable
private fun QuickAddDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title    by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("25") }
    var time     by remember { mutableStateOf("") }
    val today    = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Quick Add Task", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Task name") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Min") }, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = time, onValueChange = { time = it },
                        label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 30, 45, 60).forEach { m ->
                        FilterChip(selected = duration == m.toString(), onClick = { duration = m.toString() },
                            label = { Text("${m}m") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    onSave(Task(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        durationMinutes = duration.toIntOrNull() ?: 25,
                        scheduledDate = today,
                        scheduledTime = time.ifBlank { null },
                        createdAt = LocalDateTime.now()
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Surface2).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label,  style = MaterialTheme.typography.bodySmall,    color = OnSurface2)
    }
}
