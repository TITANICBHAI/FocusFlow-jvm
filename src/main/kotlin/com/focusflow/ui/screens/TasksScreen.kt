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
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.Task
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun TasksScreen(onStartFocus: (Task) -> Unit) {
    var tasks by remember { mutableStateOf(listOf<Task>()) }
    var showAdd by remember { mutableStateOf(false) }
    var filterCompleted by remember { mutableStateOf(false) }

    fun reload() { tasks = Database.getTasks() }
    LaunchedEffect(Unit) { reload() }

    Row(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tasks", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterCompleted,
                        onClick = { filterCompleted = !filterCompleted },
                        label = { Text("Show Completed") }
                    )
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Task")
                    }
                }
            }

            val displayed = if (filterCompleted) tasks else tasks.filter { !it.completed }
            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks yet. Click 'New Task' to add one.", color = OnSurface2)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onComplete = { Database.completeTask(task.id); reload() },
                            onDelete = { Database.deleteTask(task.id); reload() },
                            onStartFocus = { onStartFocus(task) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onSave = { task ->
                Database.upsertTask(task)
                reload()
                showAdd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("25") }
    var priority by remember { mutableStateOf("medium") }
    var recurring by remember { mutableStateOf(false) }
    var recurringType by remember { mutableStateOf("daily") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New Task", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (min)") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = OnSurface2
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Priority", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("low", "medium", "high").forEach { p ->
                                FilterChip(
                                    selected = priority == p,
                                    onClick = { priority = p },
                                    label = { Text(p, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Recurring", color = OnSurface)
                }
                if (recurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("daily", "weekdays", "weekly", "monthly").forEach { t ->
                            FilterChip(
                                selected = recurringType == t,
                                onClick = { recurringType = t },
                                label = { Text(t, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    onSave(
                        Task(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            description = description.trim(),
                            durationMinutes = durationMinutes.toIntOrNull() ?: 25,
                            priority = priority,
                            recurring = recurring,
                            recurringType = if (recurring) recurringType else null,
                            scheduledDate = LocalDate.now()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) }
        }
    )
}
