package com.focusflow.ui.screens

import com.focusflow.ui.components.FfVerticalScrollbar
import com.focusflow.ui.components.PinInfoButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.data.*
import com.focusflow.data.models.BlockRule
import com.focusflow.data.models.BlockSchedule
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.VpnBlocker
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.BlockScheduleService
import com.focusflow.services.GlobalPin
import com.focusflow.services.SessionPin
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.focusflow.ui.components.EnforcementStatusBanner
import com.focusflow.ui.components.HintCard
import com.focusflow.ui.components.HintType
import com.focusflow.ui.components.GlobalPinSetupDialog
import com.focusflow.ui.components.LiveHintBanner
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BlockDefenseScreen(onNavigateToVpn: () -> Unit = {}, onNavigateToAppBlocker: () -> Unit = {}) {
    val strings = LocalizationManager.strings
    val scope = rememberCoroutineScope()

    var alwaysOn         by remember { mutableStateOf(false) }
    var vpnEnabled       by remember { mutableStateOf(false) }
    var soundAversion    by remember { mutableStateOf(false) }
    var temptationLog    by remember { mutableStateOf(false) }
    var alwaysOnRules    by remember { mutableStateOf(listOf<BlockRule>()) }
    var blockSchedules   by remember { mutableStateOf(listOf<BlockSchedule>()) }
    var overlayMsg       by remember { mutableStateOf("") }
    var sessionPinSet    by remember { mutableStateOf(SessionPin.isSet()) }

    var showAddSchedule      by remember { mutableStateOf(false) }
    var showPinGate          by remember { mutableStateOf(false) }
    var pendingAlwaysOn      by remember { mutableStateOf(false) }
    var showSessionPinSetup  by remember { mutableStateOf(false) }
    var showSessionPinChange by remember { mutableStateOf(false) }
    var liveHint             by remember { mutableStateOf<Pair<HintType, String>?>(null) }

    var globalPinActive     by remember { mutableStateOf(false) }
    var globalPinHasHash    by remember { mutableStateOf(false) }
    var showGlobalPinSetup      by remember { mutableStateOf(false) }
    var showGlobalPinDeactivate by remember { mutableStateOf(false) }
    var showGlobalPinResume     by remember { mutableStateOf(false) }
    var showGlobalPinVerifyOld  by remember { mutableStateOf(false) }
    var showVpnPinGate          by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            // Read all values on IO, then assign Compose state back on Main
            // (withContext returns to the caller's dispatcher — Main — after the block).
            val s = withContext(Dispatchers.IO) {
                object {
                    val alwaysOn       = Database.getSetting("always_on_enforcement") == "true"
                    val vpnEnabled     = VpnBlocker.isEnabled
                    val soundAversion  = Database.getSetting("sound_aversion") == "true"
                    val temptationLog  = Database.getSetting("temptation_log") == "true"
                    val alwaysOnRules  = Database.getBlockRules().filter { it.enabled }
                    val blockSchedules = Database.getBlockSchedules()
                    val overlayMsg     = Database.getSetting("overlay_message") ?: ""
                    val sessionPinSet  = SessionPin.isSet()
                    val globalPinActive  = GlobalPin.isActive()
                    val globalPinHasHash = GlobalPin.isSet()
                }
            }
            alwaysOn        = s.alwaysOn
            vpnEnabled      = s.vpnEnabled
            soundAversion   = s.soundAversion
            temptationLog   = s.temptationLog
            alwaysOnRules   = s.alwaysOnRules
            blockSchedules  = s.blockSchedules
            overlayMsg      = s.overlayMsg
            sessionPinSet   = s.sessionPinSet
            globalPinActive  = s.globalPinActive
            globalPinHasHash = s.globalPinHasHash
        }
    }

    LaunchedEffect(Unit) { reload() }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        LiveHintBanner(
            message   = liveHint?.second ?: "",
            visible   = liveHint != null,
            type      = liveHint?.first ?: HintType.INFO,
            modifier  = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 32.dp, vertical = 8.dp),
            onDismiss = { liveHint = null }
        )

    Column(
        modifier = Modifier.fillMaxSize().background(Surface)
            .verticalScroll(scrollState).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.defTitle, style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Spacer(Modifier.width(8.dp))
            PinInfoButton()
        }

        EnforcementStatusBanner()

        // ── System Protection ──────────────────────────────────────────────────
        DefCard(title = strings.defSystemProtection) {
            DefToggleRow(
                label   = strings.defAlwaysOnEnforcement,
                checked = alwaysOn,
                icon    = Icons.Default.Shield,
                iconColor = if (alwaysOn) Success else OnSurface2
            ) { newVal ->
                if (!newVal && GlobalPin.isActive()) {
                    pendingAlwaysOn = false
                    showPinGate = true
                } else {
                    alwaysOn = newVal
                    ProcessMonitor.alwaysOnEnabled = newVal
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            Database.setSetting("always_on_enforcement", newVal.toString())
                        }
                    }
                    liveHint = if (newVal)
                        HintType.TIP to "Enforcement is live — blocked apps will be killed immediately, 24/7."
                    else
                        HintType.WARNING to "Always-On off. Blocking only happens during active Focus Sessions now."
                }
            }

            Spacer(Modifier.height(6.dp))

            // Global PIN Lock
            DefToggleRow(
                label     = "Global PIN Lock",
                checked   = globalPinActive,
                icon      = Icons.Default.Security,
                iconColor = if (globalPinActive) Success else OnSurface2
            ) { newVal ->
                if (newVal) {
                    if (globalPinHasHash) showGlobalPinResume = true
                    else showGlobalPinSetup = true
                } else {
                    showGlobalPinDeactivate = true
                }
            }

            Spacer(Modifier.height(6.dp))

            HintCard(
                title   = "Always-On vs. Focus Sessions",
                message = "Always-On enforces all enabled block rules 24/7 without starting a session. " +
                          "Turn it on here, then add apps in App Blocker → Always Block. " +
                          "Nuclear Mode only blocks escape tools (Task Manager, cmd) — it does NOT block regular apps.",
                type    = HintType.INFO,
                startExpanded = false,
            )

            Spacer(Modifier.height(10.dp))

            // Block overlay
            Text(strings.defOverlayMessage, color = OnSurface2, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = overlayMsg,
                onValueChange = { overlayMsg = it
                    scope.launch { withContext(Dispatchers.IO) { Database.setSetting("overlay_message", it) } }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2
                )
            )
        }

        // ── Focus Session Lock ─────────────────────────────────────────────────
        DefCard(title = "Focus Session Lock") {
            Text(
                "Require a PIN before ending a focus session early. " +
                "The PIN is revealed once when the session starts — write it down.",
                color = OnSurface2, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            DefToggleRow(
                label     = strings.defSessionPinLock,
                checked   = sessionPinSet,
                icon      = Icons.Default.Timer,
                iconColor = if (sessionPinSet) Warning else OnSurface2
            ) { newVal ->
                if (newVal) showSessionPinSetup = true else showSessionPinChange = true
            }
        }

        // ── VPN & Network Shield ───────────────────────────────────────────────
        DefCard(title = strings.defVpnSection) {
            DefToggleRow(
                label     = strings.vpnShieldLabel,
                checked   = vpnEnabled,
                icon      = Icons.Default.VpnKey,
                iconColor = if (vpnEnabled) Purple80 else OnSurface2
            ) { newVal ->
                if (!newVal && GlobalPin.isActive()) {
                    showVpnPinGate = true
                } else {
                    vpnEnabled = newVal
                    scope.launch { withContext(Dispatchers.IO) { VpnBlocker.isEnabled = newVal } }
                    liveHint = if (newVal)
                        HintType.TIP to "VPN Shield active — known VPN processes will be killed when detected."
                    else
                        HintType.WARNING to "VPN Shield off. Users can now bypass blocks using a VPN."
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToVpn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.defOpenVpn)
            }
        }

        // ── Aversion Deterrents ────────────────────────────────────────────────
        DefCard(title = strings.defAversionSection) {
            DefToggleRow(
                label     = strings.defSoundAversion,
                checked   = soundAversion,
                icon      = Icons.AutoMirrored.Filled.VolumeUp,
                iconColor = if (soundAversion) Warning else OnSurface2
            ) { newVal ->
                soundAversion = newVal
                scope.launch { withContext(Dispatchers.IO) { Database.setSetting("sound_aversion", newVal.toString()) } }
            }
            Spacer(Modifier.height(6.dp))
            DefToggleRow(
                label     = strings.defTemptationLog,
                checked   = temptationLog,
                icon      = Icons.Default.History,
                iconColor = if (temptationLog) Purple80 else OnSurface2
            ) { newVal ->
                temptationLog = newVal
                scope.launch { withContext(Dispatchers.IO) { Database.setSetting("temptation_log", newVal.toString()) } }
            }
        }

        // ── Always-On Block List ───────────────────────────────────────────────
        DefCard(title = strings.defAlwaysOnList) {
            if (alwaysOnRules.isEmpty()) {
                Text(strings.defNoAppsBlocked, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alwaysOnRules.take(8).forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Surface3).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(rule.displayName, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text(rule.processName, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(strings.defAlwaysOnTag, style = MaterialTheme.typography.bodySmall, color = Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (alwaysOnRules.size > 8) {
                        Text("+ ${alwaysOnRules.size - 8} more…", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = onNavigateToAppBlocker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit in App Blocker →")
            }
        }

        // ── Block Schedules ────────────────────────────────────────────────────
        DefCard(title = strings.defBlockSchedules) {
            if (blockSchedules.isEmpty()) {
                Text(strings.defNoSchedules, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val now = java.time.LocalTime.now()
                    blockSchedules.forEach { sched ->
                        val activeNow = sched.enabled && run {
                            val day = java.time.LocalDate.now().dayOfWeek.value
                            sched.daysOfWeek.contains(day) &&
                            now >= java.time.LocalTime.of(sched.startHour, sched.startMinute) &&
                            now < java.time.LocalTime.of(sched.endHour, sched.endMinute)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (activeNow) Warning.copy(alpha = 0.1f) else Surface3)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(sched.name, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
                                val dayStr = sched.daysOfWeek.mapNotNull { days.getOrNull(it-1) }.joinToString(", ")
                                Text(
                                    "$dayStr  %02d:%02d–%02d:%02d".format(sched.startHour, sched.startMinute, sched.endHour, sched.endMinute),
                                    color = OnSurface2, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (activeNow) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(Warning.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(strings.defScheduleActive, color = Warning, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAddSchedule = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.defAddSchedule, color = Purple80)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
    FfVerticalScrollbar(
        scrollState = scrollState,
        modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
    )
    }

    // ── PIN gate to turn off Always-On ─────────────────────────────────────────
    if (showPinGate) {
        PinGateDialog(
            title    = strings.defPinRequired,
            subtitle = strings.defEnterPin,
            onDismiss = { showPinGate = false },
            onVerified = {
                showPinGate = false
                alwaysOn = false
                ProcessMonitor.alwaysOnEnabled = false
                scope.launch { withContext(Dispatchers.IO) { Database.setSetting("always_on_enforcement", "false") } }
            }
        )
    }

    // ── Add schedule dialog ─────────────────────────────────────────────────────
    if (showAddSchedule) {
        AddScheduleDialogBD(
            onDismiss = { showAddSchedule = false },
            onSave    = { sched ->
                scope.launch {
                    withContext(Dispatchers.IO) { Database.upsertBlockSchedule(sched) }
                    BlockScheduleService.forceCheck()
                    reload()
                    showAddSchedule = false
                }
            }
        )
    }

    // ── Session PIN setup (PIN not previously set → toggle ON) ─────────────────
    if (showSessionPinSetup) {
        SessionPinSetupDialog(
            onDismiss = {
                showSessionPinSetup = false
                // Revert toggle if user cancelled without setting a PIN
                sessionPinSet = SessionPin.isSet()
            },
            onPinSet = { pin ->
                scope.launch {
                    withContext(Dispatchers.IO) { SessionPin.set(pin) }
                    sessionPinSet = true
                    showSessionPinSetup = false
                    liveHint = HintType.TIP to "Session PIN set — you'll need it to end sessions early."
                }
            }
        )
    }

    // ── Session PIN change/remove (PIN was set → toggled) ──────────────────────
    if (showSessionPinChange) {
        SessionPinChangeDialog(
            onDismiss = {
                showSessionPinChange = false
                // Revert toggle — user cancelled, PIN unchanged
                sessionPinSet = SessionPin.isSet()
            },
            onPinCleared = {
                scope.launch {
                    sessionPinSet = false
                    showSessionPinChange = false
                    liveHint = HintType.WARNING to "Session PIN removed — sessions can be ended without a PIN."
                }
            },
            onPinChanged = { pin ->
                scope.launch {
                    withContext(Dispatchers.IO) { SessionPin.set(pin) }
                    sessionPinSet = true
                    showSessionPinChange = false
                    liveHint = HintType.TIP to "Session PIN updated successfully."
                }
            }
        )
    }

    // ── VPN PIN gate ───────────────────────────────────────────────────────────
    if (showVpnPinGate) {
        PinGateDialog(
            title    = strings.defPinRequired,
            subtitle = "Enter your Global PIN to disable VPN Shield.",
            onDismiss = { showVpnPinGate = false },
            onVerified = {
                showVpnPinGate = false
                vpnEnabled = false
                scope.launch { withContext(Dispatchers.IO) { VpnBlocker.isEnabled = false } }
                liveHint = HintType.WARNING to "VPN Shield off. Users can now bypass blocks using a VPN."
            }
        )
    }

    // ── Global PIN deactivate gate (non-negotiable — PIN required) ────────────
    if (showGlobalPinDeactivate) {
        PinGateDialog(
            title    = "Deactivate Global PIN",
            subtitle = "Enter your PIN to deactivate protection. Your PIN hash is preserved — re-enable anytime.",
            onDismiss = { showGlobalPinDeactivate = false },
            onVerified = {
                showGlobalPinDeactivate = false
                scope.launch {
                    withContext(Dispatchers.IO) { GlobalPin.deactivate() }
                    globalPinActive = false
                    liveHint = HintType.WARNING to "Global PIN deactivated. Toggle back on to re-enable with the same PIN."
                }
            }
        )
    }

    // ── Global PIN first-time setup ───────────────────────────────────────────
    if (showGlobalPinSetup) {
        GlobalPinSetupDialog(onDismiss = {
            showGlobalPinSetup = false
            scope.launch {
                val active = withContext(Dispatchers.IO) { GlobalPin.isActive() }
                globalPinActive  = active
                globalPinHasHash = withContext(Dispatchers.IO) { GlobalPin.isSet() }
                if (active) liveHint = HintType.TIP to "Global PIN set — enforcement settings are now protected."
            }
        })
    }

    // ── Global PIN resume: keep same or set new ───────────────────────────────
    if (showGlobalPinResume) {
        GlobalPinResumeDialog(
            onDismiss = { showGlobalPinResume = false },
            onKeepSame = {
                showGlobalPinResume = false
                scope.launch {
                    withContext(Dispatchers.IO) { GlobalPin.reactivate() }
                    globalPinActive = true
                    liveHint = HintType.TIP to "Global PIN re-enabled — enforcement settings are protected."
                }
            },
            onSetNew = {
                showGlobalPinResume = false
                showGlobalPinVerifyOld = true
            },
            onForgot = {
                showGlobalPinResume = false
                scope.launch {
                    val hasName = withContext(Dispatchers.IO) {
                        Database.getSetting("user_name")?.isNotBlank() == true
                    }
                    if (hasName) {
                        withContext(Dispatchers.IO) { GlobalPin.resetWithoutPin() }
                        globalPinHasHash = false
                        showGlobalPinSetup = true
                    } else {
                        liveHint = HintType.WARNING to
                            "No profile name set. Go to Settings → Profile and add a name first to enable PIN recovery."
                    }
                }
            }
        )
    }

    // ── Verify old PIN before setting a new one ───────────────────────────────
    if (showGlobalPinVerifyOld) {
        PinGateDialog(
            title    = "Verify Old PIN",
            subtitle = "Enter your old PIN to confirm your identity, then you'll set a new one.",
            onDismiss = { showGlobalPinVerifyOld = false },
            onVerified = {
                showGlobalPinVerifyOld = false
                scope.launch {
                    withContext(Dispatchers.IO) { GlobalPin.resetWithoutPin() }
                    globalPinHasHash = false
                    showGlobalPinSetup = true
                }
            }
        )
    }
}

@Composable
private fun DefCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Surface2).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DefToggleRow(
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else { _ -> },
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Surface, checkedTrackColor = Purple80)
        )
    }
}

@Composable
private fun PinGateDialog(title: String, subtitle: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(22.dp))
                Text(title, color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = false },
                    label = { Text(LocalizationManager.strings.defPinLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = error,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error
                    )
                )
                if (error) Text(LocalizationManager.strings.defIncorrectPin, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { GlobalPin.verify(pin) }
                        if (ok) onVerified() else error = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text(LocalizationManager.strings.btnSave) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalizationManager.strings.btnCancel, color = OnSurface2) }
        }
    )
}

@Composable
private fun AddScheduleDialogBD(onDismiss: () -> Unit, onSave: (BlockSchedule) -> Unit) {
    val strings  = LocalizationManager.strings
    val days     = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var name     by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf("Mon", "Tue", "Wed", "Thu", "Fri")) }
    var startH   by remember { mutableStateOf("9") }
    var startM   by remember { mutableStateOf("0") }
    var endH     by remember { mutableStateOf("17") }
    var endM     by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = { Text(strings.defAddBlockSchedule, color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(strings.defScheduleName) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2)
                )
                Text(strings.defDaysLabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        FilterChip(
                            selected = d in selected,
                            onClick  = {
                                selected = if (d in selected) selected - d else selected + d
                            },
                            label    = { Text(d, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startH, onValueChange = { startH = it.filter(Char::isDigit).take(2) },
                        label = { Text(strings.defStartH) }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2))
                    OutlinedTextField(value = startM, onValueChange = { startM = it.filter(Char::isDigit).take(2) },
                        label = { Text(strings.defStartM) }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2))
                    OutlinedTextField(value = endH, onValueChange = { endH = it.filter(Char::isDigit).take(2) },
                        label = { Text(strings.defEndH) }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2))
                    OutlinedTextField(value = endM, onValueChange = { endM = it.filter(Char::isDigit).take(2) },
                        label = { Text(strings.defEndM) }, modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2))
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
            ) { Text(strings.btnSave) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.btnCancel, color = OnSurface2) } }
    )
}

