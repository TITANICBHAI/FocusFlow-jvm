package com.focusflow.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.FocusSession
import com.focusflow.data.models.TemptationEntry
import com.focusflow.services.WeeklyReportService
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ReportRange { TODAY, WEEK, MONTH, ALL }
private enum class ReportTab   { SESSIONS, TIMELINE, BLOCKED }

@Composable
fun ReportsScreen() {
    var range    by remember { mutableStateOf(ReportRange.WEEK) }
    var tab      by remember { mutableStateOf(ReportTab.SESSIONS) }
    var sessions by remember { mutableStateOf(listOf<FocusSession>()) }
    var temptLog by remember { mutableStateOf(listOf<TemptationEntry>()) }
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
        temptLog = Database.getTemptationLog(
            when (range) { ReportRange.TODAY -> 1; ReportRange.WEEK -> 7; ReportRange.MONTH -> 30; ReportRange.ALL -> 3650 }
        )
    }

    LaunchedEffect(range) { reload() }

    val filtered = when (filter) {
        "completed"   -> sessions.filter { it.completed }
        "interrupted" -> sessions.filter { it.interrupted }
        else          -> sessions
    }
    val grouped = filtered
        .groupBy { it.startTime.toLocalDate() }
        .entries.sortedByDescending { it.key }

    val totalMins        = filtered.filter { it.completed }.sumOf { it.actualMinutes }
    val completedCount   = filtered.count { it.completed }
    val interruptedCount = filtered.count { it.interrupted }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Header + range selector ──────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp)) {
            Text("Session Reports", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(ReportRange.TODAY to "Today", ReportRange.WEEK to "Week",
                       ReportRange.MONTH to "Month", ReportRange.ALL to "All Time").forEach { (r, label) ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (range == r) Purple80 else Color.Transparent)
                            .clickable { range = r }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = if (range == r) Color.White else OnSurface2,
                            fontWeight = if (range == r) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Summary stat row ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val h = totalMins / 60; val m = totalMins % 60
                MiniStat2(if (h > 0) "${h}h ${m}m" else "${m}m", "Focus",       Purple80,                 Modifier.weight(1f))
                MiniStat2("$completedCount",   "Completed",   Success,                  Modifier.weight(1f))
                MiniStat2("$interruptedCount", "Interrupted", Error.copy(alpha = 0.8f), Modifier.weight(1f))
                MiniStat2("${temptLog.size}",  "Blocked hits",Warning,                  Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // ── Tab row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface2).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(ReportTab.SESSIONS to "Sessions", ReportTab.TIMELINE to "Timeline",
                       ReportTab.BLOCKED to "Blocked Apps").forEach { (t, label) ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (tab == t) Surface3 else Color.Transparent)
                            .clickable { tab = t }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = if (tab == t) OnSurface else OnSurface2,
                            fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        // ── Tab content ──────────────────────────────────────────────────────
        when (tab) {
            ReportTab.SESSIONS  -> SessionsTab(filtered, grouped, filter, onFilterChange = { filter = it })
            ReportTab.TIMELINE  -> TimelineTab(filtered)
            ReportTab.BLOCKED   -> BlockedAppsTab(temptLog)
        }
    }
}

// ── Sessions tab ──────────────────────────────────────────────────────────────

