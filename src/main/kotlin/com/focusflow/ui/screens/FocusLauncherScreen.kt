package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.services.FocusLauncherApp
import com.focusflow.services.FocusLauncherService
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DURATION_PRESETS = listOf(
    "No limit" to null,
    "30 min"   to 30,
    "1 hour"   to 60,
    "2 hours"  to 120,
    "4 hours"  to 240
)

@Composable
fun FocusLauncherScreen() {

    var selectedApps     by remember { mutableStateOf<Set<String>>(emptySet()) }
    var availableApps    by remember { mutableStateOf<List<FocusLauncherApp>>(emptyList()) }
    var searchQuery      by remember { mutableStateOf("") }
    var searchResults    by remember { mutableStateOf<List<FocusLauncherApp>>(emptyList()) }
    var durationIndex    by remember { mutableStateOf(0) }
    var isLoading        by remember { mutableStateOf(true) }
    var confirmEnter     by remember { mutableStateOf(false) }

    val isActive  by FocusLauncherService.isActive.collectAsState()
    val canBreak  by FocusLauncherService.canTakeBreak.collectAsState()
    val scope     = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            val fromRules = Database.getBlockRules().map { rule ->
                FocusLauncherApp(
                    processName = rule.processName,
                    displayName = rule.displayName,
                    exePath     = InstalledAppsScanner.getExePathFor(rule.processName)
                )
            }
            val fromAllowances = Database.getDailyAllowances().map { da ->
                FocusLauncherApp(
                    processName = da.processName,
                    displayName = da.displayName,
                    exePath     = InstalledAppsScanner.getExePathFor(da.processName)
                )
            }
            (fromRules + fromAllowances)
                .distinctBy { it.processName.lowercase() }
                .sortedBy { it.displayName }
        }
        availableApps = apps

        // Load persisted selection; fall back to all-selected if none saved yet
        val persisted = Database.getSetting("launcher_selected_apps")
        selectedApps = if (persisted != null && persisted.isNotBlank()) {
            val saved     = persisted.split(",").filter { it.isNotBlank() }.toSet()
            val available = apps.map { it.processName.lowercase() }.toSet()
            val matching  = available.intersect(saved)
            if (matching.isEmpty()) available else matching
        } else {
            apps.map { it.processName.lowercase() }.toSet()
        }

        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        val q = searchQuery.lowercase()
        searchResults = withContext(Dispatchers.IO) {
            InstalledAppsScanner.getCuratedApps()
                .filter {
                    it.displayName.lowercase().contains(q) ||
                    it.processName.lowercase().contains(q)
                }
                .filter { result ->
                    availableApps.none { it.processName.equals(result.processName, ignoreCase = true) }
                }
                .take(8)
                .map { FocusLauncherApp(it.processName, it.displayName, it.exePath) }
        }
    }

    if (isActive) {
        ActiveLauncherBanner()
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier         = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Purple80.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GridView, null, tint = Purple80, modifier = Modifier.size(26.dp))
                }
                Column {
                    Text("Focus Launcher", style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("CBT-style kiosk mode — only your chosen apps, nothing else.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }

        // ── Warning banner ───────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Warning.copy(alpha = 0.08f))
                    .border(1.dp, Warning.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Full OS lockdown", color = Warning, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Taskbar hidden, keyboard shortcuts disabled, all non-selected apps killed. " +
                        (if (canBreak) "One 5-minute break available today. " else "Break already used today. ") +
                        "Requires PIN to exit if hard-locked.",
                        color = OnSurface2, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ── App selection ─────────────────────────────────────────────────────
        item {
            Text("Apps to include", color = OnSurface, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("Pulled from your FocusFlow lists. Uncheck any you don't want this session.",
                color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Purple80, modifier = Modifier.size(28.dp))
                }
            }
        } else if (availableApps.isEmpty()) {
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Surface3).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                    Text("No apps in your FocusFlow lists yet. Use the search below to add apps.",
                        color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(availableApps) { app ->
                val key      = app.processName.lowercase()
                val checked  = key in selectedApps
                AppSelectRow(
                    app     = app,
                    checked = checked,
                    onToggle = {
                        selectedApps = if (checked) selectedApps - key else selectedApps + key
                    }
                )
            }
        }

        // ── Search & add ──────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Text("Add more apps", color = OnSurface, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value          = searchQuery,
                onValueChange  = { searchQuery = it },
                placeholder    = { Text("Search installed apps…", color = OnSurface2) },
                leadingIcon    = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                trailingIcon   = if (searchQuery.isNotEmpty()) {{
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                    }
                }} else null,
                singleLine     = true,
                colors         = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Purple80,
                    unfocusedBorderColor = Surface3
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (searchResults.isNotEmpty()) {
            items(searchResults) { app ->
                val key     = app.processName.lowercase()
                val added   = availableApps.any { it.processName.equals(app.processName, ignoreCase = true) }
                val checked = key in selectedApps
                Row(
                    modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Surface3).padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(app.displayName, color = OnSurface,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(app.processName, color = OnSurface2,
                            style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }
                    if (!added) {
                        IconButton(
                            onClick = {
                                availableApps = availableApps + app
                                selectedApps  = selectedApps + key
                                searchQuery   = ""
                            },
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Purple80.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Add, null, tint = Purple80, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Checkbox(
                            checked  = checked,
                            onCheckedChange = {
                                selectedApps = if (checked) selectedApps - key else selectedApps + key
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Purple80)
                        )
                    }
                }
            }
        }

        // ── Duration ─────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Text("Session duration", color = OnSurface, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DURATION_PRESETS.forEachIndexed { idx, (label, _) ->
                    val selected = durationIndex == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Purple80 else Surface3)
                            .border(1.dp, if (selected) Purple80 else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { durationIndex = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            color      = if (selected) Color.White else OnSurface2,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        // ── Enter button ──────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            val appsForSession = availableApps.filter {
                it.processName.lowercase() in selectedApps
            }
            Button(
                onClick  = { confirmEnter = true },
                enabled  = appsForSession.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (appsForSession.isEmpty()) "Select at least one app"
                    else "Enter Focus Launcher with ${appsForSession.size} app${if (appsForSession.size == 1) "" else "s"}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Confirm dialog ────────────────────────────────────────────────────────
    if (confirmEnter) {
        val appsForSession = availableApps.filter { it.processName.lowercase() in selectedApps }
        val duration       = DURATION_PRESETS[durationIndex].second

        AlertDialog(
            onDismissRequest = { confirmEnter = false },
            containerColor   = Surface2,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text("Enter Focus Launcher?", color = OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will hide the taskbar, disable system shortcuts, and restrict you " +
                        "to ${appsForSession.size} app${if (appsForSession.size == 1) "" else "s"}" +
                        if (duration != null) " for $duration minutes." else ". No time limit.",
                        color = OnSurface2, style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "You get one 5-minute break per day. Exit requires your GlobalPin if hard-locked.",
                        color = Warning, style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEnter = false
                        val saved = appsForSession.joinToString(",") { it.processName.lowercase() }
                        scope.launch(Dispatchers.IO) {
                            Database.setSetting("launcher_selected_apps", saved)
                        }
                        FocusLauncherService.enter(appsForSession, duration)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) { Text("Enter Launcher") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnter = false }) {
                    Text("Cancel", color = OnSurface2)
                }
            }
        )
    }
}

@Composable
private fun AppSelectRow(app: FocusLauncherApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (checked) Purple80.copy(alpha = 0.07f) else Surface3)
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.displayName, color = OnSurface,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(app.processName, color = OnSurface2,
                style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
        Checkbox(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = CheckboxDefaults.colors(checkedColor = Purple80)
        )
    }
}

