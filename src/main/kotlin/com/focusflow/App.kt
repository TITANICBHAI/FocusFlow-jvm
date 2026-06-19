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
import com.focusflow.enforcement.NuclearMode
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.RegistryLockdown
import com.focusflow.i18n.LocalizationManager
import com.focusflow.services.FocusSessionService
import kotlin.system.exitProcess
import com.focusflow.ui.components.AndroidPromoDialog
import com.focusflow.ui.components.BlockOverlay
import com.focusflow.ui.components.FocusLauncherBreakBanner
import com.focusflow.ui.components.FocusLauncherOverlay
import com.focusflow.ui.components.GlobalPinSetupDialog
import com.focusflow.ui.components.OsBanner
import com.focusflow.ui.components.OnboardingDialog
import com.focusflow.ui.components.ReviewPromptDialog
import com.focusflow.ui.components.SideNav
import com.focusflow.ui.components.TelemetryConsentDialog
import com.focusflow.services.FocusLauncherService
import com.focusflow.services.GlobalPin
import com.focusflow.ui.screens.*
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.*
import com.focusflow.ui.LocalNavigate

@Composable
fun App() {
    var currentScreen         by remember { mutableStateOf(Screen.DASHBOARD) }
    var dashboardRefreshKey   by remember { mutableStateOf(0) }
    var focusPreloadTask by remember { mutableStateOf<Task?>(null) }
    var overlayVisible   by remember { mutableStateOf(false) }
    var overlayAppName   by remember { mutableStateOf("") }
    var showOnboarding      by remember { mutableStateOf(false) }
    var showGlobalPinSetup  by remember { mutableStateOf(false) }
    var showAndroidPromo    by remember { mutableStateOf(false) }
    var showReviewPrompt    by remember { mutableStateOf(false) }
    var showTelemetryConsent     by remember { mutableStateOf(false) }
    var showRegistryOrphanDialog by remember { mutableStateOf(false) }
    val scope               = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        LocalizationManager.loadSavedLanguage()
        withContext(Dispatchers.IO) {
            when (Database.getSetting("theme_mode")) {
                "light" -> applyLightTheme()
                else    -> applyDarkTheme()
            }
        }
        AppBlocker.onOverlayShow = { appName ->
            overlayAppName = appName
            overlayVisible = true
        }
        AppBlocker.onOverlayHide = {
            overlayVisible = false
        }
        val launchData = withContext(Dispatchers.IO) {
            val fl = Database.getSetting("onboarding_complete") != "true"
            val pn = !GlobalPin.isSet() && !GlobalPin.isDeclined()

            val openCount = (Database.getSetting("app_open_count")?.toIntOrNull() ?: 0) + 1
            Database.setSetting("app_open_count", openCount.toString())

            val showAndroid = openCount >= 10
                && Database.getSetting("android_promo_shown") != "true"
                && !fl
            val showReview = openCount >= 15
                && Database.getSetting("review_prompt_shown") != "true"
                && !fl
                && !showAndroid

            // Show telemetry consent on the 2nd launch (after onboarding is done)
            // if the user has never been asked (null = never set, as opposed to "true"/"false").
            val showConsent = !fl
                && Database.getSetting("crash_reports_enabled") == null

            if (showAndroid) Database.setSetting("android_promo_shown", "true")
            if (showReview)  Database.setSetting("review_prompt_shown", "true")

            listOf(fl, pn, showAndroid, showReview, showConsent)
        }
        val firstLaunch  = launchData[0]
        val pinNeeded    = launchData[1]
        val androidPromo = launchData[2]
        val reviewPrompt = launchData[3]
        val needsConsent = launchData[4]

        if (firstLaunch) showOnboarding = true
        if (pinNeeded && !firstLaunch) showGlobalPinSetup = true
        if (androidPromo) showAndroidPromo = true
        if (reviewPrompt) showReviewPrompt = true
        if (needsConsent) showTelemetryConsent = true
    }

    // ── Registry orphan check ─────────────────────────────────────────────────
    // After services have fully settled (4 s), check whether DisableTaskMgr is
    // stuck in HKCU when no enforcement session is active.  Two-pass detection
    // inside detectOrphanedKeys() means we only reach here if (a) the key is
    // confirmed == 1 AND (b) a fresh disable() pass still couldn't clear it —
    // virtually eliminating false positives.  Runs on IO dispatcher (JNA blocks).
    LaunchedEffect(Unit) {
        delay(4_000L)
        val orphaned = withContext(Dispatchers.IO) {
            val nuclearOn = try { NuclearMode.isActive } catch (_: Throwable) { true }
            val kioskOn   = try { FocusLauncherService.isActive.value } catch (_: Throwable) { true }
            if (nuclearOn || kioskOn) false
            else RegistryLockdown.detectOrphanedKeys()
        }
        if (orphaned) showRegistryOrphanDialog = true
    }

    // Clear the focus preload task whenever the user navigates away from the Focus
    // screen. Without this, returning to FocusScreen later (via the side nav, not
    // via "Start Focus") would still pre-populate the old task from the prior visit.
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.FOCUS) {
            focusPreloadTask = null
        }
    }

    // During any launcher session (kiosk active OR break active), the fullscreen
    // overlay covers the UI. ThemeToggleButton is declared AFTER FocusLauncherOverlay
    // in the Box, giving it higher hit-test priority in Compose. Hide it completely
    // while the launcher is running so no UI element can receive clicks above the overlay.
    val launcherActive by FocusLauncherService.isActive.collectAsState()

    FocusFlowTheme {
        val sessionState by FocusSessionService.state.collectAsState()
        val navigate: (Screen) -> Unit = { dest ->
            if (dest == Screen.DASHBOARD) dashboardRefreshKey++
            currentScreen = dest
        }
        CompositionLocalProvider(LocalNavigate provides navigate) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                    // Ctrl+H works even during an active session (read-only help)
                    if (event.key == Key.H) { navigate(Screen.HOW_TO_USE); return@onPreviewKeyEvent true }
                    if (sessionState.isActive) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.One   -> { navigate(Screen.DASHBOARD);  true }
                        Key.Two   -> { navigate(Screen.TASKS);      true }
                        Key.Three -> { navigate(Screen.FOCUS);      true }
                        Key.Four  -> { navigate(Screen.BLOCK_APPS); true }
                        Key.Five  -> { navigate(Screen.STATS);      true }
                        Key.Comma -> { navigate(Screen.SETTINGS);   true }
                        else      -> false
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FocusLauncherBreakBanner()
                OsBanner()

                Row(modifier = Modifier.weight(1f)) {
                    SideNav(
                        current    = currentScreen,
                        onNavigate = navigate
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
                                Screen.BLOCK_DEFENSE  -> BlockDefenseScreen(onNavigateToVpn = { currentScreen = Screen.VPN_NETWORK }, onNavigateToAppBlocker = { currentScreen = Screen.BLOCK_APPS })
                                Screen.VPN_NETWORK    -> VpnNetworkScreen()
                                Screen.HOW_TO_USE     -> HowToUseScreen()
                                Screen.CHANGELOG      -> ChangelogScreen()
                                Screen.WINDOWS_SETUP  -> WindowsSetupScreen()
                                Screen.CONTACT        -> ContactScreen()
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

            // Hidden during any launcher session — the overlay must be fully impenetrable.
            // ThemeToggleButton is declared after FocusLauncherOverlay in this Box, which
            // gives it higher Compose hit-test priority; hiding it prevents clicks from
            // leaking through the overlay to the theme toggle in the top-right corner.
            if (!launcherActive) {
                ThemeToggleButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 14.dp)
                )
            }
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

        if (showAndroidPromo) {
            AndroidPromoDialog(onDismiss = { showAndroidPromo = false })
        }

        if (showReviewPrompt) {
            ReviewPromptDialog(onDismiss = { showReviewPrompt = false })
        }

        if (showTelemetryConsent) {
            TelemetryConsentDialog(
                onAllow = {
                    showTelemetryConsent = false
                    scope.launch(Dispatchers.IO) {
                        Database.setSetting("crash_reports_enabled", "true")
                    }
                },
                onDecline = {
                    showTelemetryConsent = false
                    scope.launch(Dispatchers.IO) {
                        Database.setSetting("crash_reports_enabled", "false")
                    }
                }
            )
        }

        if (showRegistryOrphanDialog) {
            AlertDialog(
                onDismissRequest = { showRegistryOrphanDialog = false },
                containerColor   = Surface2,
                titleContentColor = OnSurface,
                textContentColor  = OnSurface2,
                title = {
                    Text(
                        "Task Manager May Be Disabled",
                        style = MaterialTheme.typography.titleMedium,
                        color = Warning
                    )
                },
                text = {
                    Text(
                        "FocusFlow detected that Windows Task Manager appears disabled " +
                        "in the registry, but no blocking session is currently active.\n\n" +
                        "This is usually caused by a previous session that closed " +
                        "unexpectedly before it could restore your settings.\n\n" +
                        "Restarting FocusFlow as Administrator will allow it to " +
                        "remove the stuck registry entry and restore normal access.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRegistryOrphanDialog = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val exe = ProcessHandle.current().info().command().orElse(null)
                                    if (exe != null) {
                                        val escaped = exe.replace("'", "''")
                                        ProcessBuilder(
                                            "powershell", "-WindowStyle", "Hidden",
                                            "-Command",
                                            "Start-Process -FilePath '$escaped' -Verb RunAs"
                                        ).start()
                                        exitProcess(0)
                                    }
                                } catch (_: Throwable) {}
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Text("Restart as Admin", color = Surface)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegistryOrphanDialog = false }) {
                        Text("Dismiss", color = OnSurface2)
                    }
                }
            )
        }
        } // CompositionLocalProvider
    }
}

@Composable
private fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val dark = isDarkTheme
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = {
            if (dark) {
                applyLightTheme()
                scope.launch(Dispatchers.IO) { Database.setSetting("theme_mode", "light") }
            } else {
                applyDarkTheme()
                scope.launch(Dispatchers.IO) { Database.setSetting("theme_mode", "dark") }
            }
        },
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
