package com.focusflow

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.focusflow.data.models.Screen
import com.focusflow.data.models.Task
import com.focusflow.enforcement.AppBlocker
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.services.FocusSessionService
import com.focusflow.ui.components.BlockOverlay
import com.focusflow.ui.components.SideNav
import com.focusflow.ui.screens.*
import com.focusflow.ui.theme.*

@Composable
fun App() {
    var currentScreen   by remember { mutableStateOf(Screen.DASHBOARD) }
    var focusPreloadTask by remember { mutableStateOf<Task?>(null) }
    var overlayVisible  by remember { mutableStateOf(false) }
    var overlayAppName  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AppBlocker.onOverlayShow = { appName ->
            overlayAppName = appName
            overlayVisible = true
        }
        AppBlocker.onOverlayHide = {
            overlayVisible = false
        }
    }

    FocusFlowTheme {
        Box(modifier = Modifier.fillMaxSize().background(Surface)) {
            Row(modifier = Modifier.fillMaxSize()) {
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
                            Screen.FOCUS    -> FocusScreen(preloadTask = focusPreloadTask)
                            Screen.STATS    -> StatsScreen()
                            Screen.NOTES    -> DailyNotesScreen()
                            Screen.HABITS   -> HabitsScreen()
                            Screen.REPORTS  -> ReportsScreen()
                            Screen.PROFILE  -> ProfileScreen()
                            Screen.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }

            BlockOverlay(
                visible  = overlayVisible,
                appName  = overlayAppName,
                onDismiss = { AppBlocker.hideOverlay() }
            )
        }
    }
}
