package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.focusflow.data.models.Task
import com.focusflow.ui.theme.*

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
    val priorityColor = when (task.priority) {
        "high"   -> Error
        "medium" -> Warning
        else     -> Success
    }

    val isDone = task.completed || task.skipped

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDone) Surface3.copy(alpha = 0.5f) else Surface3)
            .padding(16.dp)
    ) {
        // Priority bar
        Box(modifier = Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(if (isDone) OnSurface2.copy(alpha = 0.3f) else priorityColor))

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
                if (task.skipped) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Warning.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("skipped", style = MaterialTheme.typography.bodySmall, color = Warning)
                    }
                }
                if (task.focusMode && !isDone) {
                    Spacer(Modifier.width(8.dp))
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
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = OnSurface2, modifier = Modifier.size(18.dp))
            }
        }
    }
}
