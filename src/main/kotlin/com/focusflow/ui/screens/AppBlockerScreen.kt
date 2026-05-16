package com.focusflow.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.BlockRule
import com.focusflow.data.models.DailyAllowance
import com.focusflow.enforcement.BlockPresets
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.ScannedApp
import com.focusflow.services.DailyAllowanceTracker
import com.focusflow.services.StandaloneBlockService
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

// ── Brand colors for known apps ────────────────────────────────────────────────

private val appBrandColors = mapOf(
    "chrome.exe"            to Color(0xFF4285F4),
    "firefox.exe"           to Color(0xFFFF6611),
    "msedge.exe"            to Color(0xFF0078D7),
    "opera.exe"             to Color(0xFFCC1A22),
    "brave.exe"             to Color(0xFFFF3800),
    "discord.exe"           to Color(0xFF5865F2),
    "slack.exe"             to Color(0xFF4A154B),
    "teams.exe"             to Color(0xFF6264A7),
    "zoom.exe"              to Color(0xFF2196F3),
    "telegram.exe"          to Color(0xFF2AABEE),
    "whatsapp.exe"          to Color(0xFF25D366),
    "signal.exe"            to Color(0xFF3A76F0),
    "spotify.exe"           to Color(0xFF1DB954),
    "steam.exe"             to Color(0xFF1B2838),
    "epicgameslauncher.exe" to Color(0xFF2C2C2C),
    "origin.exe"            to Color(0xFFF56C2D),
    "battle.net.exe"        to Color(0xFF148EFF),
    "leagueclient.exe"      to Color(0xFFC89B3C),
    "twitch.exe"            to Color(0xFF9147FF),
    "obs64.exe"             to Color(0xFF302E31),
    "tiktok.exe"            to Color(0xFF010101),
    "netflix.exe"           to Color(0xFFE50914),
    "vlc.exe"               to Color(0xFFFF8800),
    "wmplayer.exe"          to Color(0xFF005A9E),
    "outlook.exe"           to Color(0xFF0078D4),
    "winword.exe"           to Color(0xFF2B579A),
    "excel.exe"             to Color(0xFF217346),
    "powerpnt.exe"          to Color(0xFFB7472A),
    "notepad++.exe"         to Color(0xFF81BF43),
    "code.exe"              to Color(0xFF007ACC),
    "devenv.exe"            to Color(0xFF68217A),
    "idea64.exe"            to Color(0xFFFF318C),
    "pycharm64.exe"         to Color(0xFF21D789),
    "webstorm64.exe"        to Color(0xFF00CDD7),
    "studio64.exe"          to Color(0xFF3DDC84)
)

@Composable
fun AppIcon(processName: String, displayName: String, size: Int = 38) {
    val key   = processName.lowercase()
    val brand = appBrandColors[key]
    val color = brand ?: Purple80.copy(alpha = 0.7f)
    val letter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = color,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppBlockerScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Always Block", "Block for Time", "Daily Allowance")
    val tabIcons = listOf(
        Icons.Default.Block,
        Icons.Default.Timer,
        Icons.Default.Timelapse
    )

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface2)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Block, null, tint = Purple80, modifier = Modifier.size(28.dp))
            Column {
                Text(
                    "App Blocker",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Block distracting apps — permanently, for a time, or with daily limits",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Surface2,
            contentColor     = Purple80,
            edgePadding      = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == index) Purple80 else OnSurface2
                        )
                    },
                    icon = {
                        Icon(
                            tabIcons[index], null,
                            tint = if (selectedTab == index) Purple80 else OnSurface2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> AlwaysBlockTab()
            1 -> TimedBlockTab()
            2 -> DailyAllowanceTab()
        }
    }
}

// ── Always Block Tab ───────────────────────────────────────────────────────────

