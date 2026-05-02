package com.focusflow.ui.screens

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
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.FocusSession
import com.focusflow.data.models.TemptationEntry
import com.focusflow.ui.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen() {
    var sessions by remember { mutableStateOf(listOf<FocusSession>()) }
    var streak by remember { mutableStateOf(0) }
    var temptations by remember { mutableStateOf(listOf<TemptationEntry>()) }
    var focusToday by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        sessions = Database.getRecentSessions(30)
        streak = Database.getCurrentStreak()
        temptations = Database.getTemptationLog(7)
        focusToday = Database.getTotalFocusMinutesToday()
    }

    val completedSessions = sessions.filter { it.completed }
    val totalFocusAllTime = sessions.sumOf { it.actualMinutes }
    val avgSession = if (completedSessions.isEmpty()) 0 else
        completedSessions.sumOf { it.actualMinutes } / completedSessions.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
        }

        // Summary cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBig("🔥", "$streak", "Current Streak", "days", Modifier.weight(1f))
                StatBig("⏱", "${focusToday}m", "Focus Today", "minutes", Modifier.weight(1f))
                StatBig("🎯", "${completedSessions.size}", "Sessions Done", "all time", Modifier.weight(1f))
                StatBig("📊", "${totalFocusAllTime}m", "Total Focus", "all time", Modifier.weight(1f))
            }
        }

        // Session history
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
            items(sessions.take(20)) { session ->
                SessionRow(session)
            }
        }

        // Temptation log
        if (temptations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Temptation Log (Last 7 Days)", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${temptations.size} blocked app attempts",
                    style = MaterialTheme.typography.bodyMedium, color = OnSurface2
                )
            }

            val topApps = temptations
                .groupBy { it.displayName }
                .mapValues { it.value.size }
                .entries.sortedByDescending { it.value }
                .take(5)

            item {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topApps.forEach { (app, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app, color = OnSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width((count * 8).coerceAtMost(120).dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Purple80.copy(alpha = 0.7f))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$count×", color = Purple60, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