@Composable
private fun SessionsTab(
    filtered: List<FocusSession>,
    grouped: List<Map.Entry<LocalDate, List<FocusSession>>>,
    filter: String,
    onFilterChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "completed" to "Completed", "interrupted" to "Interrupted").forEach { (f, label) ->
                    FilterChip(selected = filter == f, onClick = { onFilterChange(f) }, label = { Text(label) })
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(32.dp),
                    contentAlignment = Alignment.Center) {
                    Text("No sessions in this range", color = OnSurface2)
                }
            }
        } else {
            grouped.forEach { (date, daySessions) ->
                item {
                    val isToday = date == LocalDate.now()
                    val label = when {
                        isToday        -> "Today"
                        date == LocalDate.now().minusDays(1) -> "Yesterday"
                        else           -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall,
                            color = if (isToday) Purple80 else OnSurface, fontWeight = FontWeight.SemiBold)
                        val dayMins = daySessions.filter { it.completed }.sumOf { it.actualMinutes }
                        if (dayMins > 0) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Purple80.copy(alpha=0.12f)).padding(horizontal=6.dp, vertical=2.dp)) {
                                Text("${dayMins}m", style = MaterialTheme.typography.bodySmall, color = Purple80)
                            }
                        }
                    }
                }
                items(daySessions) { session ->
                    SessionRow(session)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
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
                val startFmt = session.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                val endFmt   = session.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
                Text("$startFmt – $endFmt", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${session.actualMinutes}m", color = Purple60, fontWeight = FontWeight.SemiBold)
            Text("of ${session.plannedMinutes}m", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
    }
}

// ── Timeline tab ──────────────────────────────────────────────────────────────

@Composable
private fun TimelineTab(sessions: List<FocusSession>) {
    val byDay = sessions
        .filter { it.completed || it.interrupted }
        .groupBy { it.startTime.toLocalDate() }
        .entries.sortedByDescending { it.key }

    if (byDay.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No sessions to show.", color = OnSurface2)
        }
        return
    }

    val maxMins = byDay.maxOf { (_, s) -> s.sumOf { it.actualMinutes }.coerceAtLeast(1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        items(byDay) { (date, daySessions) ->
            val isToday   = date == LocalDate.now()
            val dayLabel  = when {
                isToday -> "Today"
                date == LocalDate.now().minusDays(1) -> "Yesterday"
                else    -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            }
            val totalMins = daySessions.filter { it.completed }.sumOf { it.actualMinutes }
            val sortedSessions = daySessions.sortedBy { it.startTime }

            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel, color = if (isToday) Purple80 else OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("${totalMins}m focused", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }

                // Proportional bar chart for the day
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp)).background(Surface3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dayMax = daySessions.sumOf { it.actualMinutes }.coerceAtLeast(1)
                    sortedSessions.forEach { s ->
                        val frac = s.actualMinutes.toFloat() / dayMax
                        val color = if (s.completed) Purple80 else Error.copy(alpha = 0.6f)
                        Box(modifier = Modifier.fillMaxHeight().weight(frac).background(color))
                        Spacer(Modifier.width(2.dp))
                    }
                }

                // Mini session chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortedSessions.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(if (s.completed) Success else Error.copy(alpha = 0.7f)))
                            Text(s.taskName, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                            Text(
                                s.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodySmall, color = OnSurface2
                            )
                            Text("${s.actualMinutes}m", style = MaterialTheme.typography.bodySmall, color = Purple60)
                        }
                    }
                }

                // Bar relative to week max
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val frac = totalMins.toFloat() / maxMins
                    Box(modifier = Modifier.height(6.dp).weight(frac).clip(RoundedCornerShape(3.dp)).background(Purple80))
                    Box(modifier = Modifier.height(6.dp).weight((1f - frac).coerceAtLeast(0.001f)).clip(RoundedCornerShape(3.dp)).background(Surface3))
                    Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                }
            }
        }
    }
}

// ── Blocked Apps tab ──────────────────────────────────────────────────────────

@Composable
private fun BlockedAppsTab(temptLog: List<TemptationEntry>) {
    val grouped = temptLog
        .groupBy { it.displayName }
        .entries.sortedByDescending { it.value.size }

    if (grouped.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Shield, null, tint = Success, modifier = Modifier.size(40.dp))
                Text("No blocked app attempts in this period.", color = OnSurface2)
            }
        }
        return
    }

    val maxCount = grouped.maxOf { it.value.size }.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Text("${temptLog.size} total blocked attempts across ${grouped.size} app(s)",
                style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }

        items(grouped) { (displayName, entries) ->
            val frac  = entries.size.toFloat() / maxCount
            val lastAttempt = entries.maxBy { it.timestamp }
                .timestamp.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Block, null, tint = Error, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(displayName, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.height(6.dp).weight(frac).clip(RoundedCornerShape(3.dp)).background(Error.copy(alpha = 0.7f)))
                        if (frac < 0.999f)
                            Box(modifier = Modifier.height(6.dp).weight(1f - frac).clip(RoundedCornerShape(3.dp)).background(Surface3))
                    }
                    Text("Last: $lastAttempt", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${entries.size}", color = Error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("attempts", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }

        // Recent raw log
        item {
            Spacer(Modifier.height(8.dp))
            Text("Recent attempts", style = MaterialTheme.typography.titleSmall, color = OnSurface)
        }
        items(temptLog.take(30)) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Error.copy(alpha = 0.6f)))
                Text(entry.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                Text(entry.timestamp.format(DateTimeFormatter.ofPattern("MMM d HH:mm")),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun MiniStat2(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
           horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
}
