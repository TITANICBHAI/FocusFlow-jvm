package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.Task
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(onStartFocus: (Task) -> Unit, onNavigateTasks: () -> Unit) {
    val today = LocalDate.now()
    var tasks by remember { mutableStateOf(listOf<Task>()) }
    var streak by remember { mutableStateOf(0) }
    var focusMinutesToday by remember { mutableStateOf(0) }
    var completedToday by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        tasks = Database.getTasksForDate(today)
        streak = Database.getCurrentStreak()
        focusMinutesToday = Database.getTotalFocusMinutesToday()
        completedToday = tasks.count { it.completed }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column {
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface2
            )
            Text("Good day", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
        }

        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("🔥", "$streak", "Day Streak", Modifier.weight(1f))
            StatCard("⏱", "${focusMinutesToday}m", "Focus Today", Modifier.weight(1f))
            StatCard("✅", "$completedToday", "Completed", Modifier.weight(1f))
            StatCard("📋", "${tasks.size}", "Tasks Today", Modifier.weight(1f))
        }

        // Today's tasks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Today's Tasks", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            TextButton(onClick = onNavigateTasks) {
                Text("View All", color = Purple80)
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📅", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("No tasks scheduled for today", color = OnSurface2)
                    TextButton(onClick = onNavigateTasks) {
                        Text("Add a task", color = Purple80)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.take(5).forEach { task ->
                    TaskCard(
                        task = task,
                        onComplete = {
                            Database.completeTask(task.id)
                            tasks = Database.getTasksForDate(today)
                            completedToday = tasks.count { it.completed }
                        },
                        onDelete = {
                            Database.deleteTask(task.id)
                            tasks = Database.getTasksForDate(today)
                        },
                        onStartFocus = { onStartFocus(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Purple80)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