@Composable
private fun AlwaysBlockTab() {
    val scope = rememberCoroutineScope()

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
            val rules   = withContext(Dispatchers.IO) { Database.getBlockRules() }
            val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
            val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
            val runningNames = running.map { it.processName }.toSet()
            blockRules  = rules
            scannedApps = running + curated.filter { it.processName !in runningNames }
            isLoading   = false
        }
    }

    fun addManual(raw: String) {
        val proc = raw.trim().lowercase().let { if (it.endsWith(".exe")) it else "$it.exe" }
        if (proc.length < 5) { manualError = "Enter a valid process name"; return }
        if (blockRules.any { it.processName.equals(proc, ignoreCase = true) }) {
            manualError = "$proc is already in your block list"; return
        }
        manualError = null
        scope.launch {
            withContext(Dispatchers.IO) {
                Database.upsertBlockRule(
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
                        "Apps here are killed immediately when detected — during sessions AND when Always-On enforcement is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Add buttons row ──────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick from List", fontWeight = FontWeight.SemiBold)
                    }
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
                                if (showAllInline) "All Apps" else "Running Now",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = { showAllInline = !showAllInline },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (showAllInline) "Running only" else "Show all",
                                color = Purple80,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Purple80
                            )
                        }
                    } else {
                        val displayList = if (showAllInline) scannedApps
                            else scannedApps.filter { it.isRunning }.take(10)
                                .ifEmpty { scannedApps.take(10) }
                        if (displayList.isEmpty()) {
                            Text(
                                "No apps detected. Try \"Show all\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface2
                            )
                        } else {
                            displayList.forEach { app ->
                                val alreadyInList = blockRules.any {
                                    it.processName.equals(app.processName, ignoreCase = true)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (alreadyInList) Surface3.copy(alpha = 0.5f) else Surface3
                                        )
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIcon(app.processName, app.displayName, size = 30)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                app.displayName,
                                                color = if (alreadyInList) OnSurface2 else OnSurface,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (app.isRunning) {
                                                Box(
                                                    modifier = Modifier.size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(Success)
                                                )
                                            }
                                        }
                                        Text(
                                            app.processName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurface2,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (alreadyInList) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Purple80.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "blocked",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Purple80
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { addManual(app.processName) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add, null,
                                                tint = Purple80,
                                                modifier = Modifier.size(16.dp)
                                            )
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
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit, null,
                            tint = OnSurface2, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Manual entry",
                            style = MaterialTheme.typography.titleSmall,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Know the .exe name? Type it directly — useful for apps not shown in the picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            Text("Block", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Rules list ───────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Purple80) }
                }
            } else if (blockRules.isEmpty()) {
                item { EmptyBlockState() }
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
                            color = OnSurface2,
                            fontWeight = FontWeight.Medium
                        )
                        if (blockRules.size > 4) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search…", color = OnSurface2, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search, null,
                                        tint = OnSurface2, modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = if (searchQuery.isNotBlank()) {
                                    {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close, null,
                                                tint = OnSurface2, modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                } else null,
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
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No apps match \"$searchQuery\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface2
                            )
                        }
                    }
                } else {
                    items(filteredRules, key = { it.id }) { rule ->
                        BlockRuleCard(
                            rule = rule,
                            onToggle = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        Database.upsertBlockRule(rule.copy(enabled = enabled))
                                    }
                                    if (!enabled) NetworkBlocker.removeRule(rule.processName)
                                    reload()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Database.deleteBlockRule(rule.id) }
                                    NetworkBlocker.removeRule(rule.processName)
                                    reload()
                                }
                            }
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter  = rememberScrollbarAdapter(listState)
        )
    }

    if (showPicker) {
        AppPickerDialog(
            scannedApps       = scannedApps,
            alreadyBlocked    = blockRules.map { it.processName.lowercase() }.toSet(),
            title             = "Pick Apps to Always Block",
            confirmLabel      = "Block Selected",
            confirmColor      = Purple80,
            showNetworkToggle = true,
            showPresets       = true,
            onDismiss = { showPicker = false },
            onConfirm = { picked, networkMap ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        picked.forEach { app ->
                            Database.upsertBlockRule(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    rule.processName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rule.blockNetwork) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Warning.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("+ network", style = MaterialTheme.typography.labelSmall, color = Warning)
                    }
                }
                if (!rule.enabled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(OnSurface2.copy(alpha = 0.10f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("paused", style = MaterialTheme.typography.labelSmall, color = OnSurface2)
                    }
                }
            }
        }

        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Purple80,
                checkedTrackColor = Purple80.copy(alpha = 0.35f)
            )
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.DeleteOutline, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyBlockState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Block, null, tint = OnSurface2, modifier = Modifier.size(36.dp))
        }
        Text("No apps blocked yet", style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text(
            "Pick from the list above or type a .exe name to add your first block rule.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )
    }
}

// ── Daily Allowance Tab ────────────────────────────────────────────────────────

private val allowanceOptions = listOf(
    15  to "15m",
    30  to "30m",
    45  to "45m",
    60  to "1h",
    90  to "1h 30m",
    120 to "2h",
    180 to "3h",
    240 to "4h"
)

