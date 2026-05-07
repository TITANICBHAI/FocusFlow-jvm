package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusflow.data.Database
import com.focusflow.data.models.BlockRule
import com.focusflow.enforcement.BlockPreset
import com.focusflow.enforcement.BlockPresets
import com.focusflow.enforcement.InstalledAppsScanner
import com.focusflow.enforcement.WindowsStartupManager
import com.focusflow.enforcement.isWindows
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class GoalOption(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val sublabel: String
)

private val GOALS = listOf(
    GoalOption("social",  Icons.Default.ChatBubble,   "Social media & chat",    "Discord, Instagram, WhatsApp…"),
    GoalOption("gaming",  Icons.Default.SportsEsports, "Gaming & entertainment", "Steam, Twitch, Netflix…"),
    GoalOption("web",     Icons.Default.Language,      "Browsing the web",       "Chrome, Firefox, Edge…"),
    GoalOption("deep",    Icons.Default.Psychology,    "Deep work sessions",     "Block everything, stay locked in")
)

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var page          by remember { mutableStateOf(0) }
    var selectedGoal  by remember { mutableStateOf<String?>(null) }
    var selectedPresets by remember { mutableStateOf(setOf<String>()) }
    var focusDuration by remember { mutableStateOf(25) }
    var termsAccepted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val totalPages = 7

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Surface,
            modifier = Modifier.width(600.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState)
                            fadeIn() + slideInHorizontally { it / 5 } togetherWith fadeOut() + slideOutHorizontally { -it / 5 }
                        else
                            fadeIn() + slideInHorizontally { -it / 5 } togetherWith fadeOut() + slideOutHorizontally { it / 5 }
                    }
                ) { p ->
                    when (p) {
                        0 -> WelcomePage()
                        1 -> GoalPage(selectedGoal) { goal ->
                            selectedGoal = goal
                            val suggestions = BlockPresets.goalSuggestions[goal] ?: emptyList()
                            selectedPresets = suggestions.toSet()
                        }
                        2 -> PresetsPage(selectedPresets) { selectedPresets = it }
                        3 -> FocusDurationPage(focusDuration) { focusDuration = it }
                        4 -> PrivacyTermsPage(termsAccepted) { termsAccepted = it }
                        5 -> PermissionsPage()
                        6 -> GuidePage()
                    }
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(totalPages) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == page) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == page) Purple80 else Purple80.copy(alpha = 0.22f))
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page > 0) {
                        TextButton(onClick = { page-- }) {
                            Text("Back", color = OnSurface2)
                        }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }

                    if (page < 4) {
                        TextButton(onClick = { page = 4 }) {
                            Text("Skip setup", color = OnSurface2.copy(alpha = 0.55f), fontSize = 13.sp)
                        }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }

                    Button(
                        onClick = {
                            if (page < totalPages - 1) {
                                page++
                            } else {
                                scope.launch {
                                    applyOnboardingSelections(selectedPresets, focusDuration)
                                    onDismiss()
                                }
                            }
                        },
                        enabled = if (page == 4) termsAccepted else true,
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (page == totalPages - 1) {
                            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Let's Go!", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(if (page == 0) "Get Started" else "Next", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun applyOnboardingSelections(
    selectedPresets: Set<String>,
    focusDuration: Int
) {
    withContext(Dispatchers.IO) {
        val processesToBlock = selectedPresets
            .mapNotNull { BlockPresets.findById(it) }
            .flatMap { it.processNames }
            .distinct()

        val existing = Database.getBlockRules().map { it.processName.lowercase() }.toSet()
        processesToBlock.forEach { proc ->
            if (proc !in existing) {
                Database.upsertBlockRule(
                    BlockRule(
                        id = UUID.randomUUID().toString(),
                        processName = proc.lowercase(),
                        displayName = InstalledAppsScanner.friendlyNameFor(proc),
                        enabled = true,
                        blockNetwork = false
                    )
                )
            }
        }

        if (selectedPresets.isNotEmpty()) {
            Database.setSetting("onboarding_presets", selectedPresets.joinToString(","))
        }
        Database.setSetting("default_focus_minutes", focusDuration.toString())
    }
}

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            LogoMark(size = 64.dp)
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Welcome to FocusFlow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "Deep Focus & Real App Blocking",
            style = MaterialTheme.typography.titleMedium,
            color = Purple80,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "FocusFlow doesn't just remind you to focus — it enforces it. " +
            "Apps that distract you get killed the moment you open them during a session. " +
            "Let's set up your block list in 60 seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface2,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            FeaturePill(Icons.Default.Block, "Real enforcement")
            FeaturePill(Icons.Default.Timer, "Pomodoro timer")
            FeaturePill(Icons.Default.BarChart, "Focus stats")
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Purple80.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, tint = Purple80, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Purple80, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoalPage(selectedGoal: String?, onGoalSelect: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.TrackChanges, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "What's your biggest distraction?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "We'll suggest the right block preset for you — you can always change it next.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GOALS.forEach { goal ->
                val isSelected = selectedGoal == goal.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) Purple80.copy(alpha = 0.14f)
                            else Surface2
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) Purple80 else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onGoalSelect(goal.id) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Purple80.copy(alpha = 0.2f)
                                else Purple80.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(goal.icon, null, tint = Purple80, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(goal.label, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(goal.sublabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                    }
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, null, tint = Purple80, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsPage(selectedPresets: Set<String>, onToggle: (Set<String>) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Block, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "Pick your block presets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "Selected presets are added to your permanent block list. Tap any to toggle.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        if (selectedPresets.isNotEmpty()) {
            Text(
                "${selectedPresets.size} preset${if (selectedPresets.size == 1) "" else "s"} selected · " +
                "${BlockPresets.all.filter { it.id in selectedPresets }.flatMap { it.processNames }.distinct().size} apps will be blocked",
                style = MaterialTheme.typography.labelMedium,
                color = Purple80,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(BlockPresets.all, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = preset.id in selectedPresets,
                    onToggle = {
                        onToggle(
                            if (preset.id in selectedPresets)
                                selectedPresets - preset.id
                            else
                                selectedPresets + preset.id
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: BlockPreset, isSelected: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) Purple80.copy(alpha = 0.14f) else Surface2
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Purple80 else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggle() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(preset.emoji, fontSize = 24.sp)
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Purple80,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            preset.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            preset.description,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface2,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun FocusDurationPage(focusDuration: Int, onSelect: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Timer, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "How long are your focus sessions?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "This sets your default session length. You can change it any time in Focus mode.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DurationOption(
                minutes = 25,
                label = "25 minutes",
                sublabel = "Pomodoro — the classic sprint. Work 25, break 5.",
                isSelected = focusDuration == 25,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 45,
                label = "45 minutes",
                sublabel = "Extended sprint — great for deeper tasks.",
                isSelected = focusDuration == 45,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 60,
                label = "60 minutes",
                sublabel = "Flow state — one solid hour, no interruptions.",
                isSelected = focusDuration == 60,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 90,
                label = "90 minutes",
                sublabel = "Deep work block — for complex creative work.",
                isSelected = focusDuration == 90,
                onSelect = onSelect
            )
        }

        if (focusDuration > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Purple80.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = Purple80, modifier = Modifier.size(14.dp))
                Text(
                    "You can start any session with a custom duration — this is just the default.",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface2
                )
            }
        }
    }
}

@Composable
private fun DurationOption(
    minutes: Int,
    label: String,
    sublabel: String,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) Purple80.copy(alpha = 0.14f) else Surface2
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Purple80 else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect(minutes) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Purple80.copy(alpha = 0.2f) else Purple80.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${minutes}m",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Purple80
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, null, tint = Purple80, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PrivacyTermsPage(accepted: Boolean, onAccept: (Boolean) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "Privacy & Terms",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "A quick summary of how FocusFlow handles your data.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF1E1E2A))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Storage, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("100% local data", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Everything stays on your device. No external servers, no cloud sync.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
                }
            }
            HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Process monitoring", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("FocusFlow watches running process names only — no file contents or personal data.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
                }
            }
            HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Elevated privileges", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Admin rights (if granted) are only used to terminate blocked processes and manage hosts entries.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (accepted) Purple80.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color(0xFF1E1E2A))
                .border(
                    width = if (accepted) 1.5.dp else 0.dp,
                    color = if (accepted) Purple80 else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onAccept(!accepted) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = { onAccept(it) },
                colors = CheckboxDefaults.colors(checkedColor = Purple80)
            )
            Text(
                "I accept the Terms of Service and Privacy Policy",
                color = OnSurface,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        if (!accepted) {
            Text(
                "Please accept the terms to get started",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface2.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Permission deep-link helpers ────────────────────────────────────────────

private fun openSettingsUrl(url: String) {
    try {
        if (java.awt.Desktop.isDesktopSupported())
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {}
}

private fun runShellCommand(vararg args: String) {
    try { ProcessBuilder(*args).inheritIO().start() } catch (_: Exception) {}
}

private fun relaunchAsAdmin() {
    if (!isWindows) return
    try {
        val exePath = WindowsStartupManager.resolveExePath()
        ProcessBuilder(
            "powershell", "-WindowStyle", "Hidden", "-Command",
            "Start-Process -FilePath '$exePath' -Verb RunAs"
        ).start()
        kotlin.system.exitProcess(0)
    } catch (_: Exception) {}
}

// ─── Page 5: Permissions ─────────────────────────────────────────────────────

@Composable
private fun PermissionsPage() {
    val isAdmin = remember { isRunningAsAdmin() }
    var autoStartEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        autoStartEnabled = withContext(Dispatchers.IO) { WindowsStartupManager.isEnabled() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "Grant Permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "All optional — you can change these any time in Settings. Skip if you're not sure.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(2.dp))

        // ── Run as Admin ──────────────────────────────────────────────────────
        OnboardingPermRow(
            icon = Icons.Default.AdminPanelSettings,
            iconTint = androidx.compose.ui.graphics.Color(0xFFEF5350),
            title = "Run as Administrator",
            subtitle = "Process kill, firewall rules & Nuclear Mode",
            badge = if (isAdmin) "Granted" else "Required for full blocking",
            badgeGranted = isAdmin
        ) {
            if (!isAdmin && isWindows) {
                OutlinedButton(
                    onClick = { relaunchAsAdmin() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Relaunch as Admin →", fontSize = 11.sp, color = Purple80)
                }
            } else if (isAdmin) {
                Icon(Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
            }
        }

        // ── Windows Defender Exclusion ────────────────────────────────────────
        OnboardingPermRow(
            icon = Icons.Default.Security,
            iconTint = androidx.compose.ui.graphics.Color(0xFFFF9800),
            title = "Windows Defender Exclusion",
            subtitle = "Prevents Defender from interfering with app blocking",
            badge = "Recommended",
            badgeGranted = null
        ) {
            if (isWindows) {
                OutlinedButton(
                    onClick = { openSettingsUrl("ms-settings:windowsdefender") },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Open Security →", fontSize = 11.sp, color = Purple80)
                }
            }
        }

        // ── Auto-start ────────────────────────────────────────────────────────
        OnboardingPermRow(
            icon = Icons.Default.Autorenew,
            iconTint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            title = "Auto-Start with Windows",
            subtitle = "FocusFlow launches automatically when you log in",
            badge = if (autoStartEnabled) "Enabled" else "Optional",
            badgeGranted = if (autoStartEnabled) true else null
        ) {
            Switch(
                checked = autoStartEnabled,
                onCheckedChange = { checked ->
                    autoStartEnabled = checked
                    scope.launch(Dispatchers.IO) {
                        if (checked) WindowsStartupManager.enable()
                        else WindowsStartupManager.disable()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Purple80, checkedTrackColor = Purple80.copy(alpha = 0.4f))
            )
        }

        // ── Windows Firewall ──────────────────────────────────────────────────
        OnboardingPermRow(
            icon = Icons.Default.Wifi,
            iconTint = Purple80,
            title = "Windows Firewall Rules",
            subtitle = "View/verify outbound block rules FocusFlow adds",
            badge = "Optional",
            badgeGranted = null
        ) {
            if (isWindows) {
                OutlinedButton(
                    onClick = { runShellCommand("cmd", "/c", "start", "wf.msc") },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Open Firewall →", fontSize = 11.sp, color = Purple80)
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "Tip: You can also reach these from Settings → Windows Setup & Permissions",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface2.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPermRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    badge: String,
    badgeGranted: Boolean?,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = OnSurface2, fontSize = 11.sp, lineHeight = 15.sp)
            val badgeColor = when (badgeGranted) {
                true  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                false -> androidx.compose.ui.graphics.Color(0xFFEF5350)
                null  -> OnSurface2
            }
            Text(
                badge,
                fontSize = 10.sp,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        action()
    }
}

// ─── Page 6: Guide ───────────────────────────────────────────────────────────

@Composable
private fun GuidePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.RocketLaunch, null, tint = Purple80, modifier = Modifier.size(32.dp))
        }

        Text(
            "You're all set!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "Here's how FocusFlow works in 4 steps.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        GuideStep(
            number = 1,
            icon = Icons.Default.Block,
            iconTint = androidx.compose.ui.graphics.Color(0xFFEF5350),
            title = "Add apps to your block list",
            body = "Go to App Blocker in the sidebar, pick apps or use presets — they'll be blocked during sessions."
        )
        GuideStep(
            number = 2,
            icon = Icons.Default.Timer,
            iconTint = Purple80,
            title = "Start a Focus Session",
            body = "Open the Focus tab, set your duration, and hit Start. Blocked apps are killed the moment you open them."
        )
        GuideStep(
            number = 3,
            icon = Icons.Default.Shield,
            iconTint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            title = "Explore Block Defense",
            body = "Enable Network blocking, Keyword Blocker, Nuclear Mode, and Always-On enforcement from Block Defense."
        )
        GuideStep(
            number = 4,
            icon = Icons.Default.BarChart,
            iconTint = androidx.compose.ui.graphics.Color(0xFFFF9800),
            title = "Track your progress",
            body = "Stats and Reports show your daily streaks, session history, and productivity trends over time."
        )
    }
}

@Composable
private fun GuideStep(
    number: Int,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape)
                        .background(Purple80.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$number",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple80
                    )
                }
                Text(title, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(body, color = OnSurface2, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
