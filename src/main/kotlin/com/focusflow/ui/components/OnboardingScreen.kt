package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.focusflow.i18n.AppLanguage
import com.focusflow.i18n.LocalizationManager
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

private fun buildGoals(s: com.focusflow.i18n.AppStrings) = listOf(
    GoalOption("social",  Icons.Default.ChatBubble,    s.goalSocialLabel, s.goalSocialSub),
    GoalOption("gaming",  Icons.Default.SportsEsports, s.goalGamingLabel, s.goalGamingSub),
    GoalOption("web",     Icons.Default.Language,       s.goalWebLabel,   s.goalWebSub),
    GoalOption("deep",    Icons.Default.Psychology,     s.goalDeepLabel,  s.goalDeepSub)
)

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var page          by remember { mutableStateOf(0) }
    var selectedGoal  by remember { mutableStateOf<String?>(null) }
    var selectedPresets by remember { mutableStateOf(setOf<String>()) }
    var focusDuration by remember { mutableStateOf(25) }
    var termsAccepted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = LocalizationManager.strings

    // Page 0 = Language picker (new)
    // Page 1 = Welcome
    // Page 2 = Privacy & Terms
    // Page 3 = Permissions
    // Page 4 = Goal
    // Page 5 = Presets
    // Page 6 = Focus Duration
    // Page 7 = Guide
    val totalPages = 8

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
                        0 -> LanguageSelectionPage()
                        1 -> WelcomePage()
                        2 -> PrivacyTermsPage(termsAccepted) { termsAccepted = it }
                        3 -> PermissionsPage()
                        4 -> GoalPage(selectedGoal) { goal ->
                            selectedGoal = goal
                            val suggestions = BlockPresets.goalSuggestions[goal] ?: emptyList()
                            selectedPresets = suggestions.toSet()
                        }
                        5 -> PresetsPage(selectedPresets) { selectedPresets = it }
                        6 -> FocusDurationPage(focusDuration) { focusDuration = it }
                        7 -> GuidePage()
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
                            Text(s.btnBack, color = OnSurface2)
                        }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }

                    if (page in 4..6) {
                        TextButton(onClick = { page = 7 }) {
                            Text(s.btnSkipSetup, color = OnSurface2.copy(alpha = 0.55f), fontSize = 13.sp)
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
                        enabled = if (page == 2) termsAccepted else true,
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (page == totalPages - 1) {
                            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.btnLetsGo, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(if (page == 0) s.btnGetStarted else s.btnNext, fontWeight = FontWeight.SemiBold)
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
private fun LanguageSelectionPage() {
    val scope = rememberCoroutineScope()
    val currentLanguage = LocalizationManager.currentLanguage

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Language, null, tint = Purple80, modifier = Modifier.size(38.dp))
        }

        Text(
            LocalizationManager.strings.langPickerTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            LocalizationManager.strings.langPickerSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                val isSelected = lang == currentLanguage
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
                        .clickable {
                            scope.launch {
                                LocalizationManager.saveLanguage(lang)
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(lang.flag, fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lang.nativeName, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(lang.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
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
private fun WelcomePage() {
    val s = LocalizationManager.strings
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
            s.welcomeTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            s.welcomeSubtitle,
            style = MaterialTheme.typography.titleMedium,
            color = Purple80,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            s.welcomeBody,
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
            FeaturePill(Icons.Default.Block, s.featureEnforcement)
            FeaturePill(Icons.Default.Timer, s.featurePomodoro)
            FeaturePill(Icons.Default.BarChart, s.featureStats)
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
    val s = LocalizationManager.strings
    val goals = buildGoals(s)
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
            s.goalTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            s.goalSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            goals.forEach { goal ->
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
    val s = LocalizationManager.strings
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
            s.presetsTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            s.presetsSubtitle,
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
    val s = LocalizationManager.strings
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
            s.durationTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            s.durationSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DurationOption(
                minutes = 25,
                label = s.duration25Label,
                sublabel = s.duration25Sub,
                isSelected = focusDuration == 25,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 45,
                label = s.duration45Label,
                sublabel = s.duration45Sub,
                isSelected = focusDuration == 45,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 60,
                label = s.duration60Label,
                sublabel = s.duration60Sub,
                isSelected = focusDuration == 60,
                onSelect = onSelect
            )
            DurationOption(
                minutes = 90,
                label = s.duration90Label,
                sublabel = s.duration90Sub,
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
                    s.durationHint,
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
    val s = LocalizationManager.strings
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
            s.privacyTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            s.privacySubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface3)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Storage, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.privacyLocalData, color = OnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(s.privacyLocalDataDesc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
            HorizontalDivider(color = OnSurface2.copy(alpha = 0.12f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.privacyProcessMonitoring, color = OnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(s.privacyProcessMonitoringDesc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
            HorizontalDivider(color = OnSurface2.copy(alpha = 0.12f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = Purple80, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.privacyElevatedPrivileges, color = OnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(s.privacyElevatedPrivilegesDesc, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (accepted) Purple80.copy(alpha = 0.12f) else Surface2)
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
                s.privacyAcceptText,
                color = OnSurface,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        if (!accepted) {
            Text(
                s.privacyAcceptHint,
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
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        autoStartEnabled = withContext(Dispatchers.IO) { WindowsStartupManager.isEnabled() }
    }

    // Header sits outside the scroll so it stays pinned
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape)
                .background(Purple80.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = Purple80, modifier = Modifier.size(28.dp))
        }
        Text(
            LocalizationManager.strings.permissionsTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            LocalizationManager.strings.permissionsSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        // Scrollable card list — capped so dialog doesn't overflow the screen
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
            ) {
                // ── Run as Admin ──────────────────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.AdminPanelSettings,
                    iconTint = Error,
                    title = "Run as Administrator",
                    subtitle = "Required for process kill, firewall rules & Nuclear Mode",
                    badge = if (isAdmin) "✓ Already running as admin" else "Needed for full blocking",
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
                        Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(22.dp))
                    }
                }

                // ── Windows Defender Exclusion ────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.Security,
                    iconTint = Warning,
                    title = "Windows Defender Exclusion",
                    subtitle = "Stops Defender from flagging FocusFlow when it kills blocked processes",
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

                // ── Notifications ─────────────────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.Notifications,
                    iconTint = Purple80,
                    title = "Allow Notifications",
                    subtitle = "Session alerts, blocked-app warnings & weekly focus reports",
                    badge = "Recommended",
                    badgeGranted = null
                ) {
                    if (isWindows) {
                        OutlinedButton(
                            onClick = { openSettingsUrl("ms-settings:notifications") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Open Notifications →", fontSize = 11.sp, color = Purple80)
                        }
                    }
                }

                // ── Focus Assist ──────────────────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.DoNotDisturb,
                    iconTint = Warning,
                    title = "Disable Focus Assist (Do Not Disturb)",
                    subtitle = "Windows DND silences FocusFlow's session & block alerts",
                    badge = "Optional",
                    badgeGranted = null
                ) {
                    if (isWindows) {
                        OutlinedButton(
                            onClick = { openSettingsUrl("ms-settings:quiethours") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Open Focus Assist →", fontSize = 11.sp, color = Purple80)
                        }
                    }
                }

                // ── Auto-start ────────────────────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.Autorenew,
                    iconTint = Success,
                    title = "Auto-Start with Windows",
                    subtitle = "FocusFlow launches automatically when you log in — no admin needed",
                    badge = if (autoStartEnabled) "✓ Enabled" else "Optional",
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
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Purple80,
                            checkedTrackColor = Purple80.copy(alpha = 0.4f)
                        )
                    )
                }

                // ── Windows Firewall ──────────────────────────────────────────
                OnboardingPermRow(
                    icon = Icons.Default.Wifi,
                    iconTint = Purple80,
                    title = "Windows Firewall Rules",
                    subtitle = "Verify outbound block rules FocusFlow adds during network blocking",
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
            }

            // Scroll indicator
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp)
            )
        }

        Text(
            "All of these are also reachable from Settings → Windows Setup & Permissions",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface2.copy(alpha = 0.5f),
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
                true  -> Success
                false -> Error
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
            LocalizationManager.strings.guideTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            LocalizationManager.strings.guideSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface2,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        GuideStep(
            number = 1,
            icon = Icons.Default.Block,
            iconTint = Error,
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
            iconTint = Success,
            title = "Explore Block Defense",
            body = "Enable Network blocking, Keyword Blocker, Nuclear Mode, and Always-On enforcement from Block Defense."
        )
        GuideStep(
            number = 4,
            icon = Icons.Default.BarChart,
            iconTint = Warning,
            title = "Track your progress",
            body = "Stats and Reports show your daily streaks, session history, and productivity trends over time."
        )
    }
}

@Composable
private fun GuideStep(
    number: Int,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,  // kept for call-site brevity; theme colors passed
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