@Composable
private fun DailyAllowanceTab() {
    val scope = rememberCoroutineScope()

    var allowances  by remember { mutableStateOf(listOf<DailyAllowance>()) }
    var scannedApps by remember { mutableStateOf(listOf<ScannedApp>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var showPicker  by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<DailyAllowance?>(null) }
    var tick        by remember { mutableStateOf(0) }

    fun reload() {
        scope.launch {
            allowances  = withContext(Dispatchers.IO) { Database.getDailyAllowances() }
            val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
            val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
            val runningNames = running.map { it.processName }.toSet()
            scannedApps = running + curated.filter { it.processName !in runningNames }
            isLoading   = false
            DailyAllowanceTracker.reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Live tick every second to update progress bars
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++ }
    }

    val blockedToday = remember(tick) { DailyAllowanceTracker.blockedProcesses }
    val alreadyAllowed = remember(allowances) { allowances.map { it.processName.lowercase() }.toSet() }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Info banner ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Warning.copy(alpha = 0.08f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Timelapse, null,
                        tint = Warning, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Daily allowances let an app run for a set time each day, then block it until midnight. " +
                        "Limits reset automatically every day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Add button ──────────────────────────────────────────────────
            item {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Daily Allowance", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            // ── Loading / empty ─────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Warning) }
                }
            } else if (allowances.isEmpty()) {
                item { EmptyAllowanceState() }
            } else {
                item {
                    Text(
                        "${allowances.size} app${if (allowances.size == 1) "" else "s"} with daily limits",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface2,
                        fontWeight = FontWeight.Medium
                    )
                }

                items(allowances, key = { it.processName }) { allowance ->
                    val usedMinutes = remember(tick) {
                        DailyAllowanceTracker.getUsageMinutes(allowance.processName)
                    }
                    val remaining = remember(tick) {
                        DailyAllowanceTracker.getRemainingMinutes(allowance)
                    }
                    val isBlockedToday = allowance.processName.lowercase() in blockedToday

                    AllowanceCard(
                        allowance      = allowance,
                        usedMinutes    = usedMinutes,
                        remainingMinutes = remaining,
                        isBlockedToday = isBlockedToday,
                        onEdit         = { editTarget = allowance },
                        onDelete       = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    Database.deleteDailyAllowance(allowance.processName)
                                }
                                DailyAllowanceTracker.reload()
                                reload()
                            }
                        }
                    )
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter  = rememberScrollbarAdapter(listState)
        )
    }

    // ── Add allowance flow ─────────────────────────────────────────────────────
    if (showPicker) {
        AllowancePickerDialog(
            scannedApps    = scannedApps,
            alreadyAllowed = alreadyAllowed,
            onDismiss      = { showPicker = false },
            onConfirm      = { processName, displayName, minutes ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Database.upsertDailyAllowance(
                            DailyAllowance(processName, displayName, minutes)
                        )
                    }
                    DailyAllowanceTracker.reload()
                    showPicker = false
                    reload()
                }
            }
        )
    }

    // ── Edit allowance minutes ─────────────────────────────────────────────────
    editTarget?.let { target ->
        EditAllowanceDialog(
            allowance = target,
            onDismiss = { editTarget = null },
            onSave    = { newMinutes ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Database.upsertDailyAllowance(
                            target.copy(allowanceMinutes = newMinutes)
                        )
                    }
                    DailyAllowanceTracker.reload()
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
    val progress = if (allowance.allowanceMinutes > 0)
        (usedMinutes.toFloat() / allowance.allowanceMinutes.toFloat()).coerceIn(0f, 1f)
    else 0f

    val barColor = when {
        isBlockedToday  -> Error
        progress > 0.8f -> Warning
        else            -> Success
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isBlockedToday  -> Error.copy(alpha = 0.06f)
                    progress > 0.8f -> Warning.copy(alpha = 0.05f)
                    else            -> Surface2
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(
                processName = allowance.processName,
                displayName = allowance.displayName,
                size = 42
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        allowance.displayName,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isBlockedToday) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Blocked until midnight",
                                style = MaterialTheme.typography.labelSmall,
                                color = Error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    allowance.processName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, null, tint = OnSurface2, modifier = Modifier.size(16.dp))
            }
        }

        // ── Progress bar ───────────────────────────────────────────────────
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color     = barColor,
            trackColor = Surface3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMinutes(usedMinutes) + " used",
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Limit: " + formatMinutes(allowance.allowanceMinutes.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface2
            )
            if (!isBlockedToday) {
                Text(
                    formatMinutes(remainingMinutes) + " left",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface2
                )
            }
        }
    }
}

private fun formatMinutes(mins: Long): String {
    if (mins <= 0L) return "0m"
    val h = mins / 60
    val m = mins % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0           -> "${h}h"
        else            -> "${m}m"
    }
}

