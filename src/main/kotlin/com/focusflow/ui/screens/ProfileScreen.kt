package com.focusflow.ui.screens

import androidx.compose.foundation.background
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
import com.focusflow.services.BackupService
import com.focusflow.ui.theme.*

@Composable
fun ProfileScreen() {
    var userName      by remember { mutableStateOf("") }
    var dailyGoal     by remember { mutableStateOf(120) }
    var saved         by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var exportMsg     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        userName  = Database.getSetting("user_name")          ?: ""
        dailyGoal = Database.getSetting("daily_focus_goal")?.toIntOrNull() ?: 120
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Surface).verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Profile & Data", style = MaterialTheme.typography.headlineLarge, color = OnSurface)

        // Avatar + name
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Purple80.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(if (userName.isNotBlank()) userName.first().uppercaseChar().toString() else "?", fontSize = 32.sp, color = Purple80, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = userName, onValueChange = { userName = it; saved = false },
                label = { Text("Your name") }, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
            )
        }

        // Daily goal
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Daily Focus Goal", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text("Minimum focus minutes per day to maintain your streak and hit your goal.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = dailyGoal.toFloat(),
                    onValueChange = { dailyGoal = it.toInt(); saved = false },
                    valueRange = 15f..480f,
                    steps = 30,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Purple80, activeTrackColor = Purple80)
                )
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Purple80.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val h = dailyGoal / 60; val m = dailyGoal % 60
                    Text(if (h > 0) "${h}h ${m}m" else "${m}m", color = Purple80, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30m", 60 to "1h", 90 to "90m", 120 to "2h", 180 to "3h", 240 to "4h").forEach { (g, label) ->
                    FilterChip(selected = dailyGoal == g, onClick = { dailyGoal = g; saved = false }, label = { Text(label) })
                }
            }
        }

        // Save button
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    Database.setSetting("user_name",        userName.trim())
                    Database.setSetting("daily_focus_goal", dailyGoal.toString())
                    saved = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Profile")
            }
            if (saved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Saved", color = Success, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Data export
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export Data", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text("Export your sessions or tasks as CSV files for analysis.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val path = BackupService.exportToCsv()
                    exportMsg = if (path != null) "Sessions saved to $path" else "Export cancelled"
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export Sessions")
                }
                OutlinedButton(onClick = {
                    val path = BackupService.exportTasksToCsv()
                    exportMsg = if (path != null) "Tasks saved to $path" else "Export cancelled"
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export Tasks")
                }
            }
            if (exportMsg.isNotBlank()) {
                Text(exportMsg, style = MaterialTheme.typography.bodySmall, color = Success)
            }
        }

        // Stats summary
        val allTimeMins  = remember { Database.getAllTimeFocusMinutes() }
        val allTimeSess  = remember { Database.getAllTimeFocusSessions() }
        val bestStreak   = remember { Database.getBestStreak() }
        val totalTasks   = remember { Database.getTasks().count { it.completed } }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("All-Time Summary", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            val h = allTimeMins / 60; val m = allTimeMins % 60
            ProfileStatRow("⏱", "Total focus time", if (h > 0) "${h}h ${m}m" else "${m}m")
            ProfileStatRow("🎯", "Total sessions",   "$allTimeSess sessions")
            ProfileStatRow("✅", "Tasks completed",  "$totalTasks tasks")
            ProfileStatRow("🔥", "Best streak",      "$bestStreak days")
        }

        // Danger zone
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Error.copy(alpha = 0.06f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Danger Zone", style = MaterialTheme.typography.headlineSmall, color = Error)
            Text("Permanently delete all stored data. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear All Data")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Clear All Data?", color = Error) },
            text = { Text("This will delete all sessions, tasks, notes, and temptation logs permanently. Your settings will be preserved.", color = OnSurface2) },
            confirmButton = {
                Button(
                    onClick = { BackupService.clearAllData(); showClearDialog = false; exportMsg = "All data cleared." },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = OnSurface2) } }
        )
    }
}

@Composable
private fun ProfileStatRow(emoji: String, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, color = Purple80, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
