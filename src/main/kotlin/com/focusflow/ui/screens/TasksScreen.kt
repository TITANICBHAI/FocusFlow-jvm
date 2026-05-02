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
import com.focusflow.data.models.Task
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun TasksScreen(onStartFocus: (Task) -> Unit) {
    var tasks           by remember { mutableStateOf(listOf<Task>()) }
    var showAdd         by remember { mutableStateOf(false) }
    var filterCompleted by remember { mutableStateOf(false) }
    var editTask        by remember { mutableStateOf<Task?>(null) }

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
                Column {
                    Text("Tasks", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
                    val done  = tasks.count { it.completed }
                    val total = tasks.size
                    if (total > 0) Text("$done/$total done", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterCompleted, onClick = { filterCompleted = !filterCompleted }, label = { Text("Show Completed") })
                    Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Task")
                    }
                }
            }

            val displayed = if (filterCompleted) tasks else tasks.filter { !it.completed && !it.skipped }
            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("No tasks yet.", color = OnSurface2)
                        TextButton(onClick = { showAdd = true }) { Text("Add your first task", color = Purple80) }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { task ->
                        TaskCard(
                            task        = task,
                            onComplete  = { Database.completeTask(task.id); reload() },
                            onDelete    = { Database.deleteTask(task.id); reload() },
                            onStartFocus = { onStartFocus(task) },
                            onEdit      = { editTask = task },
                            onSkip      = { Database.skipTask(task.id); reload() }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(onDismiss = { showAdd = false }, onSave = { task ->
            Database.upsertTask(task); reload(); showAdd = false
        })
    }

    if (editTask != null) {
        EditTaskDialog(
            task      = editTask!!,
            onDismiss = { editTask = null },
            onSave    = { task -> Database.upsertTask(task); reload(); editTask = null },
            onDelete  = { Database.deleteTask(editTask!!.id); reload(); editTask = null }
        )
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title         by remember { mutableStateOf("") }
    var description   by remember { mutableStateOf("") }
    var duration      by remember { mutableStateOf("25") }
    var date          by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var time          by remember { mutableStateOf("") }
    var priority      by remember { mutableStateOf("medium") }
    var focusMode     by remember { mutableStateOf(false) }
    var recurring     by remember { mutableStateOf(false) }
    var recurringType by remember { mutableStateOf("daily") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New Task", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.weight(1f), colors = fieldColors(), singleLine = true)
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f), colors = fieldColors(), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duration:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    listOf(15, 25, 30, 45, 60, 90).forEach { m ->
                        FilterChip(selected = duration == m.toString(), onClick = { duration = m.toString() }, label = { Text("${m}m") })
                    }
                }
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Custom duration (min)") }, modifier = Modifier.width(160.dp), colors = fieldColors(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Priority:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    listOf("low", "medium", "high").forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = focusMode, onCheckedChange = { focusMode = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                    Text("Focus mode (block distracting apps)", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                    Text("Recurring task", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
                if (recurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Repeats:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        listOf("daily", "weekdays", "weekly").forEach { t ->
                            FilterChip(selected = recurringType == t, onClick = { recurringType = t }, label = { Text(t.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val parsedDate = try { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: Exception) { LocalDate.now() }
                    onSave(Task(id = UUID.randomUUID().toString(), title = title.trim(), description = description.trim(), durationMinutes = duration.toIntOrNull() ?: 25, scheduledDate = parsedDate, scheduledTime = time.ifBlank { null }, priority = priority, focusMode = focusMode, recurring = recurring, recurringType = if (recurring) recurringType else null, createdAt = LocalDateTime.now()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Add Task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
fun EditTaskDialog(task: Task, onDismiss: () -> Unit, onSave: (Task) -> Unit, onDelete: () -> Unit) {
    var title         by remember { mutableStateOf(task.title) }
    var description   by remember { mutableStateOf(task.description) }
    var duration      by remember { mutableStateOf(task.durationMinutes.toString()) }
    var date          by remember { mutableStateOf(task.scheduledDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "") }
    var time          by remember { mutableStateOf(task.scheduledTime ?: "") }
    var priority      by remember { mutableStateOf(task.priority) }
    var focusMode     by remember { mutableStateOf(task.focusMode) }
    var recurring     by remember { mutableStateOf(task.recurring) }
    var recurringType by remember { mutableStateOf(task.recurringType ?: "daily") }
    var showConfirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Task", color = OnSurface)
                IconButton(onClick = { showConfirmDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Error)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, modifier = Modifier.weight(1f), colors = fieldColors(), singleLine = true)
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f), colors = fieldColors(), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duration:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    listOf(15, 25, 30, 45, 60, 90).forEach { m ->
                        FilterChip(selected = duration == m.toString(), onClick = { duration = m.toString() }, label = { Text("${m}m") })
                    }
                }
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Duration (min)") }, modifier = Modifier.width(160.dp), colors = fieldColors(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Priority:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    listOf("low", "medium", "high").forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = focusMode, onCheckedChange = { focusMode = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                    Text("Focus mode", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                    Text("Recurring task", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
                if (recurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Repeats:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        listOf("daily", "weekdays", "weekly").forEach { t ->
                            FilterChip(selected = recurringType == t, onClick = { recurringType = t }, label = { Text(t.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = try { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: Exception) { task.scheduledDate }
                    onSave(task.copy(title = title.trim(), description = description.trim(), durationMinutes = duration.toIntOrNull() ?: task.durationMinutes, scheduledDate = parsedDate, scheduledTime = time.ifBlank { null }, priority = priority, focusMode = focusMode, recurring = recurring, recurringType = if (recurring) recurringType else null))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = Surface2,
            title = { Text("Delete Task?", color = Error) },
            text = { Text("This will permanently delete \"${task.title}\".", color = OnSurface2) },
            confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel", color = OnSurface2) } }
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
