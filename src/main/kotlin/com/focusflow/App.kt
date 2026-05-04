package com.focusflow

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        }

        if (showOnboarding) {
            OnboardingDialog(onDismiss = {
                showOnboarding = false
                Database.setSetting("onboarding_complete", "true")
            })
        }
    }
}
