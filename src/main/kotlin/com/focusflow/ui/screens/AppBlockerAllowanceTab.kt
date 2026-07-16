package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.DailyAllowance
import com.focusflow.data.repository.BlockingRepository
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.ScannedApp
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.ui.components.FfVerticalScrollbar
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val allowanceOptions = listOf(
    15  to "15m",
    30  to "30m",
    45  to "45m",
    60  to "1h",
    90  to "1h 30m",
    120 to "2h",
    180 to "3h",
    240 to "4h"
)

// ── Daily Allowance Tab ────────────────────────────────────────────────────────

@Composable
internal fun DailyAllowanceTab() {
    val scope   = rememberCoroutineScope()
    val strings = LocalizationManager.strings

    var allowances  by remember { mutableStateOf(listOf<DailyAllowance>()) }
    var scannedApps by remember { mutableStateOf(listOf<ScannedApp>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var showPicker  by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<DailyAllowance?>(null) }
    var tick        by remember { mutableStateOf(0) }

    fun reload() {
        scope.launch {
            allowances  = withContext(Dispatchers.IO) { BlockingRepository.getDailyAllowances() }
            val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
            val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
            val runningNames = running.map { it.processName }.toSet()
            scannedApps = running + curated.filter { it.processName !in runningNames }
            isLoading   = false
            withContext(Dispatchers.IO) { DailyAllowanceTracker.reload() }
        }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    val blockedToday   = remember(tick) { DailyAllowanceTracker.blockedProcesses }
    val alreadyAllowed = remember(allowances) { allowances.map { it.processName.lowercase() }.toSet() }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Warning.copy(alpha = 0.08f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Timelapse, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Text(strings.blockerAllowanceDesc, style = MaterialTheme.typography.bodySmall, color = OnSurface2, modifier = Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.blockerAddDailyAllowance, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            if (isLoading) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Warning) } }
            } else if (allowances.isEmpty()) {
                item { EmptyAllowanceState() }
            } else {
                item {
                    Text("${allowances.size} app${if (allowances.size == 1) "" else "s"} with daily limits",
                        style = MaterialTheme.typography.titleSmall, color = OnSurface2, fontWeight = FontWeight.Medium)
                }
                items(allowances, key = { it.processName }) { allowance ->
                    val usedMinutes    = remember(tick) { DailyAllowanceTracker.getUsageMinutes(allowance.processName) }
                    val remaining      = remember(tick) { DailyAllowanceTracker.getRemainingMinutes(allowance) }
                    val isBlockedToday = allowance.processName.lowercase() in blockedToday
                    AllowanceCard(
                        allowance        = allowance,
                        usedMinutes      = usedMinutes,
                        remainingMinutes = remaining,
                        isBlockedToday   = isBlockedToday,
                        onEdit           = { editTarget = allowance },
                        onDelete         = {
                            scope.launch {
                                withContext(Dispatchers.IO) { BlockingRepository.deleteDailyAllowance(allowance.processName) }
                                withContext(Dispatchers.IO) { DailyAllowanceTracker.reload() }
                                reload()
                            }
                        }
                    )
                }
            }
        }
        FfVerticalScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    if (showPicker) {
        AllowancePickerDialog(
            scannedApps    = scannedApps,
            alreadyAllowed = alreadyAllowed,
            onDismiss      = { showPicker = false },
            onConfirm      = { processName, displayName, minutes ->
                scope.launch {
                    withContext(Dispatchers.IO) { BlockingRepository.upsertDailyAllowance(DailyAllowance(processName, displayName, minutes)) }
                    withContext(Dispatchers.IO) { DailyAllowanceTracker.reload() }
                    showPicker = false
                    reload()
                }
            }
        )
    }

    editTarget?.let { target ->
        EditAllowanceDialog(
            allowance = target,
            onDismiss = { editTarget = null },
            onSave    = { newMinutes ->
                scope.launch {
                    withContext(Dispatchers.IO) { BlockingRepository.upsertDailyAllowance(target.copy(allowanceMinutes = newMinutes)) }
                    withContext(Dispatchers.IO) { DailyAllowanceTracker.reload() }
                    editTarget = null
                    reload()
                }
            }
        )
    }
}

@Composable
private fun AllowanceCard(
    allowance:        DailyAllowance,
    usedMinutes:      Long,
    remainingMinutes: Long,
    isBlockedToday:   Boolean,
    onEdit:           () -> Unit,
    onDelete:         () -> Unit
) {
    val strings  = LocalizationManager.strings
    val progress = if (allowance.allowanceMinutes > 0)
        (usedMinutes.toFloat() / allowance.allowanceMinutes.toFloat()).coerceIn(0f, 1f) else 0f
    val barColor = when { isBlockedToday -> Error; progress > 0.8f -> Warning; else -> Success }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(when { isBlockedToday -> Error.copy(alpha = 0.06f); progress > 0.8f -> Warning.copy(alpha = 0.05f); else -> Surface2 })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(processName = allowance.processName, displayName = allowance.displayName, size = 42)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(allowance.displayName, color = OnSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isBlockedToday) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Error.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(strings.blockerBlockedUntilMidnight, style = MaterialTheme.typography.labelSmall, color = Error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(allowance.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit,   modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit,          null, tint = OnSurface2, modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, null, tint = OnSurface2, modifier = Modifier.size(16.dp)) }
        }
        LinearProgressIndicator(
            progress  = { progress },
            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = barColor, trackColor = Surface3
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMinutes(usedMinutes) + " used",                                    style = MaterialTheme.typography.labelSmall, color = barColor,   fontWeight = FontWeight.Medium)
            Text("${strings.blockerLimit} ${formatMinutes(allowance.allowanceMinutes.toLong())}", style = MaterialTheme.typography.labelSmall, color = OnSurface2)
            if (!isBlockedToday) Text(formatMinutes(remainingMinutes) + " left",          style = MaterialTheme.typography.labelSmall, color = OnSurface2)
        }
    }
}

