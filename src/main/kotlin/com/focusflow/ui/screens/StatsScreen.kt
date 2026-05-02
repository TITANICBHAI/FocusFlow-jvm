package com.focusflow.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.DayFocusStats
import com.focusflow.data.models.FocusSession
import com.focusflow.data.models.TemptationEntry
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen() {
    var sessions by remember { mutableStateOf(listOf<FocusSession>()) }
    var streak by remember { mutableStateOf(0) }
    var temptations by remember { mutableStateOf(listOf<TemptationEntry>()) }
    var focusToday by remember { mutableStateOf(0) }
    var weeklyStats by remember { mutableStateOf(listOf<DayFocusStats>()) }

    LaunchedEffect(Unit) {
        sessions = Database.getRecentSessions(30)
        streak = Database.getCurrentStreak()
        temptations = Database.getTemptationLog(7)
        focusToday = Database.getTotalFocusMinutesToday()
        weeklyStats = Database.getFocusMinutesByDay(7)
    }

    val completedSessions = sessions.filter { it.completed }
    val totalFocusAllTime = sessions.sumOf { it.actualMinutes }
    val avgSession = if (completedSessions.isEmpty()) 0
    else completedSessions.sumOf { it.actualMinutes } / completedSessions.size
    val completionRate = if (sessions.isEmpty()) 0
    else (completedSessions.size * 100) / sessions.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBig("🔥", "$streak", "Streak", "days", Modifier.weight(1f))
                StatBig("⏱", "${focusToday}m", "Today", "minutes", Modifier.weight(1f))
                StatBig("🎯", "${completedSessions.size}", "Sessions", "completed", Modifier.weight(1f))
                StatBig("📊", "${totalFocusAllTime / 60}h ${totalFocusAllTime % 60}m", "Total", "all time", Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBig("⌛", "${avgSession}m", "Avg Session", "per session", Modifier.weight(1f))
                StatBig("✅", "$completionRate%", "Completion", "rate", Modifier.weight(1f))
                StatBig("🚫", "${temptations.size}", "Blocked", "last 7 days", Modifier.weight(1f))
                StatBig("📅", "${sessions.size}", "Total Runs", "30 days", Modifier.weight(1f))
            }
        }

        // 7-day bar chart
        if (weeklyStats.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Focus — Last 7 Days", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    FocusBarChart(stats = weeklyStats)
                }
            }
        }

        item {
            Text("Recent Sessions", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Surface2).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sessions yet. Start a focus session!", color = OnSurface2)
                }
            }
        } else {
            items(sessions.take(20)) { session -> SessionRow(session) }
        }

        if (temptations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Temptation Log — Last 7 Days", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${temptations.size} blocked attempts",
                    style = MaterialTheme.typography.bodyMedium, color = OnSurface2
                )
            }

            val topApps = temptations
                .groupBy { it.displayName }
                .mapValues { it.value.size }
                .entries.sortedByDescending { it.value }.take(8)

            item {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val maxCount = topApps.maxOfOrNull { it.value } ?: 1
                    topApps.forEach { (app, count) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(app, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text("$count×", color = Purple60, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Surface3)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(count.toFloat() / maxCount)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Purple80.copy(alpha = 0.8f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusBarChart(stats: List<DayFocusStats>) {
    val maxMins = stats.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(30) ?: 30
    val barColor = Purple80
    val emptyColor = Surface3
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val barWidth = (size.width - 16.dp.toPx() * (stats.size - 1)) / stats.size
            val maxH = size.height - 24.dp.toPx()

            stats.forEachIndexed { i, day ->
                val x = i * (barWidth + 16.dp.toPx())
                val barH = if (maxMins > 0) (day.totalMinutes.toFloat() / maxMins) * maxH else 0f
                val y = size.height - 24.dp.toPx() - barH

                drawRoundRect(
                    color = emptyColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, maxH),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                if (barH > 0) {
                    drawRoundRect(
                        color = if (day.date == today) barColor else barColor.copy(alpha = 0.6f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH + 24.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEach { day ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (day.totalMinutes > 0) "${day.totalMinutes}m" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (day.totalMinutes > 0) Purple80 else OnSurface2
                    )
                    Text(
                        day.date.format(DateTimeFormatter.ofPattern("EEE")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (day.date == today) OnSurface else OnSurface2
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBig(emoji: String, value: String, label: String, sub: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Purple80)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface)
        Text(sub, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Surface2).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(session.taskName, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(
                session.startTime.format(DateTimeFormatter.ofPattern("MMM d · HH:mm")),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${session.actualMinutes}m", color = Purple60)
            Icon(
                if (session.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                null,
                tint = if (session.completed) Success else Error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
