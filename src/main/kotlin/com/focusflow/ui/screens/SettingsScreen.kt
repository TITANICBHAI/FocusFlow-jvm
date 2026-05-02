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
import com.focusflow.data.models.BlockRule
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.isWindows
import com.focusflow.services.SessionPin
import com.focusflow.ui.theme.*
import java.util.UUID

@Composable
fun SettingsScreen() {
    var blockRules by remember { mutableStateOf(listOf<BlockRule>()) }
    var alwaysOn by remember { mutableStateOf(false) }
    var overlayMessage by remember { mutableStateOf("Stay focused. You've got this.") }
    var pinSet by remember { mutableStateOf(false) }
    var showAddRule by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    fun reload() {
        blockRules = Database.getBlockRules()
        alwaysOn = Database.getSetting("always_on_enforcement") == "true"
        overlayMessage = Database.getSetting("overlay_message") ?: "Stay focused. You've got this."
        pinSet = SessionPin.isSet()
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
        }

        // Enforcement status
        item {
            SectionCard(title = "Enforcement Status") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Process Monitor", color = OnSurface)
                        Text(
                            if (isWindows) "Active — polls foreground window every 500ms"
                            else "Inactive — only enforced on Windows",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWindows) Success else Warning
                        )
                    }
                    Icon(
                        if (isWindows) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = if (isWindows) Success else Warning
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Always-On Enforcement", color = OnSurface)
                        Text("Block apps even outside focus sessions", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = alwaysOn,
                        onCheckedChange = {
                            alwaysOn = it
                            Database.setSetting("always_on_enforcement", it.toString())
                            ProcessMonitor.alwaysOnEnabled = it
                            if (it) ProcessMonitor.start()
                        }
                    )
                }
            }
        }

        // Blocked apps
        item {
            SectionCard(title = "Blocked Apps (${blockRules.size})") {
                Text(
                    "These apps will be killed immediately when detected during a session.\nEnter the process name exactly as it appears in Task Manager (e.g. chrome.exe).",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )
                Spacer(Modifier.height(12.dp))

                if (blockRules.isEmpty()) {
                    Text("No apps blocked yet.", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        blockRules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Surface3)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.displayName, color = OnSurface)
                                    Text(rule.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (rule.blockNetwork) {
                                        Icon(Icons.Default.WifiOff, null, tint = Warning, modifier = Modifier.size(16.dp))
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = {
                                            Database.upsertBlockRule(rule.copy(enabled = it))
                                            if (!it) NetworkBlocker.removeRule(rule.processName)
                                            reload()
                                        },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    IconButton(onClick = {
                                        Database.deleteBlockRule(rule.id)
                                        NetworkBlocker.removeRule(rule.processName)
                                        reload()
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showAddRule = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add App to Block")
                }
            }
        }

        // Overlay message
        item {
            SectionCard(title = "Block Overlay") {
                Text("Message shown on the overlay when a blocked app is detected:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = overlayMessage,
                    onValueChange = {
                        overlayMessage = it
                        Database.setSetting("overlay_message", it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )
            }
        }

        // Session PIN
        item {
            SectionCard(title = "Session PIN") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PIN Lock", color = OnSurface)
                        Text(
                            if (pinSet) "PIN set — required to end an active session"
                            else "No PIN — anyone can end a session",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pinSet) Success else Warning
                        )
                    }
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pinSet) Error.copy(alpha = 0.7f) else Purple80
                        )
                    ) {
                        Text(if (pinSet) "Remove PIN" else "Set PIN")
                    }
                }
            }
        }

        // About
        item {
            SectionCard(title = "About") {
                Text("FocusFlow JVM v1.0.0", color = OnSurface)
                Text("Kotlin + Compose Multiplatform Desktop", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(4.dp))
                Text("Enforcement: JNA Win32 process monitor + Windows Firewall", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Text("Database: SQLite at ~/focusflow/focusflow.db", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }
    }

    if (showAddRule) {
        AddRuleDialog(
            onDismiss = { showAddRule = false },
            onSave = { rule -> Database.upsertBlockRule(rule); reload(); showAddRule = false }
        )
    }

    if (showPinDialog) {
        PinDialog(
            pinAlreadySet = pinSet,
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                if (pinSet) SessionPin.clear(pin) else SessionPin.set(pin)
                reload()
                showPinDialog = false
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Surface2).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onSave: (BlockRule) -> Unit) {
    var processName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var blockNetwork by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Block an App", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = processName,
                    onValueChange = { processName = it },
                    label = { Text("Process name (e.g. chrome.exe)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name (e.g. Google Chrome)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = blockNetwork, onCheckedChange = { blockNetwork = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Also block network access", color = OnSurface)
                        Text("Adds a Windows Firewall rule (requires admin)", style = MaterialTheme.typography.bodySmall, color = Warning)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (processName.isBlank()) return@Button
                    val name = if (processName.endsWith(".exe")) processName else "$processName.exe"
                    onSave(BlockRule(UUID.randomUUID().toString(), name.lowercase(), displayName.ifBlank { name }, true, blockNetwork))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
private fun PinDialog(pinAlreadySet: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(if (pinAlreadySet) "Remove PIN" else "Set Session PIN", color = OnSurface) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text(if (pinAlreadySet) "Current PIN" else "New PIN") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (pin.isNotBlank()) onSave(pin) },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}