// ── Session PIN setup dialog (no PIN set yet) ──────────────────────────────────
@Composable
private fun SessionPinSetupDialog(onDismiss: () -> Unit, onPinSet: (String) -> Unit) {
    val s          = LocalizationManager.strings
    var pin        by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin    by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Purple80, modifier = Modifier.size(22.dp))
                Text("Set Session PIN", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This PIN will be required to end a focus session early. Write it down — it's shown only once at session start.",
                    color = OnSurface2, style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { pin = it; error = "" },
                    label         = { Text("New PIN (min 8 characters)") },
                    singleLine    = true,
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon  = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = OnSurface2, modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    isError  = error.isNotEmpty(),
                    colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = confirmPin,
                    onValueChange = { confirmPin = it; error = "" },
                    label         = { Text("Confirm PIN") },
                    singleLine    = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError  = error.isNotEmpty(),
                    colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 8      -> error = "PIN must be at least 8 characters"
                        pin != confirmPin   -> error = "PINs do not match"
                        else               -> onPinSet(pin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80)
            ) { Text(s.btnSave) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.btnCancel, color = OnSurface2) }
        }
    )
}

// ── Session PIN change/remove dialog (PIN already set) ────────────────────────
private enum class PinChangeStep { CHOOSE, VERIFY_THEN_CHANGE, VERIFY_THEN_REMOVE }

