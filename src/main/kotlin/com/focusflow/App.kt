package com.focusflow

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focusflow.data.Database
import com.focusflow.data.models.Screen
import com.focusflow.data.models.Task
import com.focusflow.enforcement.AppBlocker
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.services.FocusSessionService
import com.focusflow.ui.components.BlockOverlay
import com.focusflow.ui.components.FocusLauncherBreakBanner
import com.focusflow.ui.components.FocusLauncherOverlay
import com.focusflow.ui.components.GlobalPinSetupDialog
import com.focusflow.ui.components.OsBanner
import com.focusflow.ui.components.OnboardingDialog
import com.focusflow.ui.components.SideNav
import com.focusflow.services.FocusLauncherService
import com.focusflow.services.GlobalPin
import com.focusflow.ui.screens.*
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App() {
    var currentScreen         by remember { mutableStateOf(Screen.DASHBOARD) }
    var dashboardRefreshKey   by remember { mutableStateOf(0) }
    var focusPreloadTask by remember { mutableStateOf<Task?>(null) }
    var overlayVisible   by remember { mutableStateOf(false) }
    var overlayAppName   by remember { mutableStateOf("") }
    var showOnboarding      by remember { mutableStateOf(false) }
    var showGlobalPinSetup  by remember { mutableStateOf(false) }
    val scope               = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppBlocker.onOverlayShow = { appName ->
            overlayAppName = appName
            overlayVisible = true
        }
        AppBlocker.onOverlayHide = {
            overlayVisible = false
        }
        val (firstLaunch, pinNeeded) = withContext(Dispatchers.IO) {
            val fl = Database.getSetting("onboarding_complete") != "true"
            val pn = !GlobalPin.isSet() && !GlobalPin.isDeclined()
            Pair(fl, pn)
        }
        if (firstLaunch) showOnboarding = true
        if (pinNeeded && !firstLaunch) showGlobalPinSetup = true
    }

    FocusFlowTheme {
        Box(modifier = Modifier.fillMaxSize().background(Surface)) {
            Column(modifier = Modifier.fillMaxSize()) {
                FocusLauncherBreakBanner()
                OsBanner()

                Row(modifier = Modifier.weight(1f)) {
                    SideNav(
                        current    = currentScreen,
                        onNavigate = { dest ->
                            if (dest == Screen.DASHBOARD) dashboardRefreshKey++
                            currentScreen = dest
                        }
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn() + slideInHorizontally { it / 8 } togetherWith
                                fadeOut() + slideOutHorizontally { -it / 8 }
                            }
                        ) { screen ->
                            when (screen) {
                                Screen.DASHBOARD -> DashboardScreen(
                                    refreshKey = dashboardRefreshKey,
                                    onStartFocus = { task ->
                                        focusPreloadTask = task
                                        currentScreen = Screen.FOCUS
                                    },
                                    onNavigateTasks = { currentScreen = Screen.TASKS }
                                )
                                Screen.TASKS -> TasksScreen(
                                    onStartFocus = { task ->
                                        focusPreloadTask = task
                                        currentScreen = Screen.FOCUS
                                    }
                                )
                                Screen.FOCUS           -> FocusScreen(preloadTask = focusPreloadTask)
                                Screen.FOCUS_LAUNCHER  -> FocusLauncherScreen()
                                Screen.BLOCK_APPS      -> AppBlockerScreen()
                                Screen.STATS          -> StatsScreen()
                                Screen.NOTES          -> DailyNotesScreen()
                                Screen.HABITS         -> HabitsScreen()
                                Screen.REPORTS        -> ReportsScreen()
                                Screen.PROFILE        -> ProfileScreen()
                                Screen.SETTINGS       -> SettingsScreen()
                                Screen.ACTIVE         -> ActiveScreen(onNavigate = { currentScreen = it })
                                Screen.KEYWORD_BLOCKER -> KeywordBlockerScreen()
                                Screen.BLOCK_DEFENSE  -> BlockDefenseScreen(onNavigate = { currentScreen = it })
                                Screen.VPN_NETWORK    -> VpnNetworkScreen()
                                Screen.HOW_TO_USE     -> HowToUseScreen()
                                Screen.CHANGELOG      -> ChangelogScreen()
                                Screen.WINDOWS_SETUP  -> WindowsSetupScreen()
                            }
                        }
                    }
                }
            }

            FocusLauncherOverlay()

            BlockOverlay(
                visible   = overlayVisible,
                appName   = overlayAppName,
                onDismiss = { AppBlocker.hideOverlay() }
            )

            ThemeToggleButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
            )
        }

        if (showOnboarding) {
            OnboardingDialog(onDismiss = {
                showOnboarding = false
                scope.launch(Dispatchers.IO) {
                    Database.setSetting("onboarding_complete", "true")
                }
                showGlobalPinSetup = true
            })
        }

        if (showGlobalPinSetup) {
            GlobalPinSetupDialog(onDismiss = { showGlobalPinSetup = false })
        }
    }
}

@Composable
private fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val dark = isDarkTheme
    IconButton(
        onClick = { if (dark) applyLightTheme() else applyDarkTheme() },
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Surface3)
    ) {
        Icon(
            imageVector = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
            tint = if (dark) Purple60 else Purple80,
            modifier = Modifier.size(18.dp)
        )
    }
}
