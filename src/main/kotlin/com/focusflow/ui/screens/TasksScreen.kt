package com.focusflow.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.Task
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.ui.components.TaskCard
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun TasksScreen(onStartFocus: (Task) -> Unit) {
    var tasks                by remember { mutableStateOf(listOf<Task>()) }
    var showAdd              by remember { mutableStateOf(false) }
    var editTask             by remember { mutableStateOf<Task?>(null) }
    var searchQuery          by remember { mutableStateOf("") }
    var sortMode             by remember { mutableStateOf("date") }
    var priorityFilter       by remember { mutableStateOf("all") }
    var showCompletedSection by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch { tasks = withContext(Dispatchers.IO) { Database.getTasks() } }
    }
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
                    Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Task")
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search tasks…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
            )

            // Sort chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                listOf("date" to "By Date", "priority" to "By Priority", "title" to "By Title").forEach { (mode, label) ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick  = { sortMode = mode },
                        label    = { Text(label, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            // Priority filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Priority:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                listOf(
                    "all"    to "All",
                    "high"   to "High",
                    "medium" to "Medium",
                    "low"    to "Low"
                ).forEach { (p, label) ->
                    FilterChip(
                        selected = priorityFilter == p,
                        onClick  = { priorityFilter = p },
                        label    = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = if (p != "all") ({
                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(when (p) { "high" -> Error; "medium" -> Warning; else -> Success }))
                        }) else null
                    )
                }
            }

            val today = LocalDate.now()
            val priorityOrder = mapOf("high" to 0, "medium" to 1, "low" to 2)
            val base = run {
                val active = tasks.filter { !it.completed && !it.skipped }
                if (priorityFilter == "all") active else active.filter { it.priority == priorityFilter }
            }
            val filtered = if (searchQuery.isBlank()) base
                           else base.filter { it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }

            val overdueTasks = filtered.filter { it.scheduledDate != null && it.scheduledDate.isBefore(today) }
            val currentTasks = filtered.filter { it.scheduledDate == null || !it.scheduledDate.isBefore(today) }

            val sortedCurrent = when (sortMode) {
                "priority" -> currentTasks.sortedBy { priorityOrder[it.priority] ?: 1 }
                "title"    -> currentTasks.sortedBy { it.title.lowercase() }
                else       -> currentTasks.sortedWith(Comparator { a, b ->
                    when {
                        a.scheduledDate == null && b.scheduledDate == null -> 0
                        a.scheduledDate == null -> 1
                        b.scheduledDate == null -> -1
                        else -> a.scheduledDate.compareTo(b.scheduledDate)
                    }
                })
            }
            val displayed = overdueTasks + sortedCurrent

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
                val completedTasks = tasks.filter { it.completed || it.skipped }
                val tasksListState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = tasksListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (overdueTasks.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Error, modifier = Modifier.size(16.dp))
                                Text(
                                    "Overdue",
                                    style      = MaterialTheme.typography.titleSmall,
                                    color      = Error
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Error.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${overdueTasks.size}",
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = Error,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(overdueTasks, key = { "od_${it.id}" }) { task ->
                            TaskCard(
                                task         = task,
                                onComplete   = { scope.launch { withContext(Dispatchers.IO) { Database.completeTask(task.id) }; reload() } },
                                onDelete     = { scope.launch { withContext(Dispatchers.IO) { Database.deleteTask(task.id) }; reload() } },
                                onStartFocus = { onStartFocus(task) },
                                onEdit       = { editTask = task },
                                onSkip       = { scope.launch { withContext(Dispatchers.IO) { Database.skipTask(task.id) }; reload() } }
                            )
                        }
                        if (sortedCurrent.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Purple80, modifier = Modifier.size(16.dp))
                                    Text("Upcoming", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                                }
                            }
                        }
                    }
                    items(sortedCurrent, key = { it.id }) { task ->
                        TaskCard(
                            task        = task,
                            onComplete  = { scope.launch { withContext(Dispatchers.IO) { Database.completeTask(task.id) }; reload() } },
                            onDelete    = { scope.launch { withContext(Dispatchers.IO) { Database.deleteTask(task.id) }; reload() } },
                            onStartFocus = { onStartFocus(task) },
                            onEdit      = { editTask = task },
                            onSkip      = { scope.launch { withContext(Dispatchers.IO) { Database.skipTask(task.id) }; reload() } }
                        )
                    }
                    if (completedTasks.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface2)
                                    .clickable { showCompletedSection = !showCompletedSection }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                                    Text("Completed (${completedTasks.size})", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                                }
                                Icon(
                                    if (showCompletedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = OnSurface2, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (showCompletedSection) {
                            items(completedTasks.sortedByDescending { it.scheduledDate }, key = { "done_${it.id}" }) { task ->
                                TaskCard(
                                    task        = task,
                                    onComplete  = { scope.launch { withContext(Dispatchers.IO) { Database.completeTask(task.id) }; reload() } },
                                    onDelete    = { scope.launch { withContext(Dispatchers.IO) { Database.deleteTask(task.id) }; reload() } },
                                    onStartFocus = { onStartFocus(task) },
                                    onEdit      = { editTask = task },
                                    onSkip      = { scope.launch { withContext(Dispatchers.IO) { Database.skipTask(task.id) }; reload() } }
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(tasksListState)
                )
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(onDismiss = { showAdd = false }, onSave = { task ->
            scope.launch { withContext(Dispatchers.IO) { Database.upsertTask(task) }; reload() }
            showAdd = false
        })
    }

    if (editTask != null) {
        EditTaskDialog(
            task      = editTask!!,
            onDismiss = { editTask = null },
            onSave    = { task ->
                scope.launch { withContext(Dispatchers.IO) { Database.upsertTask(task) }; reload() }
                editTask = null
            },
            onDelete  = {
                val id = editTask!!.id
                editTask = null
                scope.launch { withContext(Dispatchers.IO) { Database.deleteTask(id) }; reload() }
            }
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
    var tags          by remember { mutableStateOf("") }
    var focusMode           by remember { mutableStateOf(false) }
    var focusIntensity      by remember { mutableStateOf("standard") }
    var recurring           by remember { mutableStateOf(false) }
    var recurringType       by remember { mutableStateOf("daily") }
    var selectedBlockedApps by remember { mutableStateOf(setOf<String>()) }
    var requirePin          by remember { mutableStateOf(false) }

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
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true,
                    placeholder = { Text("work, urgent, health", color = OnSurface2.copy(alpha = 0.5f)) }
                )
                // ── Focus Mode card ────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focusMode) Purple80.copy(alpha = 0.10f) else Surface3)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Shield, null, tint = if (focusMode) Purple80 else OnSurface2, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Focus Mode", style = MaterialTheme.typography.bodyMedium, color = if (focusMode) Purple80 else OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Block distracting apps during this task", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                            }
                        }
                        Switch(
                            checked = focusMode,
                            onCheckedChange = { focusMode = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple80, checkedTrackColor = Purple80.copy(alpha = 0.4f))
                        )
                    }
                    if (focusMode) {
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Text("Intensity", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("standard", "Standard",  "Block rules apply"),
                                Triple("deep",     "Deep Work", "Always-on blocking"),
                                Triple("nuclear",  "Nuclear",   "Maximum blocking")
                            ).forEach { (key, label, desc) ->
                                val sel = focusIntensity == key
                                val col = when (key) { "deep" -> Warning; "nuclear" -> Error; else -> Purple80 }
                                FilterChip(
                                    selected = sel,
                                    onClick  = { focusIntensity = key },
                                    label = {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                                            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = OnSurface2)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = col.copy(alpha = 0.15f),
                                        selectedLabelColor     = col,
                                        containerColor         = Surface2,
                                        labelColor             = OnSurface2
                                    )
                                )
                            }
                        }
                        // ── App picker ─────────────────────────────────────────
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Block, null, tint = Error.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                            Text("Extra apps to block for this task (session only)", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        }
                        val curatedApps = remember { InstalledAppsScanner.getCuratedApps() }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            curatedApps.chunked(2).forEach { rowApps ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowApps.forEach { app ->
                                        Row(
                                            modifier = Modifier.weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    selectedBlockedApps = if (app.processName in selectedBlockedApps)
                                                        selectedBlockedApps - app.processName
                                                    else selectedBlockedApps + app.processName
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Checkbox(
                                                checked = app.processName in selectedBlockedApps,
                                                onCheckedChange = { checked ->
                                                    selectedBlockedApps = if (checked) selectedBlockedApps + app.processName
                                                    else selectedBlockedApps - app.processName
                                                },
                                                modifier = Modifier.size(16.dp),
                                                colors = CheckboxDefaults.colors(checkedColor = Error)
                                            )
                                            Text(app.displayName, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = OnSurface, maxLines = 1)
                                        }
                                    }
                                    if (rowApps.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (selectedBlockedApps.isNotEmpty()) {
                            Text("${selectedBlockedApps.size} extra app(s) will be blocked this session", style = MaterialTheme.typography.bodySmall, color = Error.copy(alpha = 0.8f))
                        }
                        // ── PIN toggle ─────────────────────────────────────────
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { requirePin = !requirePin }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = requirePin, onCheckedChange = { requirePin = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                            Column {
                                Text("Require PIN to end early", style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Unique PIN shown once at session start — write it down", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = OnSurface2)
                            }
                        }
                    }
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
                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(Task(id = UUID.randomUUID().toString(), title = title.trim(), description = description.trim(), durationMinutes = duration.toIntOrNull() ?: 25, scheduledDate = parsedDate, scheduledTime = time.ifBlank { null }, priority = priority, tags = tagList, focusMode = focusMode, focusIntensity = if (focusMode) focusIntensity else "standard", focusBlockedApps = if (focusMode) selectedBlockedApps.toList() else emptyList(), focusRequirePin = focusMode && requirePin, recurring = recurring, recurringType = if (recurring) recurringType else null, createdAt = LocalDateTime.now()))
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
    var tags          by remember { mutableStateOf(task.tags.joinToString(", ")) }
    var focusMode      by remember { mutableStateOf(task.focusMode) }
    var focusIntensity by remember { mutableStateOf(task.focusIntensity) }
    var recurring      by remember { mutableStateOf(task.recurring) }
    var recurringType  by remember { mutableStateOf(task.recurringType ?: "daily") }
    var showConfirmDelete   by remember { mutableStateOf(false) }
    var selectedBlockedApps by remember { mutableStateOf(task.focusBlockedApps.toSet()) }
    var requirePin          by remember { mutableStateOf(task.focusRequirePin) }

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
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true,
                    placeholder = { Text("work, urgent, health", color = OnSurface2.copy(alpha = 0.5f)) }
                )
                // ── Focus Mode card ────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focusMode) Purple80.copy(alpha = 0.10f) else Surface3)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Shield, null, tint = if (focusMode) Purple80 else OnSurface2, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Focus Mode", style = MaterialTheme.typography.bodyMedium, color = if (focusMode) Purple80 else OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Block distracting apps during this task", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                            }
                        }
                        Switch(
                            checked = focusMode,
                            onCheckedChange = { focusMode = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple80, checkedTrackColor = Purple80.copy(alpha = 0.4f))
                        )
                    }
                    if (focusMode) {
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Text("Intensity", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("standard", "Standard",  "Block rules apply"),
                                Triple("deep",     "Deep Work", "Always-on blocking"),
                                Triple("nuclear",  "Nuclear",   "Maximum blocking")
                            ).forEach { (key, label, desc) ->
                                val sel = focusIntensity == key
                                val col = when (key) { "deep" -> Warning; "nuclear" -> Error; else -> Purple80 }
                                FilterChip(
                                    selected = sel,
                                    onClick  = { focusIntensity = key },
                                    label = {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                                            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = OnSurface2)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = col.copy(alpha = 0.15f),
                                        selectedLabelColor     = col,
                                        containerColor         = Surface2,
                                        labelColor             = OnSurface2
                                    )
                                )
                            }
                        }
                        // ── App picker ─────────────────────────────────────────
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Block, null, tint = Error.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                            Text("Extra apps to block for this task (session only)", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        }
                        val curatedApps = remember { InstalledAppsScanner.getCuratedApps() }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            curatedApps.chunked(2).forEach { rowApps ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowApps.forEach { app ->
                                        Row(
                                            modifier = Modifier.weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    selectedBlockedApps = if (app.processName in selectedBlockedApps)
                                                        selectedBlockedApps - app.processName
                                                    else selectedBlockedApps + app.processName
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Checkbox(
                                                checked = app.processName in selectedBlockedApps,
                                                onCheckedChange = { checked ->
                                                    selectedBlockedApps = if (checked) selectedBlockedApps + app.processName
                                                    else selectedBlockedApps - app.processName
                                                },
                                                modifier = Modifier.size(16.dp),
                                                colors = CheckboxDefaults.colors(checkedColor = Error)
                                            )
                                            Text(app.displayName, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = OnSurface, maxLines = 1)
                                        }
                                    }
                                    if (rowApps.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (selectedBlockedApps.isNotEmpty()) {
                            Text("${selectedBlockedApps.size} extra app(s) will be blocked this session", style = MaterialTheme.typography.bodySmall, color = Error.copy(alpha = 0.8f))
                        }
                        // ── PIN toggle ─────────────────────────────────────────
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { requirePin = !requirePin }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = requirePin, onCheckedChange = { requirePin = it }, colors = CheckboxDefaults.colors(checkedColor = Purple80))
                            Column {
                                Text("Require PIN to end early", style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Unique PIN shown once at session start — write it down", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = OnSurface2)
                            }
                        }
                    }
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
                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(task.copy(title = title.trim(), description = description.trim(), durationMinutes = duration.toIntOrNull() ?: task.durationMinutes, scheduledDate = parsedDate, scheduledTime = time.ifBlank { null }, priority = priority, tags = tagList, focusMode = focusMode, focusIntensity = if (focusMode) focusIntensity else "standard", focusBlockedApps = if (focusMode) selectedBlockedApps.toList() else emptyList(), focusRequirePin = focusMode && requirePin, recurring = recurring, recurringType = if (recurring) recurringType else null))
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