@Composable
private fun SessionPinChangeDialog(
    onDismiss: () -> Unit,
    onPinCleared: () -> Unit,
    onPinChanged: (String) -> Unit
) {
    val s      = LocalizationManager.strings
    val scope  = rememberCoroutineScope()
    var step   by remember { mutableStateOf(PinChangeStep.CHOOSE) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin    by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(22.dp))
                Text(
                    when (step) {
                        PinChangeStep.CHOOSE            -> "Session PIN"
                        PinChangeStep.VERIFY_THEN_CHANGE -> "Change Session PIN"
                        PinChangeStep.VERIFY_THEN_REMOVE -> "Remove Session PIN"
                    },
                    color = OnSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    PinChangeStep.CHOOSE -> {
                        Text("A Session PIN is currently active. What would you like to do?",
                            color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            Triple(Icons.Default.Edit,   "Change PIN",  "Verify your current PIN, then set a new one."),
                            Triple(Icons.Default.LockOpen, "Remove PIN", "Verify your current PIN, then disable the lock.")
                        ).forEachIndexed { idx, (icon, label, sub) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface3)
                                    .clickable {
                                        step = if (idx == 0) PinChangeStep.VERIFY_THEN_CHANGE else PinChangeStep.VERIFY_THEN_REMOVE
                                        error = ""
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(icon, null, tint = Purple80, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(sub, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    PinChangeStep.VERIFY_THEN_CHANGE -> {
                        Text("Enter your current PIN to verify, then choose a new one.",
                            color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = oldPin, onValueChange = { oldPin = it; error = "" },
                            label = { Text("Current PIN") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = error.isNotEmpty() && newPin.isEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPin, onValueChange = { newPin = it; error = "" },
                            label = { Text("New PIN (min 8 characters)") }, singleLine = true,
                            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
                                }
                            },
                            isError = error.isNotEmpty() && newPin.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPin, onValueChange = { confirmPin = it; error = "" },
                            label = { Text("Confirm new PIN") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = error.isNotEmpty() && confirmPin.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) Text(error, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                    PinChangeStep.VERIFY_THEN_REMOVE -> {
                        Text("Enter your current PIN to confirm removal.",
                            color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = oldPin, onValueChange = { oldPin = it; error = "" },
                            label = { Text("Current PIN") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = error.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2, errorBorderColor = Error),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) Text(error, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                PinChangeStep.CHOOSE -> { /* no confirm button on choose step */ }
                PinChangeStep.VERIFY_THEN_CHANGE -> {
                    Button(
                        onClick = {
                            scope.launch {
                                val valid = withContext(Dispatchers.IO) { SessionPin.verify(oldPin) }
                                when {
                                    !valid             -> error = "Incorrect current PIN"
                                    newPin.length < 8  -> error = "New PIN must be at least 8 characters"
                                    newPin != confirmPin -> error = "New PINs do not match"
                                    else               -> onPinChanged(newPin)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) { Text("Change PIN") }
                }
                PinChangeStep.VERIFY_THEN_REMOVE -> {
                    Button(
                        onClick = {
                            scope.launch {
                                val cleared = withContext(Dispatchers.IO) { SessionPin.clear(oldPin) }
                                if (cleared) onPinCleared()
                                else error = "Incorrect PIN — removal denied"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Error)
                    ) { Text("Remove PIN") }
                }
            }
        },
        dismissButton = {
            if (step == PinChangeStep.CHOOSE) {
                TextButton(onClick = onDismiss) { Text(s.btnCancel, color = OnSurface2) }
            } else {
                TextButton(onClick = { step = PinChangeStep.CHOOSE; error = "" }) { Text(s.btnBack, color = OnSurface2) }
            }
        }
    )
}

// ── Global PIN resume dialog: keep same PIN or set new ─────────────────────────
@Composable
private fun GlobalPinResumeDialog(
    onDismiss:  () -> Unit,
    onKeepSame: () -> Unit,
    onSetNew:   () -> Unit,
    onForgot:   () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Security, null, tint = Purple80, modifier = Modifier.size(22.dp))
                Text("Re-enable Global PIN", color = OnSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your Global PIN was previously set. Would you like to keep the same PIN or set a new one?",
                    color = OnSurface2, style = MaterialTheme.typography.bodySmall
                )
                listOf(
                    Triple(Icons.Default.Check, "Keep same PIN",
                           "Re-enable with your existing PIN — nothing changes."),
                    Triple(Icons.Default.Edit,  "Set new PIN",
                           "Verify your old PIN first, then choose a new one.")
                ).forEachIndexed { idx, (icon, label, sub) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface3)
                            .clickable { if (idx == 0) onKeepSame() else onSetNew() }
                            .padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, null, tint = Purple80, modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                label,
                                color      = OnSurface,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(sub, color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = onForgot) {
                    Text(
                        "I don't have my old PIN",
                        color = Error.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}
