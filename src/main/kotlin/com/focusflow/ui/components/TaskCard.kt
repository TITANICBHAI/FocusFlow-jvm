package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.Task
import com.focusflow.ui.theme.*
import java.time.LocalDate

@Composable
fun TaskCard(
    task: Task,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        "high"   -> Error
        "medium" -> Warning
        else     -> Success
    }

    val isDone   = task.completed || task.skipped
    val isOverdue = !isDone && task.scheduledDate != null &&
                    task.scheduledDate.isBefore(LocalDate.now())

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDone) Surface3.copy(alpha = 0.5f) else Surface3)
            .padding(16.dp)
    ) {
        // Priority / overdue bar
        Box(modifier = Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp))
            .background(when {
                isDone    -> OnSurface2.copy(alpha = 0.3f)
                isOverdue -> Error
                else      -> priorityColor
            }))

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone) OnSurface2 else OnSurface
                )
                if (isOverdue) {
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Error.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("overdue", style = MaterialTheme.typography.bodySmall,
                            color = Error, fontSize = 9.sp)
                    }
                }
                if (task.skipped) {
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Warning.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("skipped", style = MaterialTheme.typography.bodySmall, color = Warning, fontSize = 9.sp)
                    }
                }
                if (task.focusMode && !isDone) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(13.dp))
                }
            }
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(task.description, style = MaterialTheme.typography.bodySmall, color = OnSurface2, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = OnSurface2, modifier = Modifier.size(12.dp))
                Text("${task.durationMinutes}m", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                if (task.scheduledTime != null) {
                    Text("·", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    Text(task.scheduledTime, style = MaterialTheme.typography.bodySmall, color = Purple60)
                }
                if (task.recurring) {
                    Icon(Icons.Default.Repeat, null, tint = Purple60, modifier = Modifier.size(12.dp))
                    Text(task.recurringType ?: "recurring", style = MaterialTheme.typography.bodySmall, color = Purple60)
                }
            }
            // Tags
            if (task.tags.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    task.tags.take(4).forEach { tag ->
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Purple80.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("#$tag", style = MaterialTheme.typography.bodySmall,
                                color = Purple60, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!isDone) {
                IconButton(onClick = onStartFocus, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlayArrow, "Start Focus", tint = Purple80, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onComplete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CheckCircle, "Complete", tint = Success, modifier = Modifier.size(18.dp))
                }
                if (onSkip != null) {
                    IconButton(onClick = onSkip, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipNext, "Skip", tint = Warning, modifier = Modifier.size(18.dp))
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = OnSurface2, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.SkipNext, null, tint = if (task.completed) Success else Warning, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = OnSurface2, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = Surface2,
            title = { Text("Delete Task?", color = Error) },
            text  = { Text("\"${task.title}\" will be permanently deleted.", color = OnSurface2) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = OnSurface2) }
            }
        )
    }
}
