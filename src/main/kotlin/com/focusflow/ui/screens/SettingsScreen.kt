package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.focusflow.data.models.BlockSchedule
import com.focusflow.data.models.DailyAllowance
import com.focusflow.enforcement.*
import com.focusflow.services.BlockScheduleService
import com.focusflow.services.BreakEnforcer
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.services.SessionPin
import com.focusflow.services.SoundAversion
import com.focusflow.services.TaskAlarmService
import com.focusflow.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()

    var blockRules       by remember { mutableStateOf(listOf<BlockRule>()) }
    var blockSchedules   by remember { mutableStateOf(listOf<BlockSchedule>()) }
    var dailyAllowances  by remember { mutableStateOf(listOf<DailyAllowance>()) }
    var showAddSchedule  by remember { mutableStateOf(false) }
    var showAddAllowance by remember { mutableStateOf(false) }
    var alwaysOn         by remember { mutableStateOf(false) }
    var startWithWin     by remember { mutableStateOf(false) }
    var soundEnabled     by remember { mutableStateOf(true) }
    var overlayMessage   by remember { mutableStateOf("Stay focused. You've got this.") }
    var pinSet           by remember { mutableStateOf(false) }
    var showAddRule      by remember { mutableStateOf(false) }
    var showPinDialog    by remember { mutableStateOf(false) }
    var hookActive       by remember { mutableStateOf(false) }
    var nuclearActive    by remember { mutableStateOf(false) }

    // Pomodoro
    var pomodoroWork   by remember { mutableStateOf("25") }
    var pomodoroShort  by remember { mutableStateOf("5") }
    var pomodoroLong   by remember { mutableStateOf("15") }
    var pomodoroCycles by remember { mutableStateOf("4") }
    var pomodoroSaved  by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val rules      = withContext(Dispatchers.IO) { Database.getBlockRules() }
            val schedules  = withContext(Dispatchers.IO) { Database.getBlockSchedules() }
            val allowances = withContext(Dispatchers.IO) { Database.getDailyAllowances() }
            val ao         = withContext(Dispatchers.IO) { Database.getSetting("always_on_enforcement") == "true" }
            val sound      = withContext(Dispatchers.IO) { Database.getSetting("sound_aversion") != "false" }
            val overlay    = withContext(Dispatchers.IO) { Database.getSetting("overlay_message") ?: "Stay focused. You've got this." }
            val pinIsSet   = withContext(Dispatchers.IO) { SessionPin.isSet() }
            val sww        = withContext(Dispatchers.IO) { WindowsStartupManager.isEnabled() }
            val pw         = withContext(Dispatchers.IO) { Database.getSetting("pomodoro_work")   ?: "25" }
            val ps         = withContext(Dispatchers.IO) { Database.getSetting("pomodoro_short")  ?: "5" }
            val pl         = withContext(Dispatchers.IO) { Database.getSetting("pomodoro_long")   ?: "15" }
            val pc         = withContext(Dispatchers.IO) { Database.getSetting("pomodoro_cycles") ?: "4" }
            blockRules      = rules
            blockSchedules  = schedules
            dailyAllowances = allowances
            alwaysOn        = ao
            startWithWin    = sww
            soundEnabled    = sound
            overlayMessage  = overlay
            pinSet          = pinIsSet
            hookActive      = WinEventHook.isActive
            nuclearActive   = NuclearMode.isActive
            pomodoroWork    = pw
            pomodoroShort   = ps
            pomodoroLong    = pl
            pomodoroCycles  = pc
        }
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
        }

        // ── Enforcement ───────────────────────────────────────────────────────
        item {
            SectionCard(title = "Enforcement") {
                SettingRow(
                    label = "Process Monitor",
                    subtitle = if (isWindows) "Active — 500ms polling + instant WinEventHook"
                               else "Inactive — only enforced on Windows",
                    trailing = {
                        Icon(
                            if (isWindows) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (isWindows) Success else Warning
                        )
                    }
                )

                Divider(color = Surface3, modifier = Modifier.padding(vertical = 8.dp))

                SettingRow(
                    label    = "Instant Detection",
                    subtitle = if (hookActive)
                                   "WinEventHook active — zero-delay foreground detection"
                               else "WinEventHook inactive — polling fallback only",
                    trailing = {
                        Icon(
                            if (hookActive) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            null,
                            tint = if (hookActive) Success else OnSurface2
                        )
                    }
                )

                Divider(color = Surface3, modifier = Modifier.padding(vertical = 8.dp))

                SettingRow(
                    label    = "Always-On Enforcement",
                    subtitle = "Block apps even outside focus sessions",
                    trailing = {
                        Switch(
                            checked = alwaysOn,
                            onCheckedChange = { enabled ->
                                alwaysOn = enabled
                                ProcessMonitor.alwaysOnEnabled = enabled
                                if (enabled) ProcessMonitor.start()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        Database.setSetting("always_on_enforcement", enabled.toString())
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }

        // ── Nuclear Mode ──────────────────────────────────────────────────────
        item {
            SectionCard(title = "Nuclear Mode") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (nuclearActive) Error.copy(alpha = 0.1f) else Surface3)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        tint = if (nuclearActive) Error else OnSurface2,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (nuclearActive) "Nuclear Mode ACTIVE" else "Nuclear Mode",
                            color = if (nuclearActive) Error else OnSurface
                        )
                        Text(
                            "Kills Task Manager, regedit, cmd, PowerShell, Process Explorer when detected — no escape",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface2
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = nuclearActive,
                        onCheckedChange = { enabled ->
                            if (enabled) NuclearMode.enable() else NuclearMode.disable()
                            nuclearActive = NuclearMode.isActive
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Error,
                            checkedTrackColor = Error.copy(alpha = 0.3f)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ Nuclear Mode kills system utilities every 300ms. Use with caution — you must toggle it off inside FocusFlow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
            }
        }

        // ── Pomodoro Settings ─────────────────────────────────────────────────
        item {
            SectionCard(title = "Pomodoro Timer") {
                Text(
                    "Configure session and break durations for Pomodoro mode in the Focus screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PomodoroField(
                        label = "Work (min)",
                        value = pomodoroWork,
                        onValueChange = { pomodoroWork = it; pomodoroSaved = false },
                        modifier = Modifier.weight(1f)
                    )
                    PomodoroField(
                        label = "Short Break",
                        value = pomodoroShort,
                        onValueChange = { pomodoroShort = it; pomodoroSaved = false },
                        modifier = Modifier.weight(1f)
                    )
                    PomodoroField(
                        label = "Long Break",
                        value = pomodoroLong,
                        onValueChange = { pomodoroLong = it; pomodoroSaved = false },
                        modifier = Modifier.weight(1f)
                    )
                    PomodoroField(
                        label = "Before Long",
                        value = pomodoroCycles,
                        onValueChange = { pomodoroCycles = it; pomodoroSaved = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val w = pomodoroWork.toIntOrNull()?.coerceIn(1, 120)   ?: 25
                            val s = pomodoroShort.toIntOrNull()?.coerceIn(1, 60)   ?: 5
                            val l = pomodoroLong.toIntOrNull()?.coerceIn(1, 60)    ?: 15
                            val c = pomodoroCycles.toIntOrNull()?.coerceIn(1, 10)  ?: 4
                            scope.launch {
                                withContext(Dispatchers.IO) { BreakEnforcer.saveSettings(w, s, l, c) }
                                pomodoroSaved = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    if (pomodoroSaved) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Saved", color = Success, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ── Startup ───────────────────────────────────────────────────────────
        item {
            SectionCard(title = "Startup") {
                SettingRow(
                    label    = "Start with Windows",
                    subtitle = if (!isWindows) "Only available on Windows"
                               else if (startWithWin) "FocusFlow launches at login (HKCU\\Run)"
                               else "FocusFlow does not start automatically",
                    trailing = {
                        Switch(
                            checked  = startWithWin,
                            enabled  = isWindows,
                            onCheckedChange = { enabled ->
                                startWithWin = enabled
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (enabled) WindowsStartupManager.enable()
                                        else         WindowsStartupManager.disable()
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }

        // ── Sound Notifications ───────────────────────────────────────────────
        item {
            SectionCard(title = "Sound Notifications") {
                SettingRow(
                    label    = "Aversion Tones",
                    subtitle = "Harsh tone when blocked app killed; chime on session start/end/break",
                    trailing = {
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { enabled ->
                                soundEnabled = enabled
                                SoundAversion.isEnabled = enabled
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        Database.setSetting("sound_aversion", enabled.toString())
                                    }
                                }
                            }
                        )
                    }
                )
                Divider(color = Surface3, modifier = Modifier.padding(vertical = 8.dp))
                SettingRow(
                    label    = "Task Alarms",
                    subtitle = "Tray notification when a scheduled task is about to start (5min + 1min warnings)",
                    trailing = {
                        Button(
                            onClick = { TaskAlarmService.testAlarm("Test Task") },
                            colors = ButtonDefaults.outlinedButtonColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                )
            }
        }

        // ── Blocked Apps ──────────────────────────────────────────────────────
        item {
            SectionCard(title = "Blocked Apps (${blockRules.size})") {
                Text(
                    "These apps are killed instantly when detected during a session. You can type a process name or pick from running apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )
                Spacer(Modifier.height(12.dp))

                // ── Quick presets ────────────────────────────────────────────
                Text("Quick add common apps:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(6.dp))
                val presets = listOf(
                    "Discord"     to "discord.exe",
                    "Steam"       to "steam.exe",
                    "Spotify"     to "Spotify.exe",
                    "Twitch"      to "twitch.exe",
                    "Epic Games"  to "EpicGamesLauncher.exe",
                    "WhatsApp"    to "WhatsApp.exe",
                    "Telegram"    to "Telegram.exe",
                    "Battle.net"  to "Battle.net Launcher.exe"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (name, proc) ->
                                val alreadyAdded = blockRules.any { it.processName.equals(proc, ignoreCase = true) }
                                OutlinedButton(
                                    onClick = {
                                        if (!alreadyAdded) scope.launch {
                                            withContext(Dispatchers.IO) {
                                                Database.upsertBlockRule(
                                                    BlockRule(UUID.randomUUID().toString(), proc.lowercase(), name, true, false)
                                                )
                                            }
                                            reload()
                                        }
                                    },
                                    enabled = !alreadyAdded,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    if (alreadyAdded) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Surface3))
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
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    Database.upsertBlockRule(rule.copy(enabled = enabled))
                                                }
                                                if (!enabled) NetworkBlocker.removeRule(rule.processName)
                                                reload()
                                            }
                                        },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    Database.deleteBlockRule(rule.id)
                                                }
                                                NetworkBlocker.removeRule(rule.processName)
                                                reload()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showAddRule = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Manually")
                    }
                    OutlinedButton(onClick = { showAddRule = true }) {
                        Icon(Icons.Default.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick from Apps")
                    }
                }
            }
        }

        // ── Block Overlay ─────────────────────────────────────────────────────
        item {
            SectionCard(title = "Block Overlay") {
                Text("Message shown when a blocked app is detected:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = overlayMessage,
                    onValueChange = { msg ->
                        overlayMessage = msg
                        scope.launch {
                            withContext(Dispatchers.IO) { Database.setSetting("overlay_message", msg) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Purple80,
                        unfocusedBorderColor = OnSurface2
                    )
                )
            }
        }

        // ── Session PIN ───────────────────────────────────────────────────────
        item {
            SectionCard(title = "Session PIN") {
                SettingRow(
                    label    = "PIN Lock",
                    subtitle = if (pinSet) "Required to end an active session"
                               else "No PIN — anyone can end a session",
                    trailing = {
                        Button(
                            onClick = { showPinDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pinSet) Error.copy(alpha = 0.7f) else Purple80
                            )
                        ) {
                            Text(if (pinSet) "Remove PIN" else "Set PIN")
                        }
                    }
                )
            }
        }

        // ── Block Schedules ───────────────────────────────────────────────────
        item {
            SectionCard(title = "Block Schedules (${blockSchedules.size})") {
                Text("Schedule recurring time windows when apps are blocked — e.g. block social media every weekday 9am–5pm.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(12.dp))
                val activeNow = BlockScheduleService.activeScheduleNames
                if (blockSchedules.isEmpty()) {
                    Text("No schedules yet.", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        blockSchedules.forEach { sched ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(sched.name, color = OnSurface)
                                        if (sched.name in activeNow) {
                                            Spacer(Modifier.width(8.dp))
                                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Success.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("active", style = MaterialTheme.typography.bodySmall, color = Success)
                                            }
                                        }
                                    }
                                    val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
                                    val dayStr = sched.daysOfWeek.mapNotNull { days.getOrNull(it - 1) }.joinToString(", ")
                                    Text("$dayStr  %02d:%02d–%02d:%02d".format(sched.startHour, sched.startMinute, sched.endHour, sched.endMinute), style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                    if (sched.processNames.isNotEmpty()) Text("${sched.processNames.size} app(s)", style = MaterialTheme.typography.bodySmall, color = Purple60)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Switch(
                                        checked = sched.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    Database.upsertBlockSchedule(sched.copy(enabled = enabled))
                                                }
                                                BlockScheduleService.forceCheck()
                                                reload()
                                            }
                                        },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    Database.deleteBlockSchedule(sched.id)
                                                }
                                                BlockScheduleService.forceCheck()
                                                reload()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showAddSchedule = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add Schedule")
                }
            }
        }

        // ── Daily Allowances ──────────────────────────────────────────────────
        item {
            SectionCard(title = "Daily Allowances (${dailyAllowances.size})") {
                Text("Cap how many minutes per day each app can be used before it gets blocked for the rest of the day.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Spacer(Modifier.height(12.dp))
                if (dailyAllowances.isEmpty()) {
                    Text("No allowances set.", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dailyAllowances.forEach { a ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(a.displayName, color = OnSurface)
                                    Text("${a.processName}  ·  ${a.allowanceMinutes}m/day", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                Database.deleteDailyAllowance(a.processName)
                                            }
                                            DailyAllowanceTracker.reload()
                                            reload()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showAddAllowance = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add Allowance")
                }
            }
        }

        // ── About ─────────────────────────────────────────────────────────────
        item {
            SectionCard(title = "About FocusFlow JVM") {
                Text("FocusFlow JVM v1.1.0", color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text("Kotlin 1.9.22 + Compose Multiplatform Desktop 1.6.1", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Text("Enforcement: JNA Win32 + WinEventHook + Nuclear Mode + Windows Firewall", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Text("Features: Pomodoro, Daily Notes, 7-Day Stats, Task Alarms, App Scanner", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Text("Database: SQLite at %USERPROFILE%\\.focusflow\\focusflow.db", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }
    }

    if (showAddRule) {
        AddRuleDialog(
            onDismiss = { showAddRule = false },
            onSave    = { rule ->
                scope.launch {
                    withContext(Dispatchers.IO) { Database.upsertBlockRule(rule) }
                    reload()
                }
                showAddRule = false
            }
        )
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            onDismiss = { showAddSchedule = false },
            onSave    = { sched ->
                scope.launch {
                    withContext(Dispatchers.IO) { Database.upsertBlockSchedule(sched) }
                    BlockScheduleService.forceCheck()
                    reload()
                }
                showAddSchedule = false
            }
        )
    }

    if (showAddAllowance) {
        AddAllowanceDialog(
            onDismiss = { showAddAllowance = false },
            onSave    = { a ->
                scope.launch {
                    withContext(Dispatchers.IO) { Database.upsertDailyAllowance(a) }
                    DailyAllowanceTracker.reload()
                    reload()
                }
                showAddAllowance = false
            }
        )
    }

    if (showPinDialog) {
        PinDialog(
            pinAlreadySet = pinSet,
            onDismiss     = { showPinDialog = false },
            onSave        = { pin ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (pinSet) SessionPin.clear(pin) else SessionPin.set(pin)
                    }
                    reload()
                }
                showPinDialog = false
            }
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun PomodoroField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(3)) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple80,
            unfocusedBorderColor = OnSurface2
        )
    )
}

@Composable
private fun SettingRow(label: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        trailing()
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
    var processName  by remember { mutableStateOf("") }
    var displayName  by remember { mutableStateOf("") }
    var blockNetwork by remember { mutableStateOf(false) }
    var showPicker   by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }

    val scannedApps = remember {
        com.focusflow.enforcement.InstalledAppsScanner.getRunningApps()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (showPicker) "Pick App to Block" else "Block an App", color = OnSurface)
                TextButton(onClick = { showPicker = !showPicker }) {
                    Icon(
                        if (showPicker) Icons.Default.Edit else Icons.Default.Apps,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showPicker) "Manual" else "Pick App", color = Purple80)
                }
            }
        },
        text = {
            if (showPicker) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps…", color = OnSurface2) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurface2) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2
                        ),
                        singleLine = true
                    )
                    Column(
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        scannedApps
                            .filter {
                                searchQuery.isBlank() ||
                                it.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.processName.contains(searchQuery, ignoreCase = true)
                            }
                            .forEach { app ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (processName == app.processName) Purple80.copy(alpha = 0.15f)
                                            else Surface3
                                        )
                                        .clickable {
                                            processName = app.processName
                                            displayName = app.displayName
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.displayName, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                        Text(app.processName, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (app.isRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Success)
                                        )
                                    }
                                }
                            }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockNetwork, onCheckedChange = { blockNetwork = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Also block network access", color = OnSurface)
                            Text("Adds a Windows Firewall rule (requires admin)", style = MaterialTheme.typography.bodySmall, color = Warning)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = processName,
                        onValueChange = { processName = it },
                        label         = { Text("Process name (e.g. chrome.exe)") },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                    )
                    OutlinedTextField(
                        value         = displayName,
                        onValueChange = { displayName = it },
                        label         = { Text("Display name (e.g. Google Chrome)") },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
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
        containerColor   = Surface2,
        title            = { Text(if (pinAlreadySet) "Remove PIN" else "Set Session PIN", color = OnSurface) },
        text = {
            OutlinedTextField(
                value         = pin,
                onValueChange = { pin = it },
                label         = { Text(if (pinAlreadySet) "Current PIN" else "New PIN") },
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (pin.isNotBlank()) onSave(pin) },
                colors  = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onSave: (BlockSchedule) -> Unit) {
    var name         by remember { mutableStateOf("") }
    var startHour    by remember { mutableStateOf("9") }
    var startMinute  by remember { mutableStateOf("0") }
    var endHour      by remember { mutableStateOf("17") }
    var endMinute    by remember { mutableStateOf("0") }
    var selectedDays by remember { mutableStateOf(setOf(1,2,3,4,5)) }
    var processNames by remember { mutableStateOf("") }

    val dayLabels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Add Block Schedule", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(460.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                Text("Days of week:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayLabels.forEachIndexed { i, label ->
                        val day = i + 1
                        FilterChip(selected = day in selectedDays, onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = startHour,   onValueChange = { startHour   = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Start Hr") },  modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                    OutlinedTextField(value = startMinute, onValueChange = { startMinute = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Start Min") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                    OutlinedTextField(value = endHour,     onValueChange = { endHour     = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("End Hr") },    modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                    OutlinedTextField(value = endMinute,   onValueChange = { endMinute   = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("End Min") },   modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                }
                OutlinedTextField(value = processNames, onValueChange = { processNames = it }, label = { Text("Processes (comma-separated, optional)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                Text("Leave processes blank to use all block_rules during this window.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || selectedDays.isEmpty()) return@Button
                val procs = processNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
                onSave(BlockSchedule(id = UUID.randomUUID().toString(), name = name.trim(), daysOfWeek = selectedDays.toList().sorted(), startHour = startHour.toIntOrNull()?.coerceIn(0,23) ?: 9, startMinute = startMinute.toIntOrNull()?.coerceIn(0,59) ?: 0, endHour = endHour.toIntOrNull()?.coerceIn(0,23) ?: 17, endMinute = endMinute.toIntOrNull()?.coerceIn(0,59) ?: 0, processNames = procs))
            }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
private fun AddAllowanceDialog(onDismiss: () -> Unit, onSave: (DailyAllowance) -> Unit) {
    var processName   by remember { mutableStateOf("") }
    var displayName   by remember { mutableStateOf("") }
    var allowanceMins by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Add Daily Allowance", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(380.dp)) {
                Text("Cap how many minutes per day this app can be used before being blocked for the rest of the day.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                OutlinedTextField(value = processName, onValueChange = { processName = it }, label = { Text("Process name (e.g. chrome.exe)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allowance:", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    listOf(15, 30, 60, 120).forEach { m ->
                        FilterChip(selected = allowanceMins == m.toString(), onClick = { allowanceMins = m.toString() }, label = { Text("${m}m") })
                    }
                }
                OutlinedTextField(value = allowanceMins, onValueChange = { allowanceMins = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Custom (minutes)") }, modifier = Modifier.width(160.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (processName.isBlank()) return@Button
                onSave(DailyAllowance(processName = processName.trim(), displayName = displayName.ifBlank { processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() } }, allowanceMinutes = allowanceMins.toIntOrNull()?.coerceAtLeast(1) ?: 30))
            }, colors = ButtonDefaults.buttonColors(containerColor = Purple80)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}
