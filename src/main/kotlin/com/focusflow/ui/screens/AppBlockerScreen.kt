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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.Database
import com.focusflow.data.models.BlockRule
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.ScannedApp
import com.focusflow.services.StandaloneBlockService
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    "spotify.exe"           to Color(0xFF1DB954),
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
    val tabs = listOf("Always Block", "Block for Time")

    Column(
        modifier = Modifier.fillMaxSize().background(Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface2).padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Block, null, tint = Purple80, modifier = Modifier.size(28.dp))
            Column {
                Text("App Blocker", style = MaterialTheme.typography.headlineMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                Text("Block distracting apps — permanently or for a set time", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Surface2,
            contentColor     = Purple80
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
                            if (index == 0) Icons.Default.Block else Icons.Default.Timer,
                            null,
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
        }
    }
}

// ── Always Block Tab ───────────────────────────────────────────────────────────

@Composable
private fun AlwaysBlockTab() {
    val scope = rememberCoroutineScope()

    var blockRules  by remember { mutableStateOf(listOf<BlockRule>()) }
    var scannedApps by remember { mutableStateOf(listOf<ScannedApp>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var showPicker  by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val rules = withContext(Dispatchers.IO) { Database.getBlockRules() }
            val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
            val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
            val runningNames = running.map { it.processName }.toSet()
            val extra = curated.filter { it.processName !in runningNames }
            blockRules  = rules
            scannedApps = running + extra
            isLoading   = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        "Apps here are killed immediately when detected — during focus sessions AND when Always-On enforcement is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Apps, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Pick Apps to Block", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple80)
                    }
                }
            } else if (blockRules.isEmpty()) {
                item { EmptyBlockState() }
            } else {
                item {
                    Text(
                        "${blockRules.size} app${if (blockRules.size == 1) "" else "s"} permanently blocked",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface2,
                        fontWeight = FontWeight.Medium
                    )
                }
                items(blockRules, key = { it.id }) { rule ->
                    BlockRuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            scope.launch {
                                withContext(Dispatchers.IO) { Database.upsertBlockRule(rule.copy(enabled = enabled)) }
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

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }

    if (showPicker) {
        AppPickerDialog(
            scannedApps    = scannedApps,
            alreadyBlocked = blockRules.map { it.processName.lowercase() }.toSet(),
            title          = "Pick Apps to Always Block",
            confirmLabel   = "Block Selected",
            confirmColor   = Purple80,
            showNetworkToggle = true,
            onDismiss = { showPicker = false },
            onConfirm = { picked, networkMap ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        picked.forEach { app ->
                            Database.upsertBlockRule(
                                BlockRule(
                                    id          = UUID.randomUUID().toString(),
                                    processName = app.processName.lowercase(),
                                    displayName = app.displayName,
                                    enabled     = true,
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
        AppIcon(
            processName = rule.processName,
            displayName = rule.displayName,
            size = 40
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(rule.displayName, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rule.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                if (rule.blockNetwork) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Warning.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("+ network", style = MaterialTheme.typography.labelSmall, color = Warning)
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
        Text("Tap 'Pick Apps to Block' to add apps to your block list.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
    }
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

    val isActive      = standaloneBlock != null && StandaloneBlockService.isActive
    val remainingMs   = StandaloneBlockService.remainingMs()
    val blockedNames  = standaloneBlock?.processNames ?: emptyList()

    LaunchedEffect(Unit) {
        val running = withContext(Dispatchers.IO) { InstalledAppsScanner.getRunningApps() }
        val curated = withContext(Dispatchers.IO) { InstalledAppsScanner.getCuratedApps() }
        val runningNames = running.map { it.processName }.toSet()
        scannedApps = running + curated.filter { it.processName !in runningNames }
        isLoading   = false
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

            if (isActive) {
                item { ActiveTimedBlock(remainingMs = remainingMs, blockedNames = blockedNames, onAddTime = { StandaloneBlockService.addTime(it * 60_000L) }) }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Configure Timed Block", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

                        Text("Duration", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "1h", 2 to "2h", 4 to "4h", 8 to "8h", 12 to "12h").forEach { (h, label) ->
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

                        HorizontalDivider(color = Surface3)

                        Text("Apps to block", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

                        if (selectedApps.isEmpty()) {
                            Text("No apps selected yet.", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedApps.forEach { proc ->
                                    val app = scannedApps.find { it.processName.equals(proc, ignoreCase = true) }
                                    val friendly = app?.displayName ?: InstalledAppsScanner.friendlyNameFor(proc)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
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

                        OutlinedButton(
                            onClick = { showPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple80)
                        ) {
                            Icon(Icons.Default.Apps, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (selectedApps.isEmpty()) "Pick Apps" else "Change App Selection")
                        }

                        Button(
                            onClick = {
                                if (selectedApps.isNotEmpty()) {
                                    StandaloneBlockService.start(
                                        selectedApps.toList(),
                                        selectedHours * 3_600_000L
                                    )
                                }
                            },
                            enabled  = selectedApps.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.85f)),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Start $selectedHours-Hour Block (${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp
                            )
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
            title             = "Pick Apps to Block for ${selectedHours}h",
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

@Composable
private fun ActiveTimedBlock(remainingMs: Long, blockedNames: List<String>, onAddTime: (Int) -> Unit) {
    val remSec = (remainingMs / 1000).toInt().coerceAtLeast(0)
    val h = remSec / 3600
    val m = (remSec % 3600) / 60
    val s = remSec % 60

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Error.copy(alpha = 0.07f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Error))
            Text("Timed Block Active", style = MaterialTheme.typography.titleMedium, color = Error, fontWeight = FontWeight.Bold)
        }
        Text(
            if (h > 0) "${h}h ${m}m ${s}s remaining" else "${m}m ${s}s remaining",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blockedNames.forEach { proc ->
                val display = InstalledAppsScanner.friendlyNameFor(proc)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface3).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(processName = proc, displayName = display, size = 28)
                    Text(display, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    Text(proc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }
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

// ── App Picker Dialog ──────────────────────────────────────────────────────────

@Composable
private fun AppPickerDialog(
    scannedApps:      List<ScannedApp>,
    alreadyBlocked:   Set<String>,
    title:            String,
    confirmLabel:     String,
    confirmColor:     Color,
    showNetworkToggle: Boolean,
    preSelected:      Set<String> = emptySet(),
    onDismiss:        () -> Unit,
    onConfirm:        (List<ScannedApp>, Map<String, Boolean>) -> Unit
) {
    var search       by remember { mutableStateOf("") }
    var selected     by remember { mutableStateOf(preSelected) }
    var networkBlock by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showAll      by remember { mutableStateOf(false) }

    val runningApps = remember(scannedApps) { scannedApps.filter { it.isRunning } }
    val allApps     = scannedApps

    val sourceList = if (showAll) allApps else runningApps

    val filtered = remember(search, sourceList) {
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
                    leadingIcon   = { Icon(Icons.Default.Search, null, tint = OnSurface2, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Purple80,
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
                            label    = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            label    = { Text("All Apps (${allApps.size})", style = MaterialTheme.typography.labelSmall) },
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
                    state  = pickerListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.SearchOff, null, tint = OnSurface2, modifier = Modifier.size(32.dp))
                                    Text(
                                        if (!showAll) "No running apps found. Switch to 'All Apps' to see the full list."
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
                                            isSelected -> Purple80.copy(alpha = 0.12f)
                                            else       -> Surface3
                                        }
                                    )
                                    .clickable(enabled = !isAlready) {
                                        selected = if (isSelected) selected - app.processName else selected + app.processName
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIcon(
                                    processName = app.processName,
                                    displayName = app.displayName,
                                    size = 36
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(OnSurface2.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text("blocked", style = MaterialTheme.typography.labelSmall, color = OnSurface2)
                                            }
                                        }
                                    }
                                    Text(app.processName, style = MaterialTheme.typography.bodySmall, color = OnSurface2, fontSize = 10.sp)
                                }

                                if (showNetworkToggle && isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.WifiOff, null, tint = if (netEnabled) Warning else OnSurface2, modifier = Modifier.size(14.dp))
                                        Switch(
                                            checked = netEnabled,
                                            onCheckedChange = { networkBlock = networkBlock + (app.processName to it) },
                                            modifier = Modifier.height(20.dp),
                                            colors = SwitchDefaults.colors(checkedTrackColor = Warning.copy(alpha = 0.4f), checkedThumbColor = Warning)
                                        )
                                    }
                                }

                                Checkbox(
                                    checked  = isSelected || isAlready,
                                    onCheckedChange = null,
                                    enabled  = !isAlready,
                                    colors   = CheckboxDefaults.colors(
                                        checkedColor = if (isAlready) OnSurface2 else Purple80
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
                onClick  = {
                    val picked = scannedApps.filter { it.processName in selected && it.processName.lowercase() !in alreadyBlocked }
                    onConfirm(picked, networkBlock)
                },
                enabled  = selected.isNotEmpty(),
                colors   = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface2) }
        }
    )
}
