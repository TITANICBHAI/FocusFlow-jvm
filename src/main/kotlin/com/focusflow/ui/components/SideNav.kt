package com.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.Screen
import com.focusflow.services.FocusSessionService
import com.focusflow.ui.theme.*
import com.focusflow.ui.components.FocusFlowLogo

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val navItems = listOf(
    NavItem(Screen.DASHBOARD,  "Dashboard",  Icons.Default.Home),
    NavItem(Screen.TASKS,      "Tasks",      Icons.Default.CheckCircle),
    NavItem(Screen.FOCUS,      "Focus",      Icons.Default.Timer),
    NavItem(Screen.BLOCK_APPS, "Block Apps", Icons.Default.Block),
    NavItem(Screen.STATS,      "Stats",      Icons.Default.BarChart),
    NavItem(Screen.NOTES,      "Notes",      Icons.Default.EditNote),
    NavItem(Screen.HABITS,     "Habits",     Icons.Default.Loop),
    NavItem(Screen.REPORTS,    "Reports",    Icons.Default.Assessment),
    NavItem(Screen.PROFILE,    "Profile",    Icons.Default.Person),
    NavItem(Screen.SETTINGS,   "Settings",   Icons.Default.Settings)
)

@Composable
fun SideNav(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val session by FocusSessionService.state.collectAsState()

    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Surface2)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            FocusFlowLogo(size = 34.dp, showText = true, textColor = OnSurface)
        }

        Spacer(Modifier.height(8.dp))

        // Live session banner in sidebar
        AnimatedVisibility(
            visible = session.isActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val remaining = session.totalSeconds - session.elapsedSeconds
            val mins = remaining / 60
            val secs = remaining % 60
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Purple80.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (session.isPaused) Warning else Purple80)
                    )
                    Text(
                        if (session.isPaused) "Paused" else "Focusing",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.isPaused) Warning else Purple80,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    "%02d:%02d".format(mins, secs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    session.taskName.take(20) + if (session.taskName.length > 20) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (session.isActive) Spacer(Modifier.height(4.dp))

        navItems.forEach { item ->
            val selected = current == item.screen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Purple80.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onNavigate(item.screen) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) Purple80 else OnSurface2,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) Purple80 else OnSurface2,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                // Focus item: live dot indicator
                if (item.screen == Screen.FOCUS && session.isActive && !selected) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (session.isPaused) Warning else Purple80)
                    )
                }
            }
        }
    }
}
