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
import com.focusflow.ui.components.OsBanner
import com.focusflow.ui.components.OnboardingDialog
import com.focusflow.ui.components.SideNav
import com.focusflow.ui.screens.*
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun App() {
    var currentScreen    by remember { mutableStateOf(Screen.DASHBOARD) }
    var focusPreloadTask by remember { mutableStateOf<Task?>(null) }
    var overlayVisible   by remember { mutableStateOf(false) }
    var overlayAppName   by remember { mutableStateOf("") }
    var showOnboarding   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppBlocker.onOverlayShow = { appName ->
            overlayAppName = appName
            overlayVisible = true
        }
        AppBlocker.onOverlayHide = {
            overlayVisible = false
        }
        val firstLaunch = withContext(Dispatchers.IO) {
            Database.getSetting("onboarding_complete") != "true"
        }
        if (firstLaunch) showOnboarding = true
    }

    FocusFlowTheme {
        Box(modifier = Modifier.fillMaxSize().background(Surface)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OsBanner()

                Row(modifier = Modifier.weight(1f)) {
                    SideNav(
                        current    = currentScreen,
                        onNavigate = { currentScreen = it }
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
                                Screen.FOCUS          -> FocusScreen(preloadTask = focusPreloadTask)
                                Screen.BLOCK_APPS     -> AppBlockerScreen()
                                Screen.STATS          -> StatsScreen()
                                Screen.NOTES          -> DailyNotesScreen()
                                Screen.HABITS         -> HabitsScreen()
                                Screen.REPORTS        -> ReportsScreen()
                                Screen.PROFILE        -> ProfileScreen()
                                Screen.SETTINGS       -> SettingsScreen()
                                Screen.ACTIVE         -> ActiveScreen()
                                Screen.KEYWORD_BLOCKER -> KeywordBlockerScreen()
                                Screen.BLOCK_DEFENSE  -> BlockDefenseScreen()
                                Screen.HOW_TO_USE     -> HowToUseScreen()
                                Screen.CHANGELOG      -> ChangelogScreen()
                                Screen.WINDOWS_SETUP  -> WindowsSetupScreen()
                            }
                        }
                    }
                }
            }

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
                Database.setSetting("onboarding_complete", "true")
            })
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