@Composable
private fun EmptyAllowanceState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Timelapse, null, tint = OnSurface2, modifier = Modifier.size(36.dp))
        }
        Text("No daily limits set", style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text(
            "Add an allowance to let an app run for a set time before it gets blocked for the day.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ── Allowance Picker Dialog (pick app → pick minutes) ─────────────────────────

@Composable
private fun AllowancePickerDialog(
    scannedApps:    List<ScannedApp>,
    alreadyAllowed: Set<String>,
    onDismiss:      () -> Unit,
    onConfirm:      (processName: String, displayName: String, minutes: Int) -> Unit
) {
    var step            by remember { mutableStateOf(0) } // 0 = pick app, 1 = pick minutes
    var pickedApp       by remember { mutableStateOf<ScannedApp?>(null) }
    var selectedMinutes by remember { mutableStateOf(60) }
    var search          by remember { mutableStateOf("") }
    var showAll         by remember { mutableStateOf(false) }
    var manualExe       by remember { mutableStateOf("") }

    val runningApps = remember(scannedApps) { scannedApps.filter { it.isRunning } }
    val sourceList  = if (showAll) scannedApps else runningApps
    val filtered    = remember(search, sourceList) {
        if (search.isBlank()) sourceList
        else sourceList.filter {
            it.displayName.contains(search, ignoreCase = true) ||
            it.processName.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(520.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Timelapse, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Text(
                        if (step == 0) "Choose an app" else "Set daily limit for ${pickedApp?.displayName ?: ""}",
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (step == 0) {
                    Text(
                        "Step 1 of 2 — select the app you want to limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2
                    )
                } else {
                    Text(
                        "Step 2 of 2 — how much time per day?",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2
                    )
                }
            }
        },
        text = {
            if (step == 0) {
                // ── Step 1: App picker ─────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search apps…", color = OnSurface2) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
                        },
                        modifier  = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Warning,
                            unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f),
                            focusedTextColor     = OnSurface,
                            unfocusedTextColor   = OnSurface
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showAll,
                            onClick  = { showAll = false },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                    Text("Running (${runningApps.size})", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Success.copy(alpha = 0.15f),
                                selectedLabelColor     = Success
                            )
                        )
                        FilterChip(
                            selected = showAll,
                            onClick  = { showAll = true },
                            label    = {
                                Text("All Apps (${scannedApps.size})", style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Warning.copy(alpha = 0.15f),
                                selectedLabelColor     = Warning
                            )
                        )
                    }

                    val pickerState = rememberLazyListState()
                    Box(modifier = Modifier.height(280.dp)) {
                        LazyColumn(
                            state = pickerState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Manual entry row
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Surface3)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit, null,
                                        tint = OnSurface2, modifier = Modifier.size(16.dp)
                                    )
                                    OutlinedTextField(
                                        value = manualExe,
                                        onValueChange = { manualExe = it },
                                        placeholder = { Text("Type .exe name…", color = OnSurface2, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (manualExe.isNotBlank()) {
                                                val proc = manualExe.trim().lowercase()
                                                    .let { if (it.endsWith(".exe")) it else "$it.exe" }
                                                pickedApp = ScannedApp(
                                                    processName = proc,
                                                    displayName = InstalledAppsScanner.friendlyNameFor(proc),
                                                    isRunning   = false
                                                )
                                                step = 1
                                            }
                                        }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor   = Warning,
                                            unfocusedBorderColor = OnSurface2.copy(alpha = 0.3f),
                                            focusedTextColor     = OnSurface,
                                            unfocusedTextColor   = OnSurface
                                        )
                                    )
                                    TextButton(
                                        onClick = {
                                            if (manualExe.isNotBlank()) {
                                                val proc = manualExe.trim().lowercase()
                                                    .let { if (it.endsWith(".exe")) it else "$it.exe" }
                                                pickedApp = ScannedApp(
                                                    processName = proc,
                                                    displayName = InstalledAppsScanner.friendlyNameFor(proc),
                                                    isRunning   = false
                                                )
                                                step = 1
                                            }
                                        },
                                        enabled = manualExe.isNotBlank()
                                    ) { Text("Use →", color = Warning) }
                                }
                            }

                            if (filtered.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No apps found. Try 'All Apps' or type a name above.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurface2,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                items(filtered, key = { it.processName }) { app ->
                                    val isAlready = app.processName.lowercase() in alreadyAllowed
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isAlready) Surface3.copy(alpha = 0.5f) else Surface3)
                                            .clickable(enabled = !isAlready) {
                                                pickedApp = app
                                                step = 1
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppIcon(app.processName, app.displayName, size = 34)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    app.displayName,
                                                    color = if (isAlready) OnSurface2 else OnSurface,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                                if (app.isRunning) {
                                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                                }
                                                if (isAlready) {
                                                    Box(
                                                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                                            .background(Warning.copy(alpha = 0.12f))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("has limit", style = MaterialTheme.typography.labelSmall, color = Warning)
                                                    }
                                                }
                                            }
                                            Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight, null,
                                            tint = if (isAlready) OnSurface2.copy(alpha = 0.3f) else OnSurface2,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter  = rememberScrollbarAdapter(pickerState)
                        )
                    }
                }
            } else {
                // ── Step 2: Pick minutes ───────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface3)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pickedApp?.let { app ->
                            AppIcon(app.processName, app.displayName, size = 36)
                            Column {
                                Text(app.displayName, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                            }
                        }
                    }

                    Text(
                        "How long can this app run per day?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface2
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowanceOptions.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (mins, label) ->
                                    FilterChip(
                                        selected = selectedMinutes == mins,
                                        onClick  = { selectedMinutes = mins },
                                        label    = {
                                            Text(
                                                label,
                                                fontWeight = if (selectedMinutes == mins) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Warning.copy(alpha = 0.20f),
                                            selectedLabelColor     = Warning
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Warning.copy(alpha = 0.07f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = Warning, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "After ${allowanceOptions.find { it.first == selectedMinutes }?.second} the app will be " +
                            "closed and blocked for the rest of the day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface2
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                // No confirm on step 1 — tapping an app advances the step
            } else {
                Button(
                    onClick = {
                        pickedApp?.let { app ->
                            onConfirm(app.processName, app.displayName, selectedMinutes)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f))
                ) { Text("Set Limit") }
            }
        },
        dismissButton = {
            if (step == 1) {
                TextButton(onClick = { step = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back", color = OnSurface2)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) }
            }
        }
    )
}

@Composable
private fun EditAllowanceDialog(
    allowance: DailyAllowance,
    onDismiss: () -> Unit,
    onSave:    (Int) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(allowance.allowanceMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(420.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(allowance.processName, allowance.displayName, size = 36)
                Column {
                    Text(
                        "Edit Daily Limit",
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(allowance.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "New daily allowance:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface2
                )
                allowanceOptions.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedMinutes == mins,
                                onClick  = { selectedMinutes = mins },
                                label    = {
                                    Text(
                                        label,
                                        fontWeight = if (selectedMinutes == mins) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Warning.copy(alpha = 0.20f),
                                    selectedLabelColor     = Warning
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedMinutes) },
                colors  = ButtonDefaults.buttonColors(containerColor = Warning.copy(alpha = 0.85f))
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) }
        }
    )
}

// ── Timed Block Tab ────────────────────────────────────────────────────────────

@Composable
private fun TimedBlockTab() {
    val standaloneBlock by StandaloneBlockService.block.collectAsState()
    var scannedApps     by remember { mutableStateOf(listOf<ScannedApp>()) }
    var showPicker      by remember { mutableStateOf(false) }
    var selectedHours   by remember { mutableStateOf(1) }
    var selectedApps    by remember { mutableStateOf(setOf<String>()) }
    var isLoading       by remember { mutableStateOf(true) }

    // Mode: 0 = Duration, 1 = Date Range
    var scheduleMode by remember { mutableStateOf(0) }

    // Date-range fields
    val todayDate = remember { LocalDate.now() }
    var startDate by remember { mutableStateOf(todayDate) }
    var startHour by remember { mutableStateOf(LocalTime.now().hour) }
    var startMin  by remember { mutableStateOf(0) }
    var endDate   by remember { mutableStateOf(todayDate) }
    var endHour   by remember { mutableStateOf((LocalTime.now().hour + 1).coerceAtMost(23)) }
    var endMin    by remember { mutableStateOf(0) }

    // Live tick so timer stays updated
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(standaloneBlock) {
        while (true) { delay(1000); tick++ }
    }

    val isScheduled  = standaloneBlock != null && StandaloneBlockService.isScheduled
    val isActive     = StandaloneBlockService.isActive
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
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Warning.copy(alpha = 0.08f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Timer, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Text(
                        "Timed blocks cannot be cancelled early. Choose carefully — this is a commitment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        modifier = Modifier.weight(1f)
                    )
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
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface2)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Configure Timed Block",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )

                        // ── Mode toggle ────────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface3)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Duration", "Date Range").forEachIndexed { idx, label ->
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (scheduleMode == idx) Purple80.copy(alpha = 0.20f)
                                            else Color.Transparent
                                        )
                                        .clickable { scheduleMode = idx }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (scheduleMode == idx) Purple80 else OnSurface2,
                                        fontWeight = if (scheduleMode == idx) FontWeight.SemiBold
                                                     else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (scheduleMode == 0) {
                            // ── Duration mode ──────────────────────────────────────
                            Text("Duration", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1 to "1h", 2 to "2h", 4 to "4h", 8 to "8h", 12 to "12h")
                                    .forEach { (h, label) ->
                                        FilterChip(
                                            selected = selectedHours == h,
                                            onClick  = { selectedHours = h },
                                            label    = { Text(label) },
                                            colors   = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Purple80.copy(alpha = 0.2f),
                                                selectedLabelColor     = Purple80
                                            )
                                        )
                                    }
                            }
                        } else {
                            // ── Date Range mode ────────────────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Start date/time
                                Text(
                                    "Start",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurface2,
                                    fontWeight = FontWeight.SemiBold
                                )
                                DateTimePicker(
                                    date      = startDate,
                                    hour      = startHour,
                                    minute    = startMin,
                                    accentColor = Purple80,
                                    onDateChange  = { startDate = it },
                                    onHourChange  = { startHour = it },
                                    onMinChange   = { startMin  = it }
                                )

                                HorizontalDivider(color = Surface3)

                                // End date/time
                                Text(
                                    "End",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurface2,
                                    fontWeight = FontWeight.SemiBold
                                )
                                DateTimePicker(
                                    date      = endDate,
                                    hour      = endHour,
                                    minute    = endMin,
                                    minDate   = startDate,
                                    accentColor = Error,
                                    onDateChange  = { endDate = it },
                                    onHourChange  = { endHour = it },
                                    onMinChange   = { endMin  = it }
                                )

                                // Duration preview
                                val startEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin))
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val endEpoch = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin))
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val durationMins = (endEpoch - startEpoch) / 60_000L
                                if (durationMins > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Purple80.copy(alpha = 0.07f))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule, null,
                                            tint = Purple80, modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Block duration: ${formatMinutes(durationMins)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurface2
                                        )
                                    }
                                } else if (durationMins <= 0) {
                                    Text(
                                        "End time must be after start time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Error
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Surface3)

                        Text("Apps to block", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

                        if (selectedApps.isEmpty()) {
                            Text(
                                "No apps selected yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface2
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedApps.forEach { proc ->
                                    val app = scannedApps.find { it.processName.equals(proc, ignoreCase = true) }
                                    val friendly = app?.displayName ?: InstalledAppsScanner.friendlyNameFor(proc)
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface3)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppIcon(processName = proc, displayName = friendly, size = 32)
                                        Text(friendly, color = OnSurface, modifier = Modifier.weight(1f))
                                        Text(proc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                                        IconButton(
                                            onClick = { selectedApps = selectedApps - proc },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close, null,
                                                tint = OnSurface2, modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple80)
                        ) {
                            Icon(Icons.Default.Apps, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (selectedApps.isEmpty()) "Pick Apps" else "Change App Selection")
                        }

                        // ── Start button ───────────────────────────────────────────
                        val canStart = selectedApps.isNotEmpty() && run {
                            if (scheduleMode == 1) {
                                val sEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin))
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val eEpoch = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin))
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                eEpoch > sEpoch
                            } else true
                        }

                        Button(
                            onClick = {
                                if (!canStart) return@Button
                                if (scheduleMode == 0) {
                                    StandaloneBlockService.start(
                                        selectedApps.toList(),
                                        selectedHours * 3_600_000L
                                    )
                                } else {
                                    val sEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin))
                                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val eEpoch = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin))
                                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val now = System.currentTimeMillis()
                                    val scheduledStart = if (sEpoch > now) sEpoch else null
                                    StandaloneBlockService.start(
                                        selectedApps.toList(),
                                        eEpoch - maxOf(sEpoch, now),
                                        scheduledStart
                                    )
                                }
                            },
                            enabled  = canStart,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f)),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (scheduleMode == 1 && run {
                                    val sEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin))
                                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    sEpoch > System.currentTimeMillis()
                                }) Icons.Default.Schedule else Icons.Default.Block,
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            val label = if (scheduleMode == 0) {
                                "Start $selectedHours-Hour Block (${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"})"
                            } else {
                                val sEpoch = LocalDateTime.of(startDate, LocalTime.of(startHour, startMin))
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                if (sEpoch > System.currentTimeMillis()) "Schedule Block (${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"})"
                                else "Start Block Now (${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"})"
                            }
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter  = rememberScrollbarAdapter(listState)
        )
    }

    if (showPicker) {
        AppPickerDialog(
            scannedApps       = scannedApps,
            alreadyBlocked    = emptySet(),
            title             = "Pick Apps to Block",
            confirmLabel      = "Select Apps",
            confirmColor      = Error,
            showNetworkToggle = false,
            preSelected       = selectedApps,
            onDismiss         = { showPicker = false },
            onConfirm         = { picked, _ ->
                selectedApps = picked.map { it.processName }.toSet()
                showPicker = false
            }
        )
    }
}

