package com.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.focusflow.ui.components.FfVerticalScrollbar
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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.data.models.Screen
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.FocusSessionService
import com.focusflow.services.WeeklyReportService
import com.focusflow.ui.theme.*
import com.focusflow.ui.components.FocusFlowLogo
import com.focusflow.ui.components.openUrl
import com.focusflow.ui.components.ShareDialog
import com.focusflow.ui.components.ShortcutTooltip

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector, val shortcut: String? = null)
private data class NavSection(val title: String, val items: List<NavItem>)

@Composable
fun SideNav(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val session       by FocusSessionService.state.collectAsState()
    val hasNewReport  by WeeklyReportService.hasNewReport.collectAsState()
    val scrollState   = rememberScrollState()
    val s             = LocalizationManager.strings
    var showShare       by remember { mutableStateOf(false) }
    var showAndroidDlg  by remember { mutableStateOf(false) }

    val navSections = listOf(
        NavSection(s.sectionLive, listOf(
            NavItem(Screen.FOCUS,          s.navFocus,          Icons.Default.Timer,                shortcut = "Ctrl+3"),
            NavItem(Screen.ACTIVE,         s.navActiveBlocks,   Icons.Default.RadioButtonChecked),
            NavItem(Screen.FOCUS_LAUNCHER, s.navFocusLauncher,  Icons.Default.GridView)
        )),
        NavSection(s.sectionProductivity, listOf(
            NavItem(Screen.DASHBOARD, s.navDashboard, Icons.Default.Home,        shortcut = "Ctrl+1"),
            NavItem(Screen.TASKS,     s.navTasks,     Icons.Default.CheckCircle, shortcut = "Ctrl+2")
        )),
        NavSection(s.sectionBlockControls, listOf(
            NavItem(Screen.BLOCK_APPS,      s.navBlockApps,      Icons.Default.Block,        shortcut = "Ctrl+4"),
            NavItem(Screen.KEYWORD_BLOCKER, s.navKeywordBlocker, Icons.Default.TextFields),
            NavItem(Screen.BLOCK_DEFENSE,   s.navBlockDefense,   Icons.Default.Shield),
            NavItem(Screen.VPN_NETWORK,     s.navVpnNetwork,     Icons.Default.VpnLock)
        )),
        NavSection(s.sectionInsights, listOf(
            NavItem(Screen.STATS,   s.navStats,   Icons.Default.BarChart,  shortcut = "Ctrl+5"),
            NavItem(Screen.REPORTS, s.navReports, Icons.Default.Assessment)
        )),
        NavSection(s.sectionAccount, listOf(
            NavItem(Screen.PROFILE,  s.navProfile,  Icons.Default.Person),
            NavItem(Screen.SETTINGS, s.navSettings, Icons.Default.Settings, shortcut = "Ctrl+,")
        ))
    )

    val footerItems = listOf(
        NavItem(Screen.WINDOWS_SETUP, s.navWindowsSetup, Icons.Default.AdminPanelSettings),
        NavItem(Screen.HOW_TO_USE,    s.navHowToUse,     Icons.AutoMirrored.Filled.Help,  shortcut = "Ctrl+H"),
        NavItem(Screen.CHANGELOG,     s.navChangelog,    Icons.Default.History),
        NavItem(Screen.CONTACT,       "Contact & Reports", Icons.Default.BugReport)
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

            // ── Session mini-card: slides down when a session is active ────────
            AnimatedVisibility(
                visible = session.isActive,
                enter   = expandVertically(tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                exit    = shrinkVertically(tween(250)) + fadeOut(tween(200))
            ) {
                val remaining = session.totalSeconds - session.elapsedSeconds
                val mins = remaining / 60
                val secs = remaining % 60

                // Pulsing dot inside the mini-card
                val dotPulse = rememberInfiniteTransition(label = "miniCardDot")
                val dotScale by dotPulse.animateFloat(
                    initialValue  = 0.75f,
                    targetValue   = 1.30f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(750, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "miniDotScale"
                )

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
                            modifier = Modifier
                                .scale(dotScale)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (session.isPaused) Warning else Purple80)
                        )
                        Text(
                            if (session.isPaused) s.statusPaused else s.statusFocusing,
                            style         = MaterialTheme.typography.bodySmall,
                            color         = if (session.isPaused) Warning else Purple80,
                            fontWeight    = FontWeight.SemiBold,
                            fontSize      = 10.sp,
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
                        style    = MaterialTheme.typography.bodySmall,
                        color    = OnSurface2,
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
                    style         = MaterialTheme.typography.labelSmall,
                    color         = OnSurface2.copy(alpha = 0.6f),
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 9.sp,
                    letterSpacing = 0.8.sp,
                    modifier      = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                section.items.forEach { item ->
                    SideNavItem(
                        item          = item,
                        selected      = current == item.screen,
                        showLiveDot   = item.screen == Screen.FOCUS && session.isActive && current != Screen.FOCUS,
                        showActiveDot = item.screen == Screen.ACTIVE,
                        showBadge     = hasNewReport && item.screen == Screen.REPORTS,
                        isPaused      = session.isPaused,
                        onClick       = {
                            if (item.screen == Screen.REPORTS) WeeklyReportService.dismissNewReportBadge()
                            onNavigate(item.screen)
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))

            HorizontalDivider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
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
            HorizontalDivider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "MOBILE",
                style         = MaterialTheme.typography.labelSmall,
                color         = OnSurface2.copy(alpha = 0.45f),
                fontWeight    = FontWeight.Bold,
                fontSize      = 9.sp,
                letterSpacing = 0.8.sp,
                modifier      = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface3.copy(alpha = 0.5f))
                    .clickable { showAndroidDlg = true }
                    .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = "Android App",
                    tint     = OnSurface2,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    LocalizationManager.strings.navAndroidApp,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = OnSurface2,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 12.sp,
                    modifier   = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint     = OnSurface2.copy(alpha = 0.5f),
                    modifier = Modifier.size(11.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { showShare = true }
                    .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint     = OnSurface2,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    LocalizationManager.strings.shareTitle,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = OnSurface2,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        FfVerticalScrollbar(
            scrollState = scrollState,
            modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
        )
    }

    if (showShare) {
        ShareDialog(onDismiss = { showShare = false })
    }

    if (showAndroidDlg) {
        AndroidPromoDialog(onDismiss = { showAndroidDlg = false })
    }
}

@Composable
private fun SideNavItem(
    item: NavItem,
    selected: Boolean,
    showLiveDot: Boolean,
    showActiveDot: Boolean,
    showBadge: Boolean = false,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    val rowContent: @Composable () -> Unit = { SideNavItemRow(item, selected, showLiveDot, showActiveDot, showBadge, isPaused, onClick) }
    if (item.shortcut != null) {
        ShortcutTooltip(shortcut = item.shortcut, delayMillis = 500) { rowContent() }
    } else {
        rowContent()
    }
}

@Composable
private fun SideNavItemRow(
    item: NavItem,
    selected: Boolean,
    showLiveDot: Boolean,
    showActiveDot: Boolean,
    showBadge: Boolean = false,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    // Animate the selection state: background, icon tint, and label colour all
    // crossfade smoothly instead of snapping on every navigation change.
    val bgColor by animateColorAsState(
        targetValue   = if (selected) Purple80.copy(alpha = 0.13f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(200),
        label         = "navBg"
    )
    val accentColor by animateColorAsState(
        targetValue   = if (selected) Purple80 else OnSurface2,
        animationSpec = tween(200),
        label         = "navAccent"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .drawBehind {
                if (selected) {
                    drawRect(
                        color    = androidx.compose.ui.graphics.Color(0xFF6C63FF),
                        topLeft  = androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f),
                        size     = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * 0.6f)
                    )
                }
            }
            .clickable { onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint     = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            item.label,
            style      = MaterialTheme.typography.bodyMedium,
            color      = accentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize   = 13.sp,
            modifier   = Modifier.weight(1f)
        )

        // Live session dot — pulses while a session is running
        if (showLiveDot) {
            val dotTransition = rememberInfiniteTransition(label = "liveNavDot")
            val dotAlpha by dotTransition.animateFloat(
                initialValue  = 0.35f,
                targetValue   = 1.00f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "navDotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background((if (isPaused) Warning else Purple80).copy(alpha = dotAlpha))
            )
        }
        if (showActiveDot) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Success))
        }
        if (showBadge) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Warning))
        }
    }
}
