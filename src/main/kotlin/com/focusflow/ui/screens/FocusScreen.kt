package com.focusflow.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.SessionState
import com.focusflow.data.models.Task
import com.focusflow.services.FocusSessionService
import com.focusflow.services.TemptationLogger
import com.focusflow.ui.theme.*
import kotlin.math.min

@Composable
fun FocusScreen(preloadTask: Task? = null) {
    val sessionState by FocusSessionService.state.collectAsState()
    var customTaskName by remember { mutableStateOf(preloadTask?.title ?: "") }
    var customMinutes by remember { mutableStateOf((preloadTask?.durationMinutes ?: 25).toString()) }
    var recentTasks by remember { mutableStateOf(listOf<Task>()) }
    var attempts by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        recentTasks = Database.getTasks().filter { !it.completed }.take(10)
    }

    LaunchedEffect(preloadTask) {
        preloadTask?.let {
            customTaskName = it.title
            customMinutes = it.durationMinutes.toString()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineLarge, color = OnSurface)

        if (!sessionState.isActive) {
            // Setup panel
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface2)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Configure Session", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

                OutlinedTextField(
                    value = customTaskName,
                    onValueChange = { customTaskName = it },
                    label = { Text("What are you working on?") },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )

                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Duration (minutes)") },
                    modifier = Modifier.widthIn(min = 120.dp, max = 200.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )

                // Quick durations
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        FilterChip(
                            selected = customMinutes == m.toString(),
                            onClick = { customMinutes = m.toString() },
                            label = { Text("${m}m") }
                        )
                    }
                }

                val blockedCount = Database.getBlockRules().count { it.enabled }
                if (blockedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple80.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$blockedCount app${if (blockedCount == 1) "" else "s"} will be blocked during this session",
                            style = MaterialTheme.typography.bodySmall,
                            color = Purple80
                        )
                    }
                }

                Button(
                    onClick = {
                        val mins = customMinutes.toIntOrNull() ?: 25
                        val name = customTaskName.ifBlank { "Focus Session" }
                        FocusSessionService.start(name, mins)
                        TemptationLogger.clearSession()
                        attempts = 0
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Focus", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            // Active session panel
            val progress = if (sessionState.totalSeconds > 0)
                sessionState.elapsedSeconds.toFloat() / sessionState.totalSeconds
            else 0f

            val remaining = sessionState.totalSeconds - sessionState.elapsedSeconds
            val mins = remaining / 60
            val secs = remaining % 60

            // Timer ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2
                    val center = Offset(size.width / 2, size.height / 2)

                    // Track
                    drawArc(
                        color = Surface3,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // Progress
                    drawArc(
                        color = Purple80,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%02d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp),
                        color = if (sessionState.isPaused) OnSurface2 else OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(sessionState.taskName, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                }
            }

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (sessionState.isPaused) {
                    Button(
                        onClick = { FocusSessionService.resume() },
                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(onClick = { FocusSessionService.pause() }) {
                        Icon(Icons.Default.Pause, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }
                }
                Button(
                    onClick = { FocusSessionService.end(completed = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("End")
                }
            }

            // Attempt counter — re-read on every recompose triggered by sessionState changes
            val count = TemptationLogger.getSessionAttempts()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (count == 0) "No blocked app attempts this session"
                    else "$count app attempt${if (count == 1) "" else "s"} blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (count == 0) Success else Warning
                )
            }
        }
    }
}
