package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.BlockRule
import com.focusflow.data.repository.BlockingRepository
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.ScannedApp
import com.focusflow.i18n.LocalizationManager
import com.focusflow.ui.components.EmptyStateCard
import com.focusflow.ui.components.FfVerticalScrollbar
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ── Always Block Tab ───────────────────────────────────────────────────────────

@Composable
internal fun AlwaysBlockTab() {
    val scope   = rememberCoroutineScope()
    val strings = LocalizationManager.strings

    var blockRules    by remember { mutableStateOf(listOf<BlockRule>()) }
    var scannedApps   by remember { mutableStateOf(listOf<ScannedApp>()) }
    var isLoading     by remember { mutableStateOf(true) }
    var showPicker    by remember { mutableStateOf(false) }
    var manualEntry   by remember { mutableStateOf("") }
    var manualError   by remember { mutableStateOf<String?>(null) }
    var searchQuery   by remember { mutableStateOf("") }
    var showAllInline by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val rules   = withContext(Dispatchers.IO) { BlockingRepository.getBlockRules() }
            val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
            val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
            val runningNames = running.map { it.processName }.toSet()
            blockRules  = rules
            scannedApps = running + curated.filter { it.processName !in runningNames }
            isLoading   = false
        }
    }

    fun addManual(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) { manualError = "Enter a process name (e.g. chrome.exe)"; return }
        val proc = trimmed.lowercase().let { if (it.endsWith(".exe")) it else "$it.exe" }
        if (proc == ".exe" || proc.length <= 4) { manualError = "Name must end in .exe (e.g. chrome.exe)"; return }
        if (blockRules.any { it.processName.equals(proc, ignoreCase = true) }) {
            manualError = "\"$proc\" is already in your block list"; return
        }
        manualError = null
        scope.launch {
            withContext(Dispatchers.IO) {
                BlockingRepository.upsertBlockRule(
                    BlockRule(
                        id           = UUID.randomUUID().toString(),
                        processName  = proc,
                        displayName  = InstalledAppsScanner.friendlyNameFor(proc),
                        enabled      = true,
                        blockNetwork = false
                    )
                )
            }
            manualEntry = ""
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filteredRules = remember(searchQuery, blockRules) {
        if (searchQuery.isBlank()) blockRules
        else blockRules.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.processName.contains(searchQuery, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Info banner ──────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Purple80.copy(alpha = 0.10f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = Purple80, modifier = Modifier.size(20.dp))
                    Text(
                        strings.blockerAlwaysOnDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Inline apps ──────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Apps, null, tint = OnSurface2, modifier = Modifier.size(14.dp))
                            Text(
                                if (showAllInline) strings.blockerAllApps else strings.blockerRunningNow,
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("· tap + to block", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        }
                        TextButton(
                            onClick = { showAllInline = !showAllInline },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (showAllInline) strings.blockerRunningOnly else strings.blockerShowAll,
                                color = Purple80,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Purple80)
                        }
                    } else {
                        val displayList = if (showAllInline) scannedApps
                            else scannedApps.filter { it.isRunning }.take(10)
                                .ifEmpty { scannedApps.take(10) }
                        if (displayList.isEmpty()) {
                            Text(strings.blockerNoAppsDetected, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        } else {
                            displayList.forEach { app ->
                                val alreadyInList = blockRules.any { it.processName.equals(app.processName, ignoreCase = true) }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (alreadyInList) Surface3.copy(alpha = 0.5f) else Surface3)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIcon(app.processName, app.displayName, size = 30)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(
                                                app.displayName,
                                                color = if (alreadyInList) OnSurface2 else OnSurface,
                                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                                            )
                                            if (app.isRunning) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Success))
                                            }
                                        }
                                        Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                                    }
                                    if (alreadyInList) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Purple80.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("blocked", style = MaterialTheme.typography.labelSmall, color = Purple80)
                                        }
                                    } else {
                                        IconButton(onClick = { addManual(app.processName) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Add, null, tint = Purple80, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Manual entry ─────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Edit, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
                        Text(strings.blockerManualEntry, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text(strings.blockerManualEntryHint, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualEntry,
                            onValueChange = { manualEntry = it; manualError = null },
                            placeholder = { Text("e.g. discord.exe", color = OnSurface2) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = manualError != null,
                            supportingText = manualError?.let { err -> { Text(err, color = Error) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addManual(manualEntry) }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Purple80,
                                unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f),
                                focusedTextColor     = OnSurface,
                                unfocusedTextColor   = OnSurface,
                                errorBorderColor     = Error
                            )
                        )
                        Button(
                            onClick = { addManual(manualEntry) },
                            enabled = manualEntry.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.blockerBlock, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Rules list ───────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple80)
                    }
                }
            } else if (blockRules.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon        = Icons.Default.Block,
                        title       = strings.blockerNoAppsBlockedTitle,
                        message     = strings.blockerNoAppsBlockedBody,
                        actionLabel = strings.blockerPickFromList,
                        onAction    = { showPicker = true },
                        modifier    = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${blockRules.size} app${if (blockRules.size == 1) "" else "s"} permanently blocked",
                            style = MaterialTheme.typography.titleSmall,
                            color = OnSurface2, fontWeight = FontWeight.Medium
                        )
                        if (blockRules.size > 4) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(strings.blockerSearchPlaceholder, color = OnSurface2, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (searchQuery.isNotBlank()) {{
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, null, tint = OnSurface2, modifier = Modifier.size(14.dp))
                                    }
                                }} else null,
                                modifier = Modifier.width(200.dp).height(46.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Purple80,
                                    unfocusedBorderColor = OnSurface2.copy(alpha = 0.3f),
                                    focusedTextColor     = OnSurface,
                                    unfocusedTextColor   = OnSurface
                                )
                            )
                        }
                    }
                }

                if (filteredRules.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("${strings.blockerNoAppsMatch} \"$searchQuery\"", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        }
                    }
                } else {
                    items(filteredRules, key = { it.id }) { rule ->
                        BlockRuleCard(
                            rule     = rule,
                            onToggle = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { BlockingRepository.upsertBlockRule(rule.copy(enabled = enabled)) }
                                    if (!enabled) NetworkBlocker.removeRule(rule.processName)
                                    reload()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { BlockingRepository.deleteBlockRule(rule.id) }
                                    NetworkBlocker.removeRule(rule.processName)
                                    reload()
                                }
                            }
                        )
                    }
                }
            }
        }

        FfVerticalScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    if (showPicker) {
        AppPickerDialog(
            scannedApps       = scannedApps,
            alreadyBlocked    = blockRules.map { it.processName.lowercase() }.toSet(),
            title             = strings.blockerPickAlwaysTitle,
            confirmLabel      = strings.blockerBlockSelected,
            confirmColor      = Purple80,
            showNetworkToggle = true,
            showPresets       = true,
            onDismiss         = { showPicker = false },
            onConfirm         = { picked, networkMap ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        picked.forEach { app ->
                            BlockingRepository.upsertBlockRule(
                                BlockRule(
                                    id           = UUID.randomUUID().toString(),
                                    processName  = app.processName.lowercase(),
                                    displayName  = app.displayName,
                                    enabled      = true,
                                    blockNetwork = networkMap[app.processName] ?: false
                                )
                            )
                        }
                    }
                    showPicker = false
                    reload()
                }
            }
        )
    }
}

@Composable
private fun BlockRuleCard(rule: BlockRule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val strings = LocalizationManager.strings
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(processName = rule.processName, displayName = rule.displayName, size = 40)
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.displayName, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rule.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (rule.blockNetwork) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Warning.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text(strings.blockerNetworkBadge, style = MaterialTheme.typography.labelSmall, color = Warning)
                    }
                }
                if (!rule.enabled) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(OnSurface2.copy(alpha = 0.10f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text(strings.blockerPausedBadge, style = MaterialTheme.typography.labelSmall, color = OnSurface2)
                    }
                }
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Purple80, checkedTrackColor = Purple80.copy(alpha = 0.35f))
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.DeleteOutline, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyBlockState() {
    val strings = LocalizationManager.strings
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Block, null, tint = OnSurface2, modifier = Modifier.size(36.dp))
        }
        Text(strings.blockerNoAppsBlockedTitle, style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text(strings.blockerNoAppsBlockedBody, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
}
