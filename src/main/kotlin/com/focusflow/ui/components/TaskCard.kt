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
import androidx.compose.ui.unit.dp
import com.focusflow.data.models.Task
import com.focusflow.ui.theme.*

@Composable
fun TaskCard(
    task: Task,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColor = when (task.priority) {
        "high"   -> Error
        "medium" -> Warning
        else     -> Success
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface3)
            .padding(16.dp)
    ) {
        // Priority indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(priorityColor)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.completed) OnSurface2 else OnSurface
            )
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timer, null, tint = OnSurface2, modifier = Modifier.size(12.dp))
                Text("${task.durationMinutes}m", style = MaterialTheme.typography.bodySmall)
                if (task.recurring) {
                    Icon(Icons.Default.Repeat, null, tint = Purple60, modifier = Modifier.size(12.dp))
                    Text(task.recurringType ?: "recurring", style = MaterialTheme.typography.bodySmall, color = Purple60)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!task.completed) {
                IconButton(onClick = onStartFocus, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlayArrow, "Start Focus", tint = Purple80, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onComplete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CheckCircle, "Complete", tint = Success, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = OnSurface2, modifier = Modifier.size(18.dp))
            }
        }
    }
}
