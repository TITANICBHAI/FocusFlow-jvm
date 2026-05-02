package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.FocusSession
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ReportRange { TODAY, WEEK, MONTH, ALL }

@Composable
fun ReportsScreen() {
    var range    by remember { mutableStateOf(ReportRange.WEEK) }
    var sessions by remember { mutableStateOf(listOf<FocusSession>()) }
    var filter   by remember { mutableStateOf("all") }

    fun reload() {
        val today = LocalDate.now()
        val start = when (range) {
            ReportRange.TODAY -> today
            ReportRange.WEEK  -> today.minusDays(6)
            ReportRange.MONTH -> today.minusDays(29)
            ReportRange.ALL   -> LocalDate.of(2020, 1, 1)
        }
        sessions = Database.getSessionsInDateRange(start, today)
    }

    LaunchedEffect(range) { reload() }

    val filtered = when (filter) {
        "completed"   -> sessions.filter { it.completed }
        "interrupted" -> sessions.filter { it.interrupted }
        else          -> sessions
    }
    val grouped = filtered.groupBy { it.startTime.toLocalDate() }
        .entries.sortedByDescending { it.key }

    val totalMins      = filtered.filter { it.completed }.sumOf { it.actualMinutes }
    val completedCount = filtered.count { it.completed }
    val interruptedCount = filtered.count { it.interrupted }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 0.dp)) {
            Text("Session Reports", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Spacer(Modifier.height(16.dp))

            // Range tabs
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(ReportRange.TODAY to "Today", ReportRange.WEEK to "Week", ReportRange.MONTH to "Month", ReportRange.ALL to "All Time").forEach { (r, label) ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (range == r) Purple80 else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { range = r }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (range == r) androidx.compose.ui.graphics.Color.White else OnSurface2, fontWeight = if (range == r) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // Summary row
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val h = totalMins / 60; val m = totalMins % 60
                    MiniStat2(if (h > 0) "${h}h ${m}m" else "${m}m", "Focus", Purple80, Modifier.weight(1f))
                    MiniStat2("$completedCount", "Completed", Success, Modifier.weight(1f))
                    MiniStat2("$interruptedCount", "Interrupted", Error.copy(alpha = 0.8f), Modifier.weight(1f))
                    MiniStat2("${filtered.size}", "Total", Purple60, Modifier.weight(1f))
                }
            }

            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "completed" to "Completed", "interrupted" to "Interrupted").forEach { (f, label) ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No sessions in this range", color = OnSurface2)
                    }
                }
            } else {
                grouped.forEach { (date, daySessions) ->
                    item {
                        val isToday = date == LocalDate.now()
                        Text(
                            if (isToday) "Today" else if (date == LocalDate.now().minusDays(1)) "Yesterday" else date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isToday) Purple80 else OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(daySessions) { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface2).padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (session.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    null,
                                    tint = if (session.completed) Success else Error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(session.taskName, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                    val start = session.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                                    val end   = session.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
                                    Text("$start – $end", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${session.actualMinutes}m", color = Purple60, fontWeight = FontWeight.SemiBold)
                                Text("of ${session.plannedMinutes}m", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat2(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
}
