package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.focusflow.data.models.DailyNote
import com.focusflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val moodLabels = listOf("", "😔 Rough", "😕 Low", "😐 Okay", "🙂 Good", "😄 Great")
private val moodColors = listOf(
    Surface3, Error.copy(alpha = 0.7f), Warning.copy(alpha = 0.7f),
    OnSurface2, Success.copy(alpha = 0.7f), Purple80
)

@Composable
fun DailyNotesScreen() {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    var selectedDate by remember { mutableStateOf(today) }
    var note     by remember { mutableStateOf<DailyNote?>(null) }
    var content  by remember { mutableStateOf("") }
    var mood     by remember { mutableStateOf(3) }
    var saved    by remember { mutableStateOf(false) }
    var pastNotes by remember { mutableStateOf(listOf<Pair<LocalDate, DailyNote>>()) }

    LaunchedEffect(selectedDate) {
        val loaded = withContext(Dispatchers.IO) { Database.getNote(selectedDate) }
        note    = loaded
        content = loaded?.content ?: ""
        mood    = loaded?.mood ?: 3
        saved   = false

        pastNotes = withContext(Dispatchers.IO) {
            (6 downTo 0).mapNotNull { d ->
                val date = today.minusDays(d.toLong())
                Database.getNote(date)?.let { Pair(date, it) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Daily Notes", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface2
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                    Icon(Icons.Default.ChevronLeft, "Previous", tint = OnSurface2)
                }
                if (selectedDate.isBefore(today)) {
                    IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                        Icon(Icons.Default.ChevronRight, "Next", tint = OnSurface2)
                    }
                }
                if (selectedDate != today) {
                    TextButton(onClick = { selectedDate = today }) {
                        Text("Today", color = Purple80)
                    }
                }
            }
        }

        // Mood selector
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("How are you feeling?", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (1..5).forEach { m ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mood == m) moodColors[m].copy(alpha = 0.2f) else Surface3)
                            .clickable { mood = m; saved = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(moodLabels[m].take(2), fontSize = 24.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            moodLabels[m].drop(3),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mood == m) moodColors[m] else OnSurface2,
                            fontWeight = if (mood == m) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Notes editor
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Notes", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; saved = false },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("Reflect on your day, wins, challenges, what you learned…", color = OnSurface2) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Purple80,
                    unfocusedBorderColor = Surface3
                ),
                minLines = 6
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (saved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Saved", color = Success, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(
                    onClick = {
                        val capturedDate    = selectedDate
                        val capturedContent = content
                        val capturedMood    = mood
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                Database.upsertNote(DailyNote(date = capturedDate, content = capturedContent, mood = capturedMood))
                            }
                            saved = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Note")
                }
            }
        }

        // Past 7 days mood strip
        if (pastNotes.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Past 7 Days", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pastNotes.forEach { (date, n) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (date == selectedDate) Purple80.copy(alpha = 0.15f) else Surface3
                                )
                                .clickable { selectedDate = date }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(moodLabels[n.mood].take(2), fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE")),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (date == selectedDate) Purple80 else OnSurface2
                            )
                        }
                    }
                }
            }
        }
    }
}
