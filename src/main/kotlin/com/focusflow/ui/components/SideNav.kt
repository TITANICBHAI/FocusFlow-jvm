package com.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
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

/** Expanded sidebar width */
private val NAV_WIDTH_EXPANDED = 210.dp
/** Collapsed (icon-only) sidebar width */
private val NAV_WIDTH_COLLAPSED = 64.dp

@Composable
fun SideNav(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val session       by FocusSessionService.state.collectAsState()
    val hasNewReport  by WeeklyReportService.hasNewReport.collectAsState()
    val scrollState   = rememberScrollState()
    val s             = LocalizationManager.strings
    var showShare       by remember { mutableStateOf(false) }
    var showAndroidDlg  by remember { mutableStateOf(false) }

    // Animate the sidebar width between expanded and collapsed
    val navWidth by animateDpAsState(
        targetValue   = if (collapsed) NAV_WIDTH_COLLAPSED else NAV_WIDTH_EXPANDED,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label         = "sideNavWidth"
    )

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
            NavItem(Screen.BLOCK_DEFENSE,      s.navBlockDefense,      Icons.Default.Shield),
            NavItem(Screen.STANDALONE_BLOCK,   s.navStandaloneBlock,   Icons.Default.Timer),
            NavItem(Screen.BLOCK_APPS,         s.navBlockApps,         Icons.Default.Block,  shortcut = "Ctrl+4"),
            NavItem(Screen.KEYWORD_BLOCKER, s.navKeywordBlocker, Icons.Default.TextFields),
            NavItem(Screen.NUCLEAR_MODE,    s.settingsNuclearMode, Icons.Default.Warning),
            NavItem(Screen.VPN_NETWORK,     s.navVpnNetwork,      Icons.Default.VpnLock)
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
            .width(navWidth)
            .fillMaxHeight()
            .background(Surface2)
            .drawBehind {
                drawRect(
                    color    = androidx.compose.ui.graphics.Color(0xFF252436),
                    topLeft  = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 0f),
                    size     = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    vertical   = 20.dp,
                    horizontal = if (collapsed) 6.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Logo ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (collapsed) 0.dp else 10.dp,
                        vertical   = 6.dp
                    ),
                contentAlignment = if (collapsed) Alignment.Center else Alignment.CenterStart
            ) {
                FocusFlowLogo(size = 32.dp, showText = !collapsed, textColor = OnSurface)
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

                if (collapsed) {
                    // Collapsed: show only the pulsing dot + timer, centered
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Purple80.copy(alpha = 0.15f))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .scale(dotScale)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (session.isPaused) Warning else Purple80)
                        )
                        Text(
                            "%02d:%02d".format(mins, secs),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 9.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Purple80.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.spacedBy(6.dp)
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
            }

            if (session.isActive) Spacer(Modifier.height(6.dp))

            // ── Nav sections ──────────────────────────────────────────────────
            navSections.forEach { section ->
                Spacer(Modifier.height(6.dp))
                // Section header — hidden when collapsed
                if (!collapsed) {
                    Text(
                        section.title,
                        style         = MaterialTheme.typography.labelSmall,
                        color         = OnSurface2.copy(alpha = 0.6f),
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 9.sp,
                        letterSpacing = 0.8.sp,
                        modifier      = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                section.items.forEach { item ->
                    SideNavItem(
                        item          = item,
                        selected      = current == item.screen,
                        showLiveDot   = item.screen == Screen.FOCUS && session.isActive && current != Screen.FOCUS,
                        showActiveDot = item.screen == Screen.ACTIVE,
                        showBadge     = hasNewReport && item.screen == Screen.REPORTS,
                        isPaused      = session.isPaused,
                        collapsed     = collapsed,
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

            // ── Footer nav items ──────────────────────────────────────────────
            footerItems.forEach { item ->
                SideNavItem(
                    item          = item,
                    selected      = current == item.screen,
                    showLiveDot   = false,
                    showActiveDot = false,
                    isPaused      = false,
                    collapsed     = collapsed,
                    onClick       = { onNavigate(item.screen) }
                )
            }

            // ── Mobile + Share (hidden when collapsed) ────────────────────────
            if (!collapsed) {
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
            }

            // ── Collapse / Expand toggle ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Surface3, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleCollapse() }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = if (collapsed)
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = if (collapsed) "Expand sidebar" else "Collapse sidebar",
                    tint               = OnSurface2,
                    modifier           = Modifier.size(18.dp)
                )
                if (!collapsed) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Collapse",
                        style      = MaterialTheme.typography.bodySmall,
                        color      = OnSurface2,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        if (!collapsed) {
            FfVerticalScrollbar(
                scrollState = scrollState,
                modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
            )
        }
    }

    if (showShare) {
        ShareDialog(onDismiss = { showShare = false })
    }

    if (showAndroidDlg) {
        AndroidPromoDialog(onDismiss = { showAndroidDlg = false })
    }
}

// ── Nav item wrappers ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideNavItem(
    item: NavItem,
    selected: Boolean,
    showLiveDot: Boolean,
    showActiveDot: Boolean,
    showBadge: Boolean = false,
    isPaused: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit
) {
    val rowContent: @Composable () -> Unit = {
        SideNavItemRow(item, selected, showLiveDot, showActiveDot, showBadge, isPaused, collapsed, onClick)
    }

    when {
        // Collapsed: show the item label as a tooltip on hover
        collapsed -> {
            TooltipArea(
                tooltip = {
                    // Label badge, matches ShortcutBadge style
                    Text(
                        text       = item.label,
                        color      = OnSurface2,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier
                            .shadow(4.dp, RoundedCornerShape(6.dp))
                            .background(Surface3, RoundedCornerShape(6.dp))
                            .border(1.dp, OnSurface2.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                },
                delayMillis      = 300,
                tooltipPlacement = TooltipPlacement.CursorPoint(
                    alignment = Alignment.CenterEnd,
                    offset    = DpOffset(8.dp, 0.dp)
                )
            ) {
                rowContent()
            }
        }
        // Expanded + has keyboard shortcut: show the shortcut tooltip
        item.shortcut != null -> {
            ShortcutTooltip(shortcut = item.shortcut, delayMillis = 500) { rowContent() }
        }
        // Expanded, no shortcut
        else -> rowContent()
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
    collapsed: Boolean,
    onClick: () -> Unit
) {
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
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
            .padding(
                if (collapsed)
                    PaddingValues(vertical = 9.dp)
                else
                    PaddingValues(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
            )
    ) {
        // Badge dot overlay for collapsed badges/live dots — drawn as a small indicator
        // on top of the icon via a Box wrapper
        if (collapsed) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint     = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                // Indicator dot (badge / live-session / active blocks)
                val dotColor = when {
                    showBadge     -> Warning
                    showLiveDot   -> if (isPaused) Warning else Purple80
                    showActiveDot -> Success
                    else          -> null
                }
                if (dotColor != null) {
                    val dotTransition = rememberInfiniteTransition(label = "collapsedDot_${item.screen}")
                    val dotAlpha by dotTransition.animateFloat(
                        initialValue  = if (showLiveDot) 0.4f else 1f,
                        targetValue   = 1f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "collapsedDotAlpha_${item.screen}"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = dotAlpha))
                    )
                }
            }
        } else {
            // Expanded layout
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

            // Live session dot
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
}
