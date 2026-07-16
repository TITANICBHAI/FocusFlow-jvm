package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.CustomBlockPreset
import com.focusflow.data.repository.BlockingRepository
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.ScannedApp
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.StandaloneBlockService
import com.focusflow.ui.components.FfVerticalScrollbar
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

// ── Timed Block Tab ────────────────────────────────────────────────────────────

@Composable
internal fun TimedBlockTab() {
    val strings         = LocalizationManager.strings
    val standaloneBlock by StandaloneBlockService.block.collectAsState()
    var scannedApps     by remember { mutableStateOf(listOf<ScannedApp>()) }
    var showPicker      by remember { mutableStateOf(false) }
    var selectedHours   by remember { mutableStateOf(1) }
    var selectedApps    by remember { mutableStateOf(setOf<String>()) }
    var isLoading       by remember { mutableStateOf(true) }
    var scheduleMode    by remember { mutableStateOf(0) }

    val todayDate = remember { LocalDate.now() }
    var startDate by remember { mutableStateOf(todayDate) }
    var startHour by remember { mutableStateOf(LocalTime.now().hour) }
    var startMin  by remember { mutableStateOf(0) }
    var endDate   by remember { mutableStateOf(todayDate) }
    var endHour   by remember { mutableStateOf((LocalTime.now().hour + 1).coerceAtMost(23)) }
    var endMin    by remember { mutableStateOf(0) }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(standaloneBlock) { while (true) { delay(1000); tick++ } }

    val isScheduled  = standaloneBlock != null && StandaloneBlockService.isScheduled
    val remainingMs  = StandaloneBlockService.remainingMs()
    val startsInMs   = StandaloneBlockService.startsInMs()
    val blockedNames = standaloneBlock?.processNames ?: emptyList()

    LaunchedEffect(Unit) {
        val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
        val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
        val runningNames = running.map { it.processName }.toSet()
        scannedApps = running + curated.filter { it.processName !in runningNames }
        isLoading = false
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Warning.copy(alpha = 0.08f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Timer, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Text(strings.blockerTimedWarning, style = MaterialTheme.typography.bodySmall, color = OnSurface2, modifier = Modifier.weight(1f))
                }
            }

            if (isScheduled) {
                item {
                    ActiveTimedBlock(
                        remainingMs  = remainingMs,
                        startsInMs   = startsInMs,
                        blockedNames = blockedNames,
                        onAddTime    = { StandaloneBlockService.addTime(it * 60_000L) }
                    )
                }
            } else {
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(strings.blockerConfigureTimedBlock, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface3).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(strings.blockerDuration, strings.blockerDateRange).forEachIndexed { idx, label ->
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (scheduleMode == idx) Purple80.copy(alpha = 0.20f) else Color.Transparent)
                                        .clickable { scheduleMode = idx }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (scheduleMode == idx) Purple80 else OnSurface2,
                                        fontWeight = if (scheduleMode == idx) FontWeight.SemiBold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (scheduleMode == 0) {
                            Text(strings.blockerDuration, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1 to "1h", 2 to "2h", 4 to "4h", 8 to "8h", 12 to "12h").forEach { (h, label) ->
                                    FilterChip(selected = selectedHours == h, onClick = { selectedHours = h }, label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple80.copy(alpha = 0.2f), selectedLabelColor = Purple80))
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.blockerStart, style = MaterialTheme.typography.labelMedium, color = OnSurface2, fontWeight = FontWeight.SemiBold)
                                DateTimePicker(date = startDate, hour = startHour, minute = startMin, accentColor = Purple80,
                                    onDateChange = { startDate = it }, onHourChange = { startHour = it }, onMinChange = { startMin = it })
                                HorizontalDivider(color = Surface3)
                                Text(strings.blockerEnd, style = MaterialTheme.typography.labelMedium, color = OnSurface2, fontWeight = FontWeight.SemiBold)
                                DateTimePicker(date = endDate, hour = endHour, minute = endMin, minDate = startDate, accentColor = Error,
                                    onDateChange = { endDate = it }, onHourChange = { endHour = it }, onMinChange = { endMin = it })
                                val startEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val endEpoch   = LocalDateTime.of(endDate,   LocalTime.of(endHour,   endMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val durationMins = (endEpoch - startEpoch) / 60_000L
                                if (durationMins > 0) {
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Purple80.copy(alpha = 0.07f)).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Schedule, null, tint = Purple80, modifier = Modifier.size(16.dp))
                                        Text("${strings.blockerBlockDuration} ${formatMinutes(durationMins)}", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                    }
                                } else if (durationMins <= 0) {
                                    Text(strings.blockerEndAfterStart, style = MaterialTheme.typography.bodySmall, color = Error)
                                }
                            }
                        }

                        HorizontalDivider(color = Surface3)
                        Text(strings.blockerAppsToBlock, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

                        if (selectedApps.isEmpty()) {
                            Text(strings.blockerNoAppsSelected, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedApps.forEach { proc ->
                                    val app = scannedApps.find { it.processName.equals(proc, ignoreCase = true) }
                                    val friendly = app?.displayName ?: InstalledAppsScanner.friendlyNameFor(proc)
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AppIcon(processName = proc, displayName = friendly, size = 32)
                                        Text(friendly, color = OnSurface, modifier = Modifier.weight(1f))
                                        Text(proc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                        IconButton(onClick = { selectedApps = selectedApps - proc }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, null, tint = OnSurface2, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple80)) {
                            Icon(Icons.Default.Apps, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (selectedApps.isEmpty()) strings.blockerPickApps else strings.blockerChangeAppSelection)
                        }

                        val canStart = selectedApps.isNotEmpty() && run {
                            if (scheduleMode == 1) {
                                val sE = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val eE = LocalDateTime.of(endDate,   LocalTime.of(endHour,   endMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                eE > sE
                            } else true
                        }

                        Button(
                            onClick = {
                                if (!canStart) return@Button
                                if (scheduleMode == 0) {
                                    StandaloneBlockService.start(selectedApps.toList(), selectedHours * 3_600_000L)
                                } else {
                                    val sE = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val eE = LocalDateTime.of(endDate,   LocalTime.of(endHour,   endMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val now = System.currentTimeMillis()
                                    StandaloneBlockService.start(selectedApps.toList(), eE - maxOf(sE, now), if (sE > now) sE else null)
                                }
                            },
                            enabled = canStart, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f)), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (scheduleMode == 1 && run { val sE = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); sE > System.currentTimeMillis() })
                                    Icons.Default.Schedule else Icons.Default.Block,
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            val appWord = if (selectedApps.size == 1) strings.blockerApp else strings.blockerApps
                            val label = if (scheduleMode == 0) {
                                "${strings.blockerStartHourBlockFmt.format(selectedHours)} (${selectedApps.size} $appWord)"
                            } else {
                                val sE = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                if (sE > System.currentTimeMillis()) "${strings.blockerScheduleBlockFmt} (${selectedApps.size} $appWord)"
                                else "${strings.blockerStartBlockNowFmt} (${selectedApps.size} $appWord)"
                            }
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        FfVerticalScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    if (showPicker) {
        AppPickerDialog(
            scannedApps       = scannedApps,
            alreadyBlocked    = emptySet(),
            title             = strings.blockerPickTimedTitle,
            confirmLabel      = strings.blockerSelectApps,
            confirmColor      = Error,
            showNetworkToggle = false,
            preSelected       = selectedApps,
            onDismiss         = { showPicker = false },
            onConfirm         = { picked, _ -> selectedApps = picked.map { it.processName }.toSet(); showPicker = false }
        )
    }
}

@Composable
private fun DateTimePicker(
    date: LocalDate, hour: Int, minute: Int, accentColor: Color,
    minDate: LocalDate = LocalDate.now(),
    onDateChange: (LocalDate) -> Unit, onHourChange: (Int) -> Unit, onMinChange: (Int) -> Unit
) {
    val strings = LocalizationManager.strings
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface3).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpinnerField("${date.dayOfMonth}", strings.blockerDay, { onDateChange(maxOf(date.minusDays(1), minDate)) }, { onDateChange(date.plusDays(1)) }, accentColor, Modifier.weight(1.2f))
        Text("/", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        SpinnerField("%02d".format(date.monthValue), strings.blockerMonth, { onDateChange(maxOf(date.minusMonths(1).withDayOfMonth(1).also { if (it < minDate) return@SpinnerField }, minDate)) }, { onDateChange(date.plusMonths(1).withDayOfMonth(minOf(date.dayOfMonth, date.plusMonths(1).lengthOfMonth()))) }, accentColor, Modifier.weight(1.2f))
        Text("/", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        SpinnerField("${date.year}", strings.blockerYear, { onDateChange(maxOf(date.minusYears(1), minDate)) }, { onDateChange(date.plusYears(1)) }, accentColor, Modifier.weight(1.6f))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Schedule, null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        SpinnerField("%02d".format(hour),   strings.blockerHour,   { onHourChange((hour - 1 + 24) % 24) }, { onHourChange((hour + 1) % 24) }, accentColor, Modifier.weight(1.2f))
        Text(":", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        SpinnerField("%02d".format(minute), strings.blockerMinute, { onMinChange((minute - 15 + 60) % 60) }, { onMinChange((minute + 15) % 60) }, accentColor, Modifier.weight(1.2f))
    }
}

@Composable
private fun SpinnerField(value: String, label: String, onDec: () -> Unit, onInc: () -> Unit, accentColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        IconButton(onClick = onInc, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.KeyboardArrowUp, null, tint = accentColor, modifier = Modifier.size(16.dp)) }
        Text(value, color = OnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(label, color = OnSurface2, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, textAlign = TextAlign.Center)
        IconButton(onClick = onDec, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = accentColor, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ActiveTimedBlock(remainingMs: Long, startsInMs: Long, blockedNames: List<String>, onAddTime: (Int) -> Unit) {
    val strings     = LocalizationManager.strings
    val isWaiting   = startsInMs > 0L
    val accentColor = if (isWaiting) Warning else Error
    val remSec = ((if (isWaiting) startsInMs else remainingMs) / 1000).toInt().coerceAtLeast(0)
    val h = remSec / 3600; val m = (remSec % 3600) / 60; val s = remSec % 60

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.07f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentColor))
            Text(if (isWaiting) strings.blockerBlockScheduled else strings.blockerBlockActive, style = MaterialTheme.typography.titleMedium, color = accentColor, fontWeight = FontWeight.Bold)
        }
        Text(
            if (isWaiting) { if (h > 0) "${strings.blockerStartsIn} ${h}h ${m}m ${s}s" else "${strings.blockerStartsIn} ${m}m ${s}s" }
            else           { if (h > 0) "${h}h ${m}m ${s}s ${strings.dashRemaining}" else "${m}m ${s}s ${strings.dashRemaining}" },
            style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold
        )
        if (isWaiting) {
            val blockDurSec = (remainingMs / 1000).toInt().coerceAtLeast(0); val bh = blockDurSec / 3600; val bm = (blockDurSec % 3600) / 60
            Text("${strings.blockerBlockDuration} " + if (bh > 0) "${bh}h ${bm}m" else "${bm}m", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blockedNames.forEach { proc ->
                val display = InstalledAppsScanner.friendlyNameFor(proc)
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(processName = proc, displayName = display, size = 28)
                    Text(display, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    Text(proc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }
        if (!isWaiting) {
            HorizontalDivider(color = Surface3)
            Text(strings.focusExtendBlock, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "+30m", 60 to "+1h", 120 to "+2h", 240 to "+4h").forEach { (mins, label) ->
                    OutlinedButton(onClick = { onAddTime(mins) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)) { Text(label, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ── App Picker Dialog ──────────────────────────────────────────────────────────

@Composable
internal fun AppPickerDialog(
    scannedApps:       List<ScannedApp>,
    alreadyBlocked:    Set<String>,
    title:             String,
    confirmLabel:      String,
    confirmColor:      Color,
    showNetworkToggle: Boolean,
    showPresets:       Boolean = false,
    preSelected:       Set<String> = emptySet(),
    onDismiss:         () -> Unit,
    onConfirm:         (List<ScannedApp>, Map<String, Boolean>) -> Unit
) {
    val strings                = LocalizationManager.strings
    val scope                  = rememberCoroutineScope()
    var search                 by remember { mutableStateOf("") }
    var selected               by remember { mutableStateOf(preSelected) }
    var networkBlock           by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showAll                by remember { mutableStateOf(false) }
    var presetsExpanded        by remember { mutableStateOf(false) }
    var customPresets          by remember { mutableStateOf(emptyList<CustomBlockPreset>()) }
    var showCreatePresetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = BlockingRepository.getCustomBlockPresets()
            withContext(Dispatchers.Main) { customPresets = loaded }
        }
    }

    val runningApps = remember(scannedApps) { scannedApps.filter { it.isRunning } }
    val sourceList  = if (showAll) scannedApps else runningApps
    val filtered    = remember(search, sourceList) {
        if (search.isBlank()) sourceList
        else sourceList.filter { it.displayName.contains(search, ignoreCase = true) || it.processName.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(540.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(strings.blockerSearchApps, color = OnSurface2) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = confirmColor, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedTextColor = OnSurface, unfocusedTextColor = OnSurface))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !showAll, onClick = { showAll = false },
                            label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success)); Text("${strings.blockerRunning} (${runningApps.size})", style = MaterialTheme.typography.labelSmall) } },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Success.copy(alpha = 0.15f), selectedLabelColor = Success))
                        FilterChip(selected = showAll, onClick = { showAll = true },
                            label = { Text("${strings.blockerAllApps} (${scannedApps.size})", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple80.copy(alpha = 0.15f), selectedLabelColor = Purple80))
                    }
                    if (selected.isNotEmpty()) Text("${selected.size} selected", style = MaterialTheme.typography.bodySmall, color = Purple80, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            val pickerListState = rememberLazyListState()
            Box(modifier = Modifier.height(360.dp)) {
                LazyColumn(state = pickerListState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showPresets) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3)
                                    .clickable { presetsExpanded = !presetsExpanded }.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.blockerMyPresets, color = Purple80, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (customPresets.isNotEmpty()) Text("(${customPresets.size})", style = MaterialTheme.typography.labelSmall, color = OnSurface2)
                                }
                                Icon(if (presetsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (presetsExpanded) {
                            if (customPresets.isEmpty()) {
                                item { Text(strings.blockerNoPresets, style = MaterialTheme.typography.bodySmall, color = OnSurface2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                            } else {
                                items(customPresets, key = { "preset_${it.id}" }) { preset ->
                                    val presetProcs = preset.processNames.toSet()
                                    val allSel = presetProcs.isNotEmpty() && presetProcs.all { proc -> selected.any { it.equals(proc, ignoreCase = true) } }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(if (allSel) Purple80.copy(alpha = 0.10f) else Surface3)
                                            .clickable { selected = if (allSel) selected.filter { sel -> presetProcs.none { it.equals(sel, ignoreCase = true) } }.toSet() else selected + presetProcs }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(preset.emoji, fontSize = 16.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.name, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text("${preset.processNames.size} app${if (preset.processNames.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = OnSurface2)
                                        }
                                        if (allSel) Icon(Icons.Default.CheckCircle, null, tint = Purple80, modifier = Modifier.size(15.dp))
                                        IconButton(onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                BlockingRepository.deleteCustomBlockPreset(preset.id)
                                                val updated = BlockingRepository.getCustomBlockPresets()
                                                withContext(Dispatchers.Main) { customPresets = updated }
                                            }
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, null, tint = OnSurface2.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            item {
                                OutlinedButton(onClick = { showCreatePresetDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple80)) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                    Text(if (selected.isEmpty()) strings.blockerCreatePreset else strings.blockerSaveAsPreset, fontSize = 13.sp)
                                }
                            }
                        }
                        item {
                            HorizontalDivider(color = Surface3, modifier = Modifier.padding(vertical = 6.dp))
                            Text(strings.blockerAppsLabel, style = MaterialTheme.typography.labelSmall, color = OnSurface2, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.SearchOff, null, tint = OnSurface2, modifier = Modifier.size(32.dp))
                                    Text(if (!showAll) strings.blockerNoRunningApps else "${strings.blockerNoAppsMatch} \"$search\"", style = MaterialTheme.typography.bodySmall, color = OnSurface2, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(filtered, key = { index, app -> "${app.processName}|$index" }) { _, app ->
                            val isSelected = app.processName in selected
                            val isAlready  = app.processName.lowercase() in alreadyBlocked
                            val netEnabled = networkBlock[app.processName] ?: false
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(when { isAlready -> Surface3.copy(alpha = 0.5f); isSelected -> confirmColor.copy(alpha = 0.10f); else -> Surface3 })
                                    .clickable(enabled = !isAlready) { selected = if (isSelected) selected - app.processName else selected + app.processName }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIcon(app.processName, app.displayName, size = 36)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(app.displayName, color = if (isAlready) OnSurface2 else OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        if (app.isRunning) Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                        if (isAlready) Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(OnSurface2.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) { Text("blocked", style = MaterialTheme.typography.labelSmall, color = OnSurface2) }
                                    }
                                    Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                                }
                                if (showNetworkToggle && isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.WifiOff, null, tint = if (netEnabled) Warning else OnSurface2, modifier = Modifier.size(14.dp))
                                        Switch(checked = netEnabled, onCheckedChange = { networkBlock = networkBlock + (app.processName to it) },
                                            modifier = Modifier.scale(0.52f).height(18.dp),
                                            colors = SwitchDefaults.colors(checkedTrackColor = Warning.copy(alpha = 0.4f), checkedThumbColor = Warning))
                                    }
                                }
                                Checkbox(checked = isSelected || isAlready, onCheckedChange = null, enabled = !isAlready,
                                    colors = CheckboxDefaults.colors(checkedColor = if (isAlready) OnSurface2 else confirmColor))
                            }
                        }
                    }
                }
                FfVerticalScrollbar(listState = pickerListState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        },
        confirmButton = {
            Button(onClick = { val picked = scannedApps.filter { it.processName in selected && it.processName.lowercase() !in alreadyBlocked }; onConfirm(picked, networkBlock) },
                enabled = selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = confirmColor)) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizationManager.strings.btnCancel, color = OnSurface2) } }
    )

    if (showCreatePresetDialog) {
        var newPresetName  by remember { mutableStateOf("") }
        var newPresetEmoji by remember { mutableStateOf("🚫") }
        AlertDialog(
            onDismissRequest = { showCreatePresetDialog = false },
            containerColor   = Surface2,
            modifier         = Modifier.width(400.dp),
            title = { Text(strings.blockerSavePresetBtn, color = OnSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (selected.isEmpty()) strings.blockerSelectAppsFirst else "${selected.size} app${if (selected.size != 1) "s" else ""} will be saved to this preset.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newPresetEmoji, onValueChange = { if (it.length <= 2) newPresetEmoji = it }, modifier = Modifier.width(64.dp), singleLine = true,
                            label = { Text(strings.blockerIconLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Purple80, unfocusedLabelColor = OnSurface2))
                        OutlinedTextField(value = newPresetName, onValueChange = { newPresetName = it }, placeholder = { Text(strings.blockerPresetName, color = OnSurface2) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple80, unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f), focusedTextColor = OnSurface, unfocusedTextColor = OnSurface))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = newPresetName.trim()
                    if (name.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val preset = CustomBlockPreset(UUID.randomUUID().toString(), name, newPresetEmoji.trim().ifBlank { "🚫" }, selected.toList())
                            BlockingRepository.upsertCustomBlockPreset(preset)
                            val updated = BlockingRepository.getCustomBlockPresets()
                            withContext(Dispatchers.Main) { customPresets = updated; showCreatePresetDialog = false; presetsExpanded = true }
                        }
                    }
                }, enabled = newPresetName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Purple80)) { Text(LocalizationManager.strings.btnSave) }
            },
            dismissButton = { TextButton(onClick = { showCreatePresetDialog = false }) { Text(LocalizationManager.strings.btnCancel, color = OnSurface2) } }
        )
    }
}
