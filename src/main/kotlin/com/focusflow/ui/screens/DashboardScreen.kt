package com.focusflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.DailyAllowance
import com.focusflow.data.models.Task
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.services.FocusInsightsService
import com.focusflow.services.FocusSessionService
import com.focusflow.services.SessionPin
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun DashboardScreen(onStartFocus: (Task) -> Unit, onNavigateTasks: () -> Unit) {
    val today   = LocalDate.now()
    val session by FocusSessionService.state.collectAsState()
    val scope   = rememberCoroutineScope()

    var tasks            by remember { mutableStateOf(listOf<Task>()) }
    var streak           by remember { mutableStateOf(0) }
    var focusToday       by remember { mutableStateOf(0) }
    var completedToday   by remember { mutableStateOf(0) }
    var dailyGoal        by remember { mutableStateOf(120) }
    var showQuickAdd     by remember { mutableStateOf(false) }
    var showEndPinDialog by remember { mutableStateOf(false) }
    var userName         by remember { mutableStateOf("") }
    var allowances       by remember { mutableStateOf(listOf<DailyAllowance>()) }
    var blockedAttempts  by remember { mutableStateOf(0) }
    var insights         by remember { mutableStateOf(FocusInsightsService.Insights()) }

    fun reload() {
        scope.launch {
            val t  = withContext(Dispatchers.IO) { Database.getTasksForDate(today) }
            val s  = withContext(Dispatchers.IO) { Database.getCurrentStreak() }
            val ft = withContext(Dispatchers.IO) { Database.getTotalFocusMinutesToday() }
            val dg = withContext(Dispatchers.IO) { Database.getSetting("daily_focus_goal")?.toIntOrNull() ?: 120 }
            val un = withContext(Dispatchers.IO) { Database.getSetting("user_name") ?: "" }
            val al = withContext(Dispatchers.IO) { Database.getDailyAllowances() }
            val ba = withContext(Dispatchers.IO) { Database.getTemptationsInRange(today.toString(), today.toString()) }
            tasks          = t
            streak         = s
            focusToday     = ft
            completedToday = t.count { it.completed }
            dailyGoal      = dg
            userName       = un
            allowances     = al
            blockedAttempts = ba
            val ins = withContext(Dispatchers.IO) { FocusInsightsService.compute() }
            insights = ins
        }
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

    val dashScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .verticalScroll(dashScrollState)
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
                    val hour = LocalTime.now().hour
                    val timeGreeting = when {
                        hour < 12 -> "Good morning"
                        hour < 17 -> "Good afternoon"
                        else      -> "Good evening"
                    }
                    Text(
                        if (userName.isNotBlank()) "$timeGreeting, $userName" else timeGreeting,
                        style = MaterialTheme.typography.headlineLarge, color = OnSurface
                    )
                    if (tasks.isNotEmpty()) {
                        Text(
                            "$completedToday / ${tasks.size} tasks done",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (completedToday == tasks.size) Success else OnSurface2
                        )
                    }
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
                        onClick = {
                            if (SessionPin.isSet()) showEndPinDialog = true
                            else FocusSessionService.end(completed = false)
                        },
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
                    val barModifier = Modifier.fillMaxWidth(goalPct).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    if (goalPct >= 1f) {
                        Box(modifier = barModifier.background(Success))
                    } else if (goalPct > 0f) {
                        Box(modifier = barModifier.background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color(0xFF6C63FF),
                                    androidx.compose.ui.graphics.Color(0xFF9D97FF)
                                )
                            )
                        ))
                    }
                }
                if (goalPct >= 1f) Text("Goal reached! Great work.",
                    color = Success, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            // ── Stat cards ───────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Streak",       "$streak d",         Purple80,             Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                StatCard("Done",         "$completedToday",   Success,              Icons.Default.CheckCircle,  Modifier.weight(1f))
                StatCard("Blocked hits", "$blockedAttempts",  Error.copy(alpha=0.8f), Icons.Default.Block,      Modifier.weight(1f))
                StatCard("Focus score",  "$focusScore",       scoreColor,           Icons.Default.Star,         Modifier.weight(1f))
            }

            // ── Focus Insights ────────────────────────────────────────────────
            if (insights.avgSessionMinutes > 0 || insights.totalHoursAllTime > 0f) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Your Focus Patterns",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val bestHour = insights.mostProductiveHour
                        val hourLabel = if (bestHour != null) {
                            val ampm = if (bestHour < 12) "AM" else "PM"
                            val h12  = when (bestHour) { 0 -> 12; in 1..12 -> bestHour; else -> bestHour - 12 }
                            "${h12}${ampm}"
                        } else "—"
                        InsightChip("Peak Hour",  hourLabel,
                            if (bestHour != null) Purple80 else OnSurface2, Modifier.weight(1f))

                        val dayLabel = insights.bestDayOfWeek
                            ?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.take(3) ?: "—"
                        InsightChip("Best Day",   dayLabel,
                            if (insights.bestDayOfWeek != null) Success else OnSurface2, Modifier.weight(1f))

                        InsightChip("Avg Session", "${insights.avgSessionMinutes}m",
                            Warning, Modifier.weight(1f))

                        val pct = (insights.completionRate * 100).toInt()
                        InsightChip("Completion",  "$pct%",
                            if (insights.completionRate >= 0.7f) Success else Warning, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple80.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val h = insights.totalHoursAllTime.toInt()
                        val m = ((insights.totalHoursAllTime - h) * 60).toInt()
                        val timeStr = if (h > 0) "${h}h ${m}m total" else "${m}m total"
                        Text(timeStr, style = MaterialTheme.typography.bodySmall, color = Purple60)
                        Text(
                            "Best streak: ${insights.longestStreak}d",
                            style = MaterialTheme.typography.bodySmall, color = Purple60
                        )
                        Text(
                            "This week: ${insights.sessionsThisWeek} sessions",
                            style = MaterialTheme.typography.bodySmall, color = Purple60
                        )
                    }
                }
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

            // ── Today's schedule (time-slotted tasks only) ───────────────────
            val scheduledToday = tasks.filter { it.scheduledTime != null }.sortedBy { it.scheduledTime ?: "" }
            if (scheduledToday.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Today's Schedule", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                    scheduledToday.forEach { task ->
                        val pColor = when (task.priority) { "high" -> Error; "medium" -> Warning; else -> Success }
                        val isDone = task.completed || task.skipped
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                task.scheduledTime ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Purple60,
                                modifier = Modifier.width(42.dp)
                            )
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (isDone) OnSurface2.copy(alpha = 0.3f) else pColor)
                            )
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDone) OnSurface2 else OnSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                "${task.durationMinutes}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface2
                            )
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

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = 80.dp),
            adapter = rememberScrollbarAdapter(dashScrollState)
        )

        // ── FAB ───────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showQuickAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = Purple80,
            contentColor = androidx.compose.ui.graphics.Color.White,
            shape = CircleShape
        ) { Icon(Icons.Default.Add, "Add Task", modifier = Modifier.size(24.dp)) }
    }

    if (showEndPinDialog) {
        DashboardEndSessionPinDialog(
            onDismiss  = { showEndPinDialog = false },
            onVerified = { showEndPinDialog = false; FocusSessionService.end(completed = false) }
        )
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

@Composable
private fun DashboardEndSessionPinDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(22.dp))
                Text("PIN Required", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your session PIN to end the session early.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { pin = it; error = false },
                    label         = { Text("PIN") },
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = error,
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Purple80,
                        unfocusedBorderColor = OnSurface2,
                        errorBorderColor     = Error
                    )
                )
                if (error) Text("Incorrect PIN. Try again.", color = Error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (SessionPin.verify(pin)) onVerified() else error = true },
                colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f))
            ) { Text("End Session") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

// ── Dialogs + helpers ─────────────────────────────────────────────────────────

@Composable
private fun QuickAddDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title    by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("25") }
    var time     by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Priority:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    listOf("low" to Success, "medium" to Warning, "high" to Error).forEach { (p, color) ->
                        FilterChip(
                            selected = priority == p,
                            onClick  = { priority = p },
                            label    = { Text(p.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(color))
                            }
                        )
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
                        priority = priority,
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
private fun InsightChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface3)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color,
            fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color.copy(alpha = 0.7f))
        )
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = color.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            Text(label,  style = MaterialTheme.typography.bodySmall,    color = OnSurface2)
        }
    }
}
