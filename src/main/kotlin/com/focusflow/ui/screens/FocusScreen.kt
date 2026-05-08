package com.focusflow.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
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
import com.focusflow.data.models.Task
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.services.*
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun FocusScreen(preloadTask: Task? = null) {
    val sessionState    by FocusSessionService.state.collectAsState()
    val pomodoroState   by BreakEnforcer.state.collectAsState()
    val standaloneBlock by StandaloneBlockService.block.collectAsState()
    val lastSummary     by FocusSessionService.lastSummary.collectAsState()

    var customTaskName by remember { mutableStateOf(preloadTask?.title ?: "") }
    var customMinutes  by remember { mutableStateOf((preloadTask?.durationMinutes ?: pomodoroState.workMinutes).toString()) }
    var pomodoroMode   by remember { mutableStateOf(Database.getSetting("pomodoro_mode") == "true") }
    var sessionNotes      by remember { mutableStateOf("") }
    var distractionCount  by remember { mutableStateOf(0) }
    var recentTasks       by remember { mutableStateOf(listOf<Task>()) }
    var alwaysOnEnabled      by remember { mutableStateOf(false) }
    var blockRulesCount      by remember { mutableStateOf(0) }
    var scheduleCount        by remember { mutableStateOf(0) }
    var dailyAllowancesCount by remember { mutableStateOf(0) }

    var focusModeActive    by remember { mutableStateOf(preloadTask?.focusMode == true) }
    var focusIntensity     by remember { mutableStateOf(preloadTask?.focusIntensity ?: "standard") }
    var focusModeAutoEnabledEnforcement by remember { mutableStateOf(false) }
    var focusModeAutoEnabledNuclear     by remember { mutableStateOf(false) }

    var showStandaloneDialog by remember { mutableStateOf(false) }
    var showEndPinDialog     by remember { mutableStateOf(false) }
    var showPinRevealDialog  by remember { mutableStateOf(false) }
    var generatedPinText     by remember { mutableStateOf("") }
    var pendingStartMins     by remember { mutableStateOf(0) }
    var pendingStartApps     by remember { mutableStateOf(listOf<String>()) }
    var focusModeRequirePin  by remember { mutableStateOf(preloadTask?.focusRequirePin == true) }
    var focusLockUntilTimer  by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            val rt  = withContext(Dispatchers.IO) { Database.getTasks().filter { !it.completed }.take(10) }
            val aoe = withContext(Dispatchers.IO) { Database.getSetting("always_on_enforcement") == "true" }
            val brc = withContext(Dispatchers.IO) { Database.getBlockRules().count { it.enabled } }
            val sc  = withContext(Dispatchers.IO) { Database.getBlockSchedules().count { it.enabled } }
            val dac = withContext(Dispatchers.IO) { Database.getDailyAllowances().size }
            recentTasks          = rt
            alwaysOnEnabled      = aoe
            blockRulesCount      = brc
            scheduleCount        = sc
            dailyAllowancesCount = dac
        }
    }

    LaunchedEffect(Unit) {
        reload()
        withContext(Dispatchers.IO) { BreakEnforcer.loadSettings() }
        focusLockUntilTimer = withContext(Dispatchers.IO) { Database.getSetting("focus_lock_until_timer") == "true" }
    }

    LaunchedEffect(preloadTask) {
        preloadTask?.let {
            customTaskName      = it.title
            customMinutes       = it.durationMinutes.toString()
            focusModeActive     = it.focusMode
            focusIntensity      = it.focusIntensity
            focusModeRequirePin = it.focusRequirePin
        }
    }

    LaunchedEffect(sessionState.isActive) {
        if (!sessionState.isActive) {
            if (focusModeAutoEnabledEnforcement) {
                alwaysOnEnabled = false
                ProcessMonitor.alwaysOnEnabled = false
                withContext(Dispatchers.IO) { Database.setSetting("always_on_enforcement", "false") }
                focusModeAutoEnabledEnforcement = false
            }
            // Auto-disable Nuclear Mode if we started it for this session
            if (focusModeAutoEnabledNuclear && NuclearMode.isActive) {
                NuclearMode.disable()
                focusModeAutoEnabledNuclear = false
            }
        }
    }

    val isStandaloneActive  = standaloneBlock != null && StandaloneBlockService.isActive
    val standaloneRemaining = StandaloneBlockService.remainingMs()

    val focusScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().background(Surface).verticalScroll(focusScrollState).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Focus", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pomodoro", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Switch(
                    checked = pomodoroMode,
                    onCheckedChange = {
                        pomodoroMode = it
                        FocusSessionService.pomodoroMode = it
                        Database.setSetting("pomodoro_mode", it.toString())
                        if (!it) BreakEnforcer.reset()
                    }
                )
            }
        }

        if (pomodoroMode) {
            PomodoroCycleIndicator(
                cycleNumber      = pomodoroState.cycleNumber,
                cyclesBeforeLong = pomodoroState.cyclesBeforeLongBreak
            )
        }

        // ── Break overlay ─────────────────────────────────────────────────────
        if (pomodoroMode && pomodoroState.phase != BreakPhase.IDLE) {
            val isLong     = pomodoroState.phase == BreakPhase.LONG_BREAK
            val breakColor = if (isLong) Success else Purple80
            val breakMins  = pomodoroState.breakSecondsRemaining / 60
            val breakSecs  = pomodoroState.breakSecondsRemaining % 60
            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(breakColor.copy(alpha = 0.12f)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(if (isLong) "Long Break" else "Short Break", style = MaterialTheme.typography.headlineMedium, color = breakColor)
                Text("%02d:%02d".format(breakMins, breakSecs), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp), color = breakColor, fontWeight = FontWeight.Bold)
                Text(if (isLong) "Great work! Take a real break." else "Short break — stretch, breathe.", color = OnSurface2, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { BreakEnforcer.skipBreak() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = breakColor)) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Skip Break")
                }
            }

        } else if (!sessionState.isActive) {
            // ── Setup panel ───────────────────────────────────────────────────
            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Surface2).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Configure Session", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                OutlinedTextField(
                    value = customTaskName, onValueChange = { customTaskName = it },
                    label = { Text("What are you working on?") }, modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )

                if (recentTasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Quick pick from tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface2
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().widthIn(max = 400.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentTasks.take(8).forEach { task ->
                                FilterChip(
                                    selected = customTaskName == task.title,
                                    onClick  = {
                                        customTaskName = task.title
                                        customMinutes  = task.durationMinutes.toString()
                                    },
                                    label = {
                                        Text(
                                            task.title,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor    = Purple80.copy(alpha = 0.20f),
                                        selectedLabelColor        = Purple80,
                                        containerColor            = Surface3,
                                        labelColor                = OnSurface2
                                    )
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customMinutes, onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Duration (minutes)") }, modifier = Modifier.widthIn(min = 120.dp, max = 200.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        FilterChip(selected = customMinutes == m.toString(), onClick = { customMinutes = m.toString() }, label = { Text("${m}m") })
                    }
                }
                if (blockRulesCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Purple80.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("$blockRulesCount app${if (blockRulesCount == 1) "" else "s"} will be blocked", style = MaterialTheme.typography.bodySmall, color = Purple80)
                    }
                }
                if (pomodoroMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Success.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Autorenew, null, tint = Success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pomodoro: ${pomodoroState.workMinutes}m work → ${pomodoroState.shortBreakMinutes}m break", style = MaterialTheme.typography.bodySmall, color = Success)
                    }
                }

                // ── Focus Mode setup card ─────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focusModeActive) Purple80.copy(alpha = 0.10f) else Surface3)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Shield, null, tint = if (focusModeActive) Purple80 else OnSurface2, modifier = Modifier.size(18.dp))
                            Column {
                                Text("Focus Mode", style = MaterialTheme.typography.bodyMedium, color = if (focusModeActive) Purple80 else OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        !focusModeActive        -> "Optional — blocks distracting apps during session"
                                        focusIntensity == "deep"    -> "Deep Work — always-on enforcement enabled"
                                        focusIntensity == "nuclear" -> "Nuclear — maximum blocking mode"
                                        else                    -> "Standard — your block rules apply"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        !focusModeActive          -> OnSurface2
                                        focusIntensity == "nuclear" -> Error
                                        focusIntensity == "deep"    -> Warning
                                        else                      -> Purple60
                                    }
                                )
                            }
                        }
                        Switch(
                            checked = focusModeActive,
                            onCheckedChange = { focusModeActive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple80, checkedTrackColor = Purple80.copy(alpha = 0.4f))
                        )
                    }
                    if (focusModeActive) {
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Text("Intensity", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("standard", "Standard",  "Block rules apply"),
                                Triple("deep",     "Deep Work", "Always-on enforcement"),
                                Triple("nuclear",  "Nuclear",   "Maximum blocking")
                            ).forEach { (key, label, desc) ->
                                val sel = focusIntensity == key
                                val col = when (key) { "deep" -> Warning; "nuclear" -> Error; else -> Purple80 }
                                FilterChip(
                                    selected = sel,
                                    onClick  = { focusIntensity = key },
                                    label = {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = OnSurface2)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = col.copy(alpha = 0.15f),
                                        selectedLabelColor     = col,
                                        containerColor         = Surface2,
                                        labelColor             = OnSurface2
                                    )
                                )
                            }
                        }
                        // ── PIN toggle ──────────────────────────────────────────
                        HorizontalDivider(color = Purple80.copy(alpha = 0.15f))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { focusModeRequirePin = !focusModeRequirePin }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = focusModeRequirePin,
                                onCheckedChange = { focusModeRequirePin = it },
                                colors = CheckboxDefaults.colors(checkedColor = Purple80)
                            )
                            Column {
                                Text(
                                    "Require PIN to end early",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Unique PIN shown once at start — write it down",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = OnSurface2
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sessionNotes,
                    onValueChange = { sessionNotes = it },
                    label = { Text("Pre-session notes (optional)") },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                    maxLines = 3,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                val startBtnColor = when {
                    focusModeActive && focusIntensity == "nuclear" -> Error.copy(alpha = 0.9f)
                    focusModeActive && focusIntensity == "deep"    -> Warning.copy(alpha = 0.9f)
                    else                                           -> Purple80
                }
                Button(
                    onClick = {
                        val mins = if (pomodoroMode) pomodoroState.workMinutes else customMinutes.toIntOrNull() ?: 25
                        distractionCount = 0
                        FocusSessionService.setNotes(sessionNotes.trim())
                        sessionNotes = ""
                        if (focusModeActive && focusIntensity != "standard" && !alwaysOnEnabled) {
                            alwaysOnEnabled = true
                            ProcessMonitor.alwaysOnEnabled = true
                            Database.setSetting("always_on_enforcement", "true")
                            focusModeAutoEnabledEnforcement = true
                        }
                        // Nuclear intensity: actually enable Nuclear Mode
                        if (focusModeActive && focusIntensity == "nuclear" && !NuclearMode.isActive) {
                            NuclearMode.enable()
                            focusModeAutoEnabledNuclear = true
                        }
                        val extraApps = preloadTask?.focusBlockedApps ?: emptyList()
                        if (focusModeActive && focusModeRequirePin) {
                            pendingStartMins = mins
                            pendingStartApps = extraApps
                            generatedPinText = SessionPin.autoGenerate()
                            showPinRevealDialog = true
                        } else {
                            SessionPin.clearForced()
                            FocusSessionService.start(customTaskName.ifBlank { "Focus Session" }, mins, blockedProcesses = extraApps)
                            TemptationLogger.clearSession()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = startBtnColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (focusModeActive) Icons.Default.Shield else Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (focusModeActive) "Start Focus Mode" else "Start Focus",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Standalone block / always-on panel ────────────────────────────
            StandaloneBlockPanel(
                isActive             = isStandaloneActive,
                remainingMs          = standaloneRemaining,
                blockedCount         = standaloneBlock?.processNames?.size ?: 0,
                alwaysOnEnabled      = alwaysOnEnabled,
                blockRulesCount      = blockRulesCount,
                scheduleCount        = scheduleCount,
                dailyAllowancesCount = dailyAllowancesCount,
                onStartBlock         = { showStandaloneDialog = true },
                onAddTime            = { StandaloneBlockService.addTime(it * 60_000L) },
                onToggleAlwaysOn     = {
                    alwaysOnEnabled = !alwaysOnEnabled
                    ProcessMonitor.alwaysOnEnabled = alwaysOnEnabled
                    Database.setSetting("always_on_enforcement", alwaysOnEnabled.toString())
                }
            )

        } else {
            // ── Active session ring ────────────────────────────────────────────
            val progress  = if (sessionState.totalSeconds > 0) sessionState.elapsedSeconds.toFloat() / sessionState.totalSeconds else 0f
            val remaining = sessionState.totalSeconds - sessionState.elapsedSeconds
            val mins = remaining / 60; val secs = remaining % 60

            val ringColor = when {
                sessionState.isPaused -> OnSurface2.copy(alpha = 0.4f)
                remaining <= 60       -> Error.copy(alpha = 0.9f)
                remaining <= 300      -> Warning
                else                  -> Purple80
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2
                    val center = Offset(size.width / 2, size.height / 2)
                    drawArc(Surface3, -90f, 360f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(ringColor, -90f, 360f * progress, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(mins, secs), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp), color = if (sessionState.isPaused) OnSurface2 else ringColor, fontWeight = FontWeight.Bold)
                    Text(sessionState.taskName, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                    if (pomodoroMode) { Spacer(Modifier.height(4.dp)); Text("Cycle ${pomodoroState.cycleNumber + 1}", style = MaterialTheme.typography.bodySmall, color = Purple60) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (sessionState.isPaused) {
                    Button(onClick = { FocusSessionService.resume() }, colors = ButtonDefaults.buttonColors(containerColor = Success)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Resume")
                    }
                } else {
                    OutlinedButton(onClick = { FocusSessionService.pause() }) {
                        Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause")
                    }
                }
                Button(
                    onClick = {
                        when {
                            focusLockUntilTimer -> { /* timer must expire */ }
                            SessionPin.isSet()  -> showEndPinDialog = true
                            else               -> { FocusSessionService.end(completed = false); if (pomodoroMode) BreakEnforcer.reset() }
                        }
                    },
                    enabled = !focusLockUntilTimer,
                    colors  = ButtonDefaults.buttonColors(containerColor = if (focusLockUntilTimer) OnSurface2.copy(alpha = 0.4f) else Error.copy(alpha = 0.8f))
                ) {
                    Icon(if (focusLockUntilTimer) Icons.Default.Lock else Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (focusLockUntilTimer) "Locked" else "End")
                }
            }

            if (focusModeActive) {
                val intensityColor = when (focusIntensity) { "nuclear" -> Error; "deep" -> Warning; else -> Purple80 }
                val intensityLabel = when (focusIntensity) { "nuclear" -> "Nuclear Mode"; "deep" -> "Deep Work"; else -> "Standard" }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(intensityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Shield, null, tint = intensityColor, modifier = Modifier.size(14.dp))
                    Text("Focus Mode · $intensityLabel", style = MaterialTheme.typography.bodySmall, color = intensityColor, fontWeight = FontWeight.SemiBold)
                }
            }

            val motivationalMessage = when {
                sessionState.isPaused         -> "Session paused — resume when you're ready."
                progress < 0.25f              -> "Just getting started — lock in and go!"
                progress < 0.5f              -> "Good momentum — you're finding your flow."
                progress < 0.75f             -> "More than halfway there — keep the energy up!"
                progress < 0.95f             -> "Almost done — don't slow down now!"
                else                         -> "Final stretch — finish strong!"
            }
            Text(
                motivationalMessage,
                style     = MaterialTheme.typography.bodySmall,
                color     = if (sessionState.isPaused) OnSurface2 else Purple60,
                fontWeight = FontWeight.Medium
            )

            val count = TemptationLogger.getSessionAttempts()
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Surface2).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (count == 0) "No blocked app attempts this session" else "$count app attempt${if (count == 1) "" else "s"} blocked", style = MaterialTheme.typography.bodySmall, color = if (count == 0) Success else Warning)
            }

            // Distraction counter
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = if (distractionCount == 0) Success else Warning, modifier = Modifier.size(16.dp))
                Text(
                    if (distractionCount == 0) "No distractions logged" else "$distractionCount distraction${if (distractionCount == 1) "" else "s"} logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (distractionCount == 0) Success else Warning,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { distractionCount++ },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Log", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Session notes
            OutlinedTextField(
                value        = sessionNotes,
                onValueChange = {
                    sessionNotes = it
                    FocusSessionService.setNotes(it)
                },
                label      = { Text("Session notes (saved when session ends)") },
                modifier   = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                maxLines   = 3,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Purple80,
                    unfocusedBorderColor = OnSurface2.copy(alpha = 0.5f)
                )
            )
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(focusScrollState)
    )
    }

    // ── Session summary dialog ─────────────────────────────────────────────────
    if (lastSummary != null) {
        SessionSummaryDialog(
            summary   = lastSummary!!,
            onDismiss = { FocusSessionService.clearSummary() }
        )
    }

    if (showEndPinDialog) {
        EndSessionPinDialog(
            onDismiss  = { showEndPinDialog = false },
            onVerified = {
                showEndPinDialog = false
                SessionPin.clearForced()
                FocusSessionService.end(completed = false)
                if (pomodoroMode) BreakEnforcer.reset()
            }
        )
    }

    if (showPinRevealDialog) {
        PinRevealDialog(
            pin = generatedPinText,
            onConfirm = {
                showPinRevealDialog = false
                FocusSessionService.start(customTaskName.ifBlank { "Focus Session" }, pendingStartMins, blockedProcesses = pendingStartApps)
                TemptationLogger.clearSession()
            },
            onDismiss = {
                showPinRevealDialog = false
                SessionPin.clearForced()
            }
        )
    }

    if (showStandaloneDialog) {
        StartStandaloneBlockDialog(
            onDismiss = { showStandaloneDialog = false },
            onStart   = { apps, hours ->
                StandaloneBlockService.start(apps.split(",").map { it.trim() }.filter { it.isNotBlank() }, hours * 3600_000L)
                showStandaloneDialog = false
            }
        )
    }
}

