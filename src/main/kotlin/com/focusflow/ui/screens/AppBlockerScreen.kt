package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.i18n.LocalizationManager
import com.focusflow.ui.theme.*

/**
 * AppBlockerScreen
 *
 * Root composable for the app-blocking feature.  Renders a three-tab layout
 * and delegates to the focused composables in the companion files:
 *
 *   AppBlockerScreenIcons.kt      — AppIcon composable + brand-color map
 *   AppBlockerAlwaysBlockTab.kt   — AlwaysBlockTab + BlockRuleCard
 *   AppBlockerAllowanceTab.kt     — DailyAllowanceTab + AllowanceCard + dialogs
 *   AppBlockerTimedBlockTab.kt    — TimedBlockTab + DateTimePicker + AppPickerDialog
 */
@Composable
fun AppBlockerScreen() {
    val strings     = LocalizationManager.strings
    var selectedTab by remember { mutableStateOf(0) }
    val tabs     = listOf(strings.blockerTabAlwaysBlock, strings.blockerTabBlockForTime, strings.blockerTabDailyAllowance)
    val tabIcons = listOf(Icons.Default.Block, Icons.Default.Timer, Icons.Default.Timelapse)

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface2).padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Block, null, tint = Purple80, modifier = Modifier.size(28.dp))
            Column {
                Text(strings.blockerTitle, style = MaterialTheme.typography.headlineMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(strings.blockerSubtitle, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
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
                            color      = if (selectedTab == index) Purple80 else OnSurface2
                        )
                    },
                    icon = {
                        Icon(
                            tabIcons[index], null,
                            tint     = if (selectedTab == index) Purple80 else OnSurface2,
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
