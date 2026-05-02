package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.focusflow.data.models.Habit
import com.focusflow.data.models.HabitEntry
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private val EMOJI_OPTIONS = listOf("✅", "💪", "📚", "🏃", "💧", "🧘", "🥗", "😴", "✍️", "🎯", "🚶", "🎵", "🌿", "💊", "🧹")

@Composable
fun HabitsScreen() {
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()

    var habits   by remember { mutableStateOf(listOf<Habit>()) }
    var entries  by remember { mutableStateOf(mapOf<String, List<HabitEntry>>()) }
    var streaks  by remember { mutableStateOf(mapOf<String, Int>()) }
    var showAdd  by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val h = withContext(Dispatchers.IO) { Database.getHabits() }
            val since = today.minusDays(6)
            val e = withContext(Dispatchers.IO) {
                h.associate { habit ->
                    habit.id to Database.getHabitEntries(habit.id, since)
                }
            }
            val s = withContext(Dispatchers.IO) {
                h.associate { habit -> habit.id to Database.getHabitStreak(habit.id) }
            }
            habits  = h
            entries = e
            streaks = s
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Habits", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
                if (habits.isNotEmpty()) {
                    val doneToday = habits.count { h ->
                        entries[h.id]?.any { it.date == today && it.done } == true
                    }
                    Text(
                        "$doneToday / ${habits.size} done today",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (doneToday == habits.size) Success else OnSurface2
                    )
                }
            }
            Button(
                onClick = { showAdd = true },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Habit")
            }
        }

        // Week header
        if (habits.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (6 downTo 0).forEach { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val dayLabel = date.dayOfWeek.name.take(2)
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            dayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (daysAgo == 0) Purple80 else OnSurface2,
                            fontWeight = if (daysAgo == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        if (habits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔄", fontSize = 48.sp)
                    Text("No habits yet", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    Text(
                        "Track daily habits and build streaks.\nSmall consistent actions compound over time.",
                        color = OnSurface2,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) { Text("Add your first habit") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits, key = { it.id }) { habit ->
                    val habitEntries = entries[habit.id] ?: emptyList()
                    val streak       = streaks[habit.id] ?: 0
                    HabitRow(
                        habit   = habit,
                        entries = habitEntries,
                        today   = today,
                        streak  = streak,
                        onToggle = { date, done ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    Database.setHabitEntry(habit.id, date, done)
                                }
                                reload()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { Database.deleteHabit(habit.id) }
                                reload()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddHabitDialog(
            onDismiss = { showAdd = false },
            onSave = { habit ->
                scope.launch {
                    withContext(Dispatchers.IO) { Database.upsertHabit(habit) }
                    reload()
                    showAdd = false
                }
            }
        )
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    entries: List<HabitEntry>,
    today: LocalDate,
    streak: Int,
    onToggle: (LocalDate, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val doneToday = entries.any { it.date == today && it.done }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Emoji + name block (fixed width)
        Row(
            modifier = Modifier.width(160.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(habit.emoji, fontSize = 22.sp)
            Column {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (streak > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("🔥", fontSize = 10.sp)
                        Text(
                            "$streak day streak",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // 7-day grid
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (6 downTo 0).forEach { daysAgo ->
                val date  = today.minusDays(daysAgo.toLong())
                val entry = entries.find { it.date == date }
                val done  = entry?.done == true
                val isToday = daysAgo == 0

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done && isToday -> Purple80
                                done            -> Purple80.copy(alpha = 0.5f)
                                isToday         -> Surface3
                                else            -> Surface3.copy(alpha = 0.5f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isToday) {
                        Checkbox(
                            checked = done,
                            onCheckedChange = { onToggle(date, it) },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor   = androidx.compose.ui.graphics.Color.Transparent,
                                checkmarkColor = androidx.compose.ui.graphics.Color.White,
                                uncheckedColor = OnSurface2
                            )
                        )
                    } else if (done) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, null, tint = OnSurface2.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Surface2,
            title = { Text("Delete Habit?", color = Error) },
            text = { Text("Delete \"${habit.name}\" and all its history?", color = OnSurface2) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = OnSurface2) } }
        )
    }
}

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onSave: (Habit) -> Unit) {
    var name         by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("✅") }
    var nameError    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New Habit", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Habit name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Purple80,
                        unfocusedBorderColor = OnSurface2,
                        errorBorderColor     = Error
                    )
                )
                if (nameError) Text("Please enter a habit name.", color = Error, style = MaterialTheme.typography.bodySmall)

                Text("Pick an emoji", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJI_OPTIONS.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (emoji == selectedEmoji) Purple80.copy(alpha = 0.25f)
                                            else Surface3
                                        )
                                        .clickable { selectedEmoji = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    onSave(Habit(
                        id        = UUID.randomUUID().toString(),
                        name      = name.trim(),
                        emoji     = selectedEmoji,
                        createdAt = LocalDate.now()
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Add Habit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