@Composable
private fun ActiveLauncherBanner() {
    val sessionApps  by FocusLauncherService.sessionApps.collectAsState()
    val sessionEndMs by FocusLauncherService.sessionEndMs.collectAsState()
    var remaining    by remember { mutableStateOf<Long>(-1L) }

    LaunchedEffect(sessionEndMs) {
        while (true) {
            remaining = FocusLauncherService.remainingSeconds()
            kotlinx.coroutines.delay(1_000)
        }
    }

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            Box(
                modifier         = Modifier.size(64.dp).clip(CircleShape)
                    .background(Purple80.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = Purple80, modifier = Modifier.size(32.dp))
            }

            Text("Focus Launcher is active", color = OnSurface,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (sessionApps.isNotEmpty()) {
                Text(
                    "${sessionApps.size} app${if (sessionApps.size == 1) "" else "s"} in session",
                    color = OnSurface2, style = MaterialTheme.typography.bodyMedium
                )
            }

            if (remaining >= 0L) {
                val mins = remaining / 60
                val secs = remaining % 60
                Text(
                    if (remaining > 0L) "%02d:%02d remaining".format(mins, secs) else "Session ending…",
                    color      = if (remaining in 1..299L) Warning else Purple80,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text("No time limit", color = OnSurface2, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Switch to the launcher overlay to manage your session.",
                color     = OnSurface2,
                style     = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
