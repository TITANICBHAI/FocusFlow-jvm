package com.focusflow.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.*
import com.focusflow.services.BlockScheduleService
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BlockDefenseScreen() {
    val scope = rememberCoroutineScope()

    var alwaysOnEnabled    by remember { mutableStateOf(false) }
    var soundAversion      by remember { mutableStateOf(false) }
    var overlayMessage     by remember { mutableStateOf("Stay focused. You've got this.") }
    var overlayDraft       by remember { mutableStateOf("") }
    var editingOverlay     by remember { mutableStateOf(false) }
    var blockRules         by remember { mutableStateOf(listOf<BlockRule>()) }
    var schedules          by remember { mutableStateOf(listOf<BlockSchedule>()) }
    var pinSet             by remember { mutableStateOf(false) }
    var showAddSchedule    by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                alwaysOnEnabled = Database.getSetting("always_on_enforcement") == "true"
                soundAversion   = Database.getSetting("sound_aversion") == "true"
                overlayMessage  = Database.getSetting("overlay_message") ?: "Stay focused. You've got this."
                blockRules      = Database.getBlockRules()
                schedules       = Database.getBlockSchedules()
                pinSet          = Database.getSetting("session_pin_hash") != null
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Block Defense", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Text("Configure enforcement layers that make blocking harder to bypass", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

            // System Protection
            DefenseSection(
                icon  = Icons.Default.Shield,
                title = "System Protection",
                color = Purple80
            ) {
                DefenseToggleRow(
                    label    = "Always-On Enforcement",
                    subtitle = "Apps on your block list are killed whenever they launch, even outside focus sessions",
                    checked  = alwaysOnEnabled,
                    onToggle = { v ->
                        scope.launch {
                            withContext(Dispatchers.IO) { Database.setSetting("always_on_enforcement", if (v) "true" else "false") }
                            alwaysOnEnabled = v
                        }
                    }
                )

                Divider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                // PIN lock row
                DefenseInfoRow(
                    label    = "Session PIN Lock",
                    subtitle = if (pinSet) "PIN is set · ending focus requires correct PIN" else "No PIN set · sessions can be ended freely",
                    trailing = {
                        Text(
                            if (pinSet) "Set" else "Not set",
                            color   = if (pinSet) Success else OnSurface2,
                            style   = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )

                Divider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Overlay message
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Block Overlay Message", color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                    Text("Shown when a blocked app is attempted", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    if (editingOverlay) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = overlayDraft,
                                onValueChange = { overlayDraft = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { Database.setSetting("overlay_message", overlayDraft) }
                                        overlayMessage  = overlayDraft
                                        editingOverlay  = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                            ) { Text("Save") }
                            TextButton(onClick = { editingOverlay = false }) { Text("Cancel", color = OnSurface2) }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface3)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\"$overlayMessage\"", color = OnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { overlayDraft = overlayMessage; editingOverlay = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnSurface2, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Aversion Deterrents
            DefenseSection(
                icon  = Icons.Default.VolumeUp,
                title = "Aversion Deterrents",
                color = Warning
            ) {
                DefenseToggleRow(
                    label    = "Sound Aversion",
                    subtitle = "Plays an unpleasant sound when a blocked app is detected — conditions avoidance",
                    checked  = soundAversion,
                    onToggle = { v ->
                        scope.launch {
                            withContext(Dispatchers.IO) { Database.setSetting("sound_aversion", if (v) "true" else "false") }
                            soundAversion = v
                        }
                    }
                )

                Divider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                DefenseInfoRow(
                    label    = "Temptation Log",
                    subtitle = "Every blocked-app attempt is silently logged for review in Stats",
                    trailing = {
                        Text("Always On", color = Success, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                )
            }

            // Always-On Block List summary
            DefenseSection(
                icon  = Icons.Default.Block,
                title = "Always-On Block List",
                color = Error
            ) {
                if (blockRules.isEmpty()) {
                    Text("No apps added yet. Add apps in Block Apps → Always-On App List.", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                } else {
                    blockRules.take(5).forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (rule.enabled) Icons.Default.Block else Icons.Default.RemoveCircleOutline,
                                contentDescription = null,
                                tint = if (rule.enabled) Error else OnSurface2,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(rule.displayName, color = OnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(if (rule.enabled) "Enabled" else "Disabled", color = if (rule.enabled) Success else OnSurface2, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (blockRules.size > 5) {
                        Text("+ ${blockRules.size - 5} more — manage in Block Apps", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Block Schedules
            DefenseSection(
                icon  = Icons.Default.Schedule,
                title = "Block Schedules",
                color = Purple80
            ) {
                if (schedules.isEmpty()) {
                    Text("No schedules configured yet.", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                } else {
                    schedules.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface3)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${s.daysOfWeek.joinToString(",")} · %02d:%02d–%02d:%02d".format(s.startHour, s.startMinute, s.endHour, s.endMinute),
                                    color = OnSurface2, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = s.enabled,
                                onCheckedChange = { v ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Database.upsertBlockSchedule(s.copy(enabled = v))
                                            BlockScheduleService.forceCheck()
                                        }
                                        reload()
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Surface, checkedTrackColor = Purple80),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showAddSchedule = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Purple80)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Schedule")
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }

    if (showAddSchedule) {
        AddScheduleDialogBD(
            onDismiss = { showAddSchedule = false },
            onSave    = { s ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Database.upsertBlockSchedule(s)
                        BlockScheduleService.forceCheck()
                    }
                    reload()
                }
                showAddSchedule = false
            }
        )
    }
}

@Composable
private fun DefenseSection(
    icon: ImageVector,
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(title, color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun DefenseToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Surface, checkedTrackColor = Purple80)
        )
    }
}

@Composable
private fun DefenseInfoRow(
    label: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
private fun AddScheduleDialogBD(onDismiss: () -> Unit, onSave: (BlockSchedule) -> Unit) {
    var name     by remember { mutableStateOf("") }
    var startH   by remember { mutableStateOf("9") }
    var startM   by remember { mutableStateOf("0") }
    var endH     by remember { mutableStateOf("17") }
    var endM     by remember { mutableStateOf("0") }
    val days     = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    val selected = remember { mutableStateListOf("Mon","Tue","Wed","Thu","Fri") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title   = { Text("Add Block Schedule", color = OnSurface) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                Text("Days", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        val sel = selected.contains(d)
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Purple80 else Surface3)
                                .clickable { if (sel) selected.remove(d) else selected.add(d) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(d, color = if (sel) Surface else OnSurface2, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startH, onValueChange = { startH = it.filter(Char::isDigit).take(2) },
                        label = { Text("Start H") }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                    )
                    OutlinedTextField(
                        value = startM, onValueChange = { startM = it.filter(Char::isDigit).take(2) },
                        label = { Text("Start M") }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                    )
                    OutlinedTextField(
                        value = endH, onValueChange = { endH = it.filter(Char::isDigit).take(2) },
                        label = { Text("End H") }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                    )
                    OutlinedTextField(
                        value = endM, onValueChange = { endM = it.filter(Char::isDigit).take(2) },
                        label = { Text("End M") }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && selected.isNotEmpty()) {
                        onSave(BlockSchedule(
                            id          = java.util.UUID.randomUUID().toString(),
                            name        = name,
                            // daysOfWeek is List<Int> (1=Mon…7=Sun); days list is 0-indexed
                            daysOfWeek  = selected.map { days.indexOf(it) + 1 }.sorted(),
                            startHour   = startH.toIntOrNull() ?: 9,
                            startMinute = startM.toIntOrNull() ?: 0,
                            endHour     = endH.toIntOrNull() ?: 17,
                            endMinute   = endM.toIntOrNull() ?: 0,
                            enabled     = true
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}
