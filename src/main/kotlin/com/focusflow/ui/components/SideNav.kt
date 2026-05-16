package com.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.Screen
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.FocusSessionService
import com.focusflow.ui.theme.*
import com.focusflow.ui.components.FocusFlowLogo

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)
private data class NavSection(val title: String, val items: List<NavItem>)

@Composable
fun SideNav(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val session     by FocusSessionService.state.collectAsState()
    val scrollState = rememberScrollState()
    val s           = LocalizationManager.strings

    val navSections = listOf(
        NavSection(s.sectionLive, listOf(
            NavItem(Screen.ACTIVE, s.navActiveBlocks, Icons.Default.RadioButtonChecked)
        )),
        NavSection(s.sectionProductivity, listOf(
            NavItem(Screen.DASHBOARD,      s.navDashboard,      Icons.Default.Home),
            NavItem(Screen.TASKS,          s.navTasks,          Icons.Default.CheckCircle),
            NavItem(Screen.FOCUS,          s.navFocus,          Icons.Default.Timer),
            NavItem(Screen.FOCUS_LAUNCHER, s.navFocusLauncher,  Icons.Default.GridView)
        )),
        NavSection(s.sectionBlockControls, listOf(
            NavItem(Screen.BLOCK_APPS,      s.navBlockApps,      Icons.Default.Block),
            NavItem(Screen.KEYWORD_BLOCKER, s.navKeywordBlocker, Icons.Default.TextFields),
            NavItem(Screen.BLOCK_DEFENSE,   s.navBlockDefense,   Icons.Default.Shield),
            NavItem(Screen.VPN_NETWORK,     s.navVpnNetwork,     Icons.Default.VpnLock)
        )),
        NavSection(s.sectionInsights, listOf(
            NavItem(Screen.STATS,   s.navStats,   Icons.Default.BarChart),
            NavItem(Screen.REPORTS, s.navReports, Icons.Default.Assessment)
        )),
        NavSection(s.sectionAccount, listOf(
            NavItem(Screen.PROFILE,  s.navProfile,  Icons.Default.Person),
            NavItem(Screen.SETTINGS, s.navSettings, Icons.Default.Settings)
        ))
    )

    val footerItems = listOf(
        NavItem(Screen.WINDOWS_SETUP, s.navWindowsSetup, Icons.Default.AdminPanelSettings),
        NavItem(Screen.HOW_TO_USE,    s.navHowToUse,     Icons.Default.Help),
        NavItem(Screen.CHANGELOG,     s.navChangelog,    Icons.Default.History)
    )

    Box(
        modifier = modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(Surface2)
            .drawBehind {
                drawRect(
                    color = androidx.compose.ui.graphics.Color(0xFF252436),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 20.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                FocusFlowLogo(size = 32.dp, showText = true, textColor = OnSurface)
            }

            Spacer(Modifier.height(6.dp))

            AnimatedVisibility(
                visible = session.isActive,
                enter   = fadeIn(),
                exit    = fadeOut()
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
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(if (session.isPaused) Warning else Purple80)
                        )
                        Text(
                            if (session.isPaused) s.statusPaused else s.statusFocusing,
                            style      = MaterialTheme.typography.bodySmall,
                            color      = if (session.isPaused) Warning else Purple80,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        "%02d:%02d".format(mins, secs),
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        session.taskName.take(22) + if (session.taskName.length > 22) "…" else "",
                        style   = MaterialTheme.typography.bodySmall,
                        color   = OnSurface2,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            if (session.isActive) Spacer(Modifier.height(6.dp))

            navSections.forEach { section ->
                Spacer(Modifier.height(6.dp))
                Text(
                    section.title,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = OnSurface2.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize  = 9.sp,
                    letterSpacing = 0.8.sp,
                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                section.items.forEach { item ->
                    SideNavItem(
                        item       = item,
                        selected   = current == item.screen,
                        showLiveDot = item.screen == Screen.FOCUS && session.isActive && current != Screen.FOCUS,
                        showActiveDot = item.screen == Screen.ACTIVE,
                        isPaused   = session.isPaused,
                        onClick    = { onNavigate(item.screen) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))

            Divider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(4.dp))

            footerItems.forEach { item ->
                SideNavItem(
                    item          = item,
                    selected      = current == item.screen,
                    showLiveDot   = false,
                    showActiveDot = false,
                    isPaused      = false,
                    onClick       = { onNavigate(item.screen) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun SideNavItem(
    item: NavItem,
    selected: Boolean,
    showLiveDot: Boolean,
    showActiveDot: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Purple80.copy(alpha = 0.13f) else androidx.compose.ui.graphics.Color.Transparent)
            .drawBehind {
                if (selected) {
                    drawRect(
                        color = androidx.compose.ui.graphics.Color(0xFF6C63FF),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * 0.6f)
                    )
                }
            }
            .clickable { onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint     = if (selected) Purple80 else OnSurface2,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            item.label,
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (selected) Purple80 else OnSurface2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize   = 13.sp,
            modifier   = Modifier.weight(1f)
        )
        if (showLiveDot) {
            Box(
                modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(if (isPaused) Warning else Purple80)
            )
        }
        if (showActiveDot) {
            Box(
                modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(Success)
            )
        }
    }
}