/** Compact date + time spinner row. */
@Composable
private fun DateTimePicker(
    date: LocalDate,
    hour: Int,
    minute: Int,
    accentColor: Color,
    minDate: LocalDate = LocalDate.now(),
    onDateChange: (LocalDate) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinChange:  (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface3)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date spinner
        SpinnerField(
            value    = "${date.dayOfMonth}",
            label    = "Day",
            onDec    = { onDateChange(maxOf(date.minusDays(1), minDate)) },
            onInc    = { onDateChange(date.plusDays(1)) },
            accentColor = accentColor,
            modifier = Modifier.weight(1.2f)
        )
        Text("/", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        SpinnerField(
            value    = "%02d".format(date.monthValue),
            label    = "Mo",
            onDec    = { onDateChange(maxOf(date.minusMonths(1).withDayOfMonth(1).also { if (it < minDate) return@SpinnerField }, minDate)) },
            onInc    = { onDateChange(date.plusMonths(1).withDayOfMonth(minOf(date.dayOfMonth, date.plusMonths(1).lengthOfMonth()))) },
            accentColor = accentColor,
            modifier = Modifier.weight(1.2f)
        )
        Text("/", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        SpinnerField(
            value    = "${date.year}",
            label    = "Year",
            onDec    = { onDateChange(maxOf(date.minusYears(1), minDate)) },
            onInc    = { onDateChange(date.plusYears(1)) },
            accentColor = accentColor,
            modifier = Modifier.weight(1.6f)
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Schedule, null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        // Hour spinner
        SpinnerField(
            value    = "%02d".format(hour),
            label    = "HH",
            onDec    = { onHourChange((hour - 1 + 24) % 24) },
            onInc    = { onHourChange((hour + 1) % 24) },
            accentColor = accentColor,
            modifier = Modifier.weight(1.2f)
        )
        Text(":", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
        // Minute spinner (15-min steps)
        SpinnerField(
            value    = "%02d".format(minute),
            label    = "MM",
            onDec    = { onMinChange((minute - 15 + 60) % 60) },
            onInc    = { onMinChange((minute + 15) % 60) },
            accentColor = accentColor,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun SpinnerField(
    value: String,
    label: String,
    onDec: () -> Unit,
    onInc: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        IconButton(onClick = onInc, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Text(
            value,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            color = OnSurface2,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onDec, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ActiveTimedBlock(
    remainingMs:  Long,
    startsInMs:   Long,
    blockedNames: List<String>,
    onAddTime:    (Int) -> Unit
) {
    val isWaiting  = startsInMs > 0L
    val accentColor = if (isWaiting) Warning else Error

    val remSec = ((if (isWaiting) startsInMs else remainingMs) / 1000).toInt().coerceAtLeast(0)
    val h = remSec / 3600
    val m = (remSec % 3600) / 60
    val s = remSec % 60

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accentColor.copy(alpha = 0.07f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentColor))
            Text(
                if (isWaiting) "Block Scheduled — Waiting to Start" else "Timed Block Active",
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            if (isWaiting) {
                if (h > 0) "Starts in ${h}h ${m}m ${s}s" else "Starts in ${m}m ${s}s"
            } else {
                if (h > 0) "${h}h ${m}m ${s}s remaining" else "${m}m ${s}s remaining"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )

        if (isWaiting) {
            // Show how long the block will run once it starts
            val blockDurSec = (remainingMs / 1000).toInt().coerceAtLeast(0)
            val bh = blockDurSec / 3600
            val bm = (blockDurSec % 3600) / 60
            Text(
                "Block duration: " + if (bh > 0) "${bh}h ${bm}m" else "${bm}m",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface2
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blockedNames.forEach { proc ->
                val display = InstalledAppsScanner.friendlyNameFor(proc)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface3)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(processName = proc, displayName = display, size = 28)
                    Text(display, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    Text(proc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }
        if (!isWaiting) {
            HorizontalDivider(color = Surface3)
            Text("Extend the block:", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "+30m", 60 to "+1h", 120 to "+2h", 240 to "+4h").forEach { (mins, label) ->
                    OutlinedButton(
                        onClick = { onAddTime(mins) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ── App Picker Dialog ──────────────────────────────────────────────────────────

@Composable
private fun AppPickerDialog(
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
    var search       by remember { mutableStateOf("") }
    var selected     by remember { mutableStateOf(preSelected) }
    var networkBlock by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showAll      by remember { mutableStateOf(false) }

    val runningApps = remember(scannedApps) { scannedApps.filter { it.isRunning } }
    val sourceList  = if (showAll) scannedApps else runningApps
    val filtered    = remember(search, sourceList) {
        if (search.isBlank()) sourceList
        else sourceList.filter {
            it.displayName.contains(search, ignoreCase = true) ||
            it.processName.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        modifier         = Modifier.width(540.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value         = search,
                    onValueChange = { search = it },
                    placeholder   = { Text("Search apps…", color = OnSurface2) },
                    leadingIcon   = {
                        Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
                    },
                    modifier  = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = confirmColor,
                        unfocusedBorderColor = OnSurface2.copy(alpha = 0.4f),
                        focusedTextColor     = OnSurface,
                        unfocusedTextColor   = OnSurface
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showAll,
                            onClick  = { showAll = false },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                    Text("Running (${runningApps.size})", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Success.copy(alpha = 0.15f),
                                selectedLabelColor     = Success
                            )
                        )
                        FilterChip(
                            selected = showAll,
                            onClick  = { showAll = true },
                            label    = {
                                Text("All Apps (${scannedApps.size})", style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Purple80.copy(alpha = 0.15f),
                                selectedLabelColor     = Purple80
                            )
                        )
                    }
                    if (selected.isNotEmpty()) {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Purple80,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        text = {
            val pickerListState = rememberLazyListState()
            Box(modifier = Modifier.height(360.dp)) {
                LazyColumn(
                    state   = pickerListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showPresets) {
                        item {
                            Text(
                                "Quick Presets",
                                style = MaterialTheme.typography.labelSmall,
                                color = Purple80,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(BlockPresets.all, key = { "preset_${it.id}" }) { preset ->
                            val presetProcs = preset.processNames.toSet()
                            val allSel = presetProcs.all { proc ->
                                selected.any { it.equals(proc, ignoreCase = true) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (allSel) Purple80.copy(alpha = 0.10f) else Surface2)
                                    .clickable {
                                        selected = if (allSel)
                                            selected.filter { sel ->
                                                presetProcs.none { it.equals(sel, ignoreCase = true) }
                                            }.toSet()
                                        else
                                            selected + presetProcs
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(preset.emoji, fontSize = 18.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        preset.name,
                                        color = OnSurface,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        preset.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurface2
                                    )
                                }
                                if (allSel) {
                                    Icon(
                                        Icons.Default.CheckCircle, null,
                                        tint = Purple80,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(color = Surface3, modifier = Modifier.padding(vertical = 6.dp))
                            Text(
                                "Apps",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurface2,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff, null,
                                        tint = OnSurface2, modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        if (!showAll) "No running apps found. Switch to 'All Apps'."
                                        else "No apps match \"$search\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurface2,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.processName }) { app ->
                            val isSelected = app.processName in selected
                            val isAlready  = app.processName.lowercase() in alreadyBlocked
                            val netEnabled = networkBlock[app.processName] ?: false

                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isAlready  -> Surface3.copy(alpha = 0.5f)
                                            isSelected -> confirmColor.copy(alpha = 0.10f)
                                            else       -> Surface3
                                        }
                                    )
                                    .clickable(enabled = !isAlready) {
                                        selected = if (isSelected) selected - app.processName
                                                   else            selected + app.processName
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIcon(app.processName, app.displayName, size = 36)

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            app.displayName,
                                            color = if (isAlready) OnSurface2 else OnSurface,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (app.isRunning) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
                                        }
                                        if (isAlready) {
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                                    .background(OnSurface2.copy(alpha = 0.12f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "blocked",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OnSurface2
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        app.processName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurface2,
                                        fontSize = 10.sp
                                    )
                                }

                                if (showNetworkToggle && isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.WifiOff, null,
                                            tint = if (netEnabled) Warning else OnSurface2,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Switch(
                                            checked = netEnabled,
                                            onCheckedChange = { networkBlock = networkBlock + (app.processName to it) },
                                            modifier = Modifier.scale(0.52f).height(18.dp),
                                            colors = SwitchDefaults.colors(
                                                checkedTrackColor = Warning.copy(alpha = 0.4f),
                                                checkedThumbColor = Warning
                                            )
                                        )
                                    }
                                }

                                Checkbox(
                                    checked         = isSelected || isAlready,
                                    onCheckedChange = null,
                                    enabled         = !isAlready,
                                    colors          = CheckboxDefaults.colors(
                                        checkedColor = if (isAlready) OnSurface2 else confirmColor
                                    )
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter  = rememberScrollbarAdapter(pickerListState)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val picked = scannedApps.filter {
                        it.processName in selected && it.processName.lowercase() !in alreadyBlocked
                    }
                    onConfirm(picked, networkBlock)
                },
                enabled = selected.isNotEmpty(),
                colors  = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) }
        }
    )
}