// ── PIN Reveal Dialog ──────────────────────────────────────────────────────────

@Composable
private fun PinRevealDialog(pin: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Purple80, modifier = Modifier.size(24.dp))
                Text("Your Session PIN", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Write this PIN down — you'll need it if you want to end the session early.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Purple80.copy(alpha = 0.12f))
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        pin,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Purple80,
                        letterSpacing = 4.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Warning.copy(alpha = 0.10f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(16.dp))
                    Text("This PIN is shown only once — a new one is generated each session.", style = MaterialTheme.typography.bodySmall, color = Warning)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Got it — Start Session")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

// ── Session Summary Dialog ─────────────────────────────────────────────────────

@Composable
private fun SessionSummaryDialog(summary: SessionSummary, onDismiss: () -> Unit) {
    val h = summary.actualMinutes / 60
    val m = summary.actualMinutes % 60
    val timeStr = when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m"
        else   -> "< 1m"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (summary.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null,
                    tint   = if (summary.completed) Success else Warning,
                    modifier = Modifier.size(28.dp)
                )
                Text(if (summary.completed) "Session Complete!" else "Session Ended", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(summary.taskName, style = MaterialTheme.typography.bodyLarge, color = OnSurface2)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Time box
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(Purple80.copy(alpha = 0.12f)).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Timer, null, tint = Purple80, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(timeStr, style = MaterialTheme.typography.titleMedium, color = Purple80, fontWeight = FontWeight.Bold)
                        Text("Focused", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }

                    // Blocked attempts box
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(
                                if (summary.blockedAttempts == 0) Success.copy(alpha = 0.10f)
                                else Warning.copy(alpha = 0.10f)
                            ).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Shield, null,
                            tint = if (summary.blockedAttempts == 0) Success else Warning,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("${summary.blockedAttempts}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (summary.blockedAttempts == 0) Success else Warning,
                            fontWeight = FontWeight.Bold)
                        Text("Blocked", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                }

                if (summary.completed) {
                    Text(
                        "Keep the streak going — great session!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Success
                    )
                } else {
                    val temptSummary = TemptationLogger.getSessionSummary()
                    if (summary.blockedAttempts > 0) {
                        Text(temptSummary, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text("Done") }
        }
    )
}

// ── Enforcement sub-row ────────────────────────────────────────────────────────

@Composable
private fun EnforcementRow(
    icon:       androidx.compose.ui.graphics.vector.ImageVector,
    label:      String,
    count:      Int,
    countLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                .background(Purple80.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Purple80, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(
                if (count > 0) "$count $countLabel" else "None configured",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface2
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Purple80.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = Purple80,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
    }
}

// ── Standalone Block Panel ─────────────────────────────────────────────────────

@Composable
private fun StandaloneBlockPanel(
    isActive:             Boolean,
    remainingMs:          Long,
    blockedCount:         Int,
    alwaysOnEnabled:      Boolean,
    blockRulesCount:      Int,
    scheduleCount:        Int,
    dailyAllowancesCount: Int,
    onStartBlock:         () -> Unit,
    onAddTime:            (Int) -> Unit,
    onToggleAlwaysOn:     () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Header row with master toggle ──────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = if (alwaysOnEnabled || isActive) Warning else OnSurface2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Always-On Enforcement", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        if (alwaysOnEnabled) "Active — blocking 24/7 outside sessions"
                        else "Paused — apps are not being blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alwaysOnEnabled) Warning else OnSurface2
                    )
                }
            }
            Switch(
                checked = alwaysOnEnabled,
                onCheckedChange = { onToggleAlwaysOn() },
                colors = SwitchDefaults.colors(checkedThumbColor = Warning, checkedTrackColor = Warning.copy(alpha = 0.4f))
            )
        }

        HorizontalDivider(color = Surface3)

        // ── Android-style 4 enforcement sub-rows ──────────────────────────────
        EnforcementRow(Icons.Default.Block,    "Always-On App List", blockRulesCount,      "app${if (blockRulesCount == 1) "" else "s"}")
        EnforcementRow(Icons.Default.Timer,    "Daily Allowance",    dailyAllowancesCount, "app${if (dailyAllowancesCount == 1) "" else "s"}")
        EnforcementRow(Icons.Default.Schedule, "Block Schedules",    scheduleCount,        "schedule${if (scheduleCount == 1) "" else "s"}")
        EnforcementRow(Icons.Default.Search,   "Keyword Blocker",    0,                    "keywords")

        HorizontalDivider(color = Surface3)

        if (isActive) {
            val remSec = (remainingMs / 1000).toInt()
            val h = remSec / 3600; val m = (remSec % 3600) / 60; val s = remSec % 60
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Error.copy(alpha = 0.08f)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = Error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("$blockedCount app${if (blockedCount == 1) "" else "s"} blocked", color = Error, fontWeight = FontWeight.Bold)
                        Text(if (h > 0) "${h}h ${m}m remaining" else "${m}m ${s}s remaining", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                }
            }
            Text("Add time to extend the block:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "+30m", 60 to "+1h", 120 to "+2h", 240 to "+4h").forEach { (mins, label) ->
                    OutlinedButton(
                        onClick = { onAddTime(mins) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else {
            Text("Quick Block — apps are blocked for a set time. Cannot be cancelled early.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartBlock, colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f))) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Block", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (scheduleCount > 0) {
            HorizontalDivider(color = Surface3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = Purple60, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("$scheduleCount active schedule${if (scheduleCount == 1) "" else "s"} running", style = MaterialTheme.typography.bodySmall, color = Purple60)
            }
        }
    }
}

@Composable
private fun StartStandaloneBlockDialog(onDismiss: () -> Unit, onStart: (String, Int) -> Unit) {
    var apps  by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Quick Block Apps", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter process names (e.g. chrome.exe, discord.exe) separated by commas. The block CANNOT be cancelled.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                OutlinedTextField(
                    value = apps, onValueChange = { apps = it },
                    label = { Text("Process names (comma separated)") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Error, unfocusedBorderColor = OnSurface2)
                )
                Text("Duration", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 8).forEach { h ->
                        FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("${h}h") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (apps.isNotBlank()) onStart(apps, hours) }, colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f))) { Text("Start Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

// ── End Session PIN Dialog ─────────────────────────────────────────────────────

@Composable
private fun EndSessionPinDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(22.dp))
                Text("PIN Required", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your session PIN to end the session early.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { pin = it; error = false },
                    label         = { Text("PIN") },
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = error,
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Purple80,
                        unfocusedBorderColor = OnSurface2,
                        errorBorderColor     = Error
                    )
                )
                if (error) {
                    Text("Incorrect PIN. Try again.", color = Error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (SessionPin.verify(pin)) onVerified()
                    else error = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f))
            ) { Text("End Session") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) } }
    )
}

@Composable
private fun PomodoroCycleIndicator(cycleNumber: Int, cyclesBeforeLong: Int) {
    val position = cycleNumber % cyclesBeforeLong
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Autorenew, null, tint = Purple80, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        (0 until cyclesBeforeLong).forEach { i ->
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(when { i < position -> Purple80; i == position -> Purple80.copy(alpha = 0.5f); else -> Surface3 }))
        }
        Spacer(Modifier.width(4.dp))
        Text(if (position == 0 && cycleNumber > 0) "Cycle ${cycleNumber / cyclesBeforeLong + 1}" else "Session ${position + 1}/${cyclesBeforeLong}", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
}