internal fun formatMinutes(mins: Long): String {
    if (mins <= 0L) return "0m"
    val h = mins / 60; val m = mins % 60
    return when { h > 0 && m > 0 -> "${h}h ${m}m"; h > 0 -> "${h}h"; else -> "${m}m" }
}

@Composable
private fun EmptyAllowanceState() {
    val strings = LocalizationManager.strings
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Timelapse, null, tint = OnSurface2, modifier = Modifier.size(36.dp))
        }
        Text(strings.blockerNoDailyLimitsTitle, style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text(strings.blockerNoDailyLimitsBody, style = MaterialTheme.typography.bodySmall, color = OnSurface2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

// ── Allowance Picker Dialog ────────────────────────────────────────────────────

@Composable
private fun AllowancePickerDialog(
    scannedApps:    List<ScannedApp>,
    alreadyAllowed: Set<String>,
    onDismiss:      () -> Unit,
    onConfirm:      (processName: String, displayName: String, minutes: Int) -> Unit
) {
    val strings         = LocalizationManager.strings
    var step            by remember { mutableStateOf(0) }
    var pickedApp       by remember { mutableStateOf<ScannedApp?>(null) }
    var selectedMinutes by remember { mutableStateOf(60) }
    var customInput     by remember { mutableStateOf("") }
    var search          by remember { mutableStateOf("") }
    var showAll         by remember { mutableStateOf(false) }
    var manualExe       by remember { mutableStateOf("") }

    val runningApps = remember(scannedApps) { scannedApps.filter { it.isRunning } }
    val sourceList  = if (showAll) scannedApps else runningApps
    val filtered    = remember(search, sourceList) {
        if (search.isBlank()) sourceList
        else sourceList.filter { it.displayName.contains(search, ignoreCase = true) || it.processName.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(520.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Timelapse, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Text(
                        if (step == 0) strings.blockerChooseApp else "${strings.blockerSetDailyLimitFor} ${pickedApp?.displayName ?: ""}",
                        color = OnSurface, fontWeight = FontWeight.Bold
                    )
                }
                Text(if (step == 0) strings.blockerStep1 else strings.blockerStep2, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        },
        text = {
            if (step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text(strings.blockerSearchApps, color = OnSurface2) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Warning, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedTextColor = OnSurface, unfocusedTextColor = OnSurface)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !showAll, onClick = { showAll = false },
                            label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success)); Text("${strings.blockerRunning} (${runningApps.size})", style = MaterialTheme.typography.labelSmall) } },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Success.copy(alpha = 0.15f), selectedLabelColor = Success))
                        FilterChip(selected = showAll, onClick = { showAll = true },
                            label = { Text("${strings.blockerAllApps} (${scannedApps.size})", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Warning.copy(alpha = 0.15f), selectedLabelColor = Warning))
                    }
                    val pickerState = androidx.compose.foundation.lazy.rememberLazyListState()
                    Box(modifier = Modifier.height(280.dp)) {
                        LazyColumn(state = pickerState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Edit, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                                    OutlinedTextField(
                                        value = manualExe, onValueChange = { manualExe = it },
                                        placeholder = { Text(strings.blockerTypeName, color = OnSurface2, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f).height(46.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                            if (manualExe.isNotBlank()) { val proc = manualExe.trim().lowercase().let { if (it.endsWith(".exe")) it else "$it.exe" }; pickedApp = ScannedApp(proc, InstalledAppsScanner.friendlyNameFor(proc), false); step = 1 }
                                        }),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Warning, unfocusedBorderColor = OnSurface2.copy(alpha = 0.3f), focusedTextColor = OnSurface, unfocusedTextColor = OnSurface)
                                    )
                                    TextButton(onClick = { if (manualExe.isNotBlank()) { val proc = manualExe.trim().lowercase().let { if (it.endsWith(".exe")) it else "$it.exe" }; pickedApp = ScannedApp(proc, InstalledAppsScanner.friendlyNameFor(proc), false); step = 1 } }, enabled = manualExe.isNotBlank()) { Text(strings.blockerUseArrow, color = Warning) }
                                }
                            }
                            if (filtered.isEmpty()) {
                                item { Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(strings.blockerNoAppsFound, style = MaterialTheme.typography.bodySmall, color = OnSurface2, textAlign = TextAlign.Center) } }
                            } else {
                                itemsIndexed(filtered, key = { index, app -> "${app.processName}|$index" }) { _, app ->
                                    val isAlready = app.processName.lowercase() in alreadyAllowed
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(if (isAlready) Surface3.copy(alpha = 0.5f) else Surface3)
                                            .clickable(enabled = !isAlready) { pickedApp = app; step = 1 }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppIcon(app.processName, app.displayName, size = 34)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(app.displayName, color = if (isAlready) OnSurface2 else OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                if (app.isRunning) Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                                if (isAlready) Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Warning.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) { Text("has limit", style = MaterialTheme.typography.labelSmall, color = Warning) }
                                            }
                                            Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = if (isAlready) OnSurface2.copy(alpha = 0.3f) else OnSurface2, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        FfVerticalScrollbar(listState = pickerState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface3).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pickedApp?.let { app -> AppIcon(app.processName, app.displayName, size = 36)
                            Column { Text(app.displayName, color = OnSurface, fontWeight = FontWeight.SemiBold); Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2) }
                        }
                    }
                    Text(strings.blockerHowLongPerDay, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowanceOptions.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (mins, label) ->
                                    FilterChip(
                                        selected = selectedMinutes == mins && customInput.isBlank(),
                                        onClick  = { selectedMinutes = mins; customInput = "" },
                                        label    = { Text(label, fontWeight = if (selectedMinutes == mins && customInput.isBlank()) FontWeight.SemiBold else FontWeight.Normal) },
                                        modifier = Modifier.weight(1f),
                                        colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Warning.copy(alpha = 0.20f), selectedLabelColor = Warning)
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { raw -> customInput = raw; val p = raw.trim().toIntOrNull(); if (p != null && p in 1..1440) selectedMinutes = p },
                        label = { Text("Custom minutes (1–1440)", color = OnSurface2) },
                        placeholder = { Text("e.g. 75", color = OnSurface2.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = customInput.isNotBlank() && (customInput.trim().toIntOrNull()?.let { it in 1..1440 } != true),
                        supportingText = if (customInput.isNotBlank() && (customInput.trim().toIntOrNull()?.let { it in 1..1440 } != true)) { { Text("Enter a number between 1 and 1440", color = Error) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Warning, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedLabelColor = Warning, focusedTextColor = OnSurface, unfocusedTextColor = OnSurface)
                    )
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Warning.copy(alpha = 0.07f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(16.dp))
                        Text("${strings.blockerAfterLimit} ${formatMinutes(selectedMinutes.toLong())} ${strings.blockerWillBlockRest}", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(onClick = { pickedApp?.let { app -> onConfirm(app.processName, app.displayName, selectedMinutes) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f))) { Text(strings.blockerSetLimit) }
            }
        },
        dismissButton = {
            if (step == 1) {
                TextButton(onClick = { step = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(strings.btnBack, color = OnSurface2) }
            } else {
                TextButton(onClick = onDismiss) { Text(LocalizationManager.strings.btnCancel, color = OnSurface2) }
            }
        }
    )
}

@Composable
private fun EditAllowanceDialog(allowance: DailyAllowance, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val strings         = LocalizationManager.strings
    var selectedMinutes by remember { mutableStateOf(allowance.allowanceMinutes) }
    var customInput     by remember { mutableStateOf(if (allowanceOptions.any { it.first == allowance.allowanceMinutes }) "" else allowance.allowanceMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(420.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(allowance.processName, allowance.displayName, size = 36)
                Column { Text(strings.blockerEditDailyLimit, color = OnSurface, fontWeight = FontWeight.Bold); Text(allowance.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface2) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.blockerNewAllowance, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                allowanceOptions.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedMinutes == mins && customInput.isBlank(),
                                onClick  = { selectedMinutes = mins; customInput = "" },
                                label    = { Text(label, fontWeight = if (selectedMinutes == mins && customInput.isBlank()) FontWeight.SemiBold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Warning.copy(alpha = 0.20f), selectedLabelColor = Warning)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { raw -> customInput = raw; val p = raw.trim().toIntOrNull(); if (p != null && p in 1..1440) selectedMinutes = p },
                    label = { Text("Custom minutes (1–1440)", color = OnSurface2) },
                    placeholder = { Text("e.g. 75", color = OnSurface2.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = customInput.isNotBlank() && (customInput.trim().toIntOrNull()?.let { it in 1..1440 } != true),
                    supportingText = if (customInput.isNotBlank() && (customInput.trim().toIntOrNull()?.let { it in 1..1440 } != true)) { { Text("Enter a number between 1 and 1440", color = Error) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Warning, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedLabelColor = Warning, focusedTextColor = OnSurface, unfocusedTextColor = OnSurface)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedMinutes) },
                enabled = customInput.isBlank() || (customInput.trim().toIntOrNull()?.let { it in 1..1440 } == true),
                colors  = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f))) { Text(LocalizationManager.strings.btnSave) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizationManager.strings.btnCancel, color = OnSurface2) } }
    )
}
