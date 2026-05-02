package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.focusflow.data.models.Screen
import com.focusflow.ui.theme.*

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val navItems = listOf(
    NavItem(Screen.DASHBOARD, "Dashboard", Icons.Default.Home),
    NavItem(Screen.TASKS,     "Tasks",     Icons.Default.CheckCircle),
    NavItem(Screen.FOCUS,     "Focus",     Icons.Default.Timer),
    NavItem(Screen.STATS,     "Stats",     Icons.Default.BarChart),
    NavItem(Screen.NOTES,     "Notes",     Icons.Default.EditNote),
    NavItem(Screen.REPORTS,   "Reports",   Icons.Default.Assessment),
    NavItem(Screen.PROFILE,   "Profile",   Icons.Default.Person),
    NavItem(Screen.SETTINGS,  "Settings",  Icons.Default.Settings)
)

@Composable
fun SideNav(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Surface2)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.FlashOn,
                contentDescription = null,
                tint = Purple80,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "FocusFlow",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
        }

        Spacer(Modifier.height(16.dp))

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
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                 else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
    }
}
