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
    val scope = rememberCoroutineScope()

    val totalPages = 4

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

                    if (page < totalPages - 1) {
                        TextButton(onClick = {
                            scope.launch {
                                applyOnboardingSelections(selectedPresets, focusDuration)
                                onDismiss()
                            }
                        }) {
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
