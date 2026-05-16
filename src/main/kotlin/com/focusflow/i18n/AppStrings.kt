package com.focusflow.i18n

data class AppStrings(
    // ── Navigation sections ──────────────────────────────────────────────────
    val sectionLive: String,
    val sectionProductivity: String,
    val sectionBlockControls: String,
    val sectionInsights: String,
    val sectionAccount: String,

    // ── Navigation items ─────────────────────────────────────────────────────
    val navActiveBlocks: String,
    val navDashboard: String,
    val navTasks: String,
    val navFocus: String,
    val navFocusLauncher: String,
    val navBlockApps: String,
    val navKeywordBlocker: String,
    val navBlockDefense: String,
    val navVpnNetwork: String,
    val navStats: String,
    val navReports: String,
    val navProfile: String,
    val navSettings: String,
    val navWindowsSetup: String,
    val navHowToUse: String,
    val navChangelog: String,

    // ── Session status ───────────────────────────────────────────────────────
    val statusFocusing: String,
    val statusPaused: String,

    // ── Common buttons ───────────────────────────────────────────────────────
    val btnBack: String,
    val btnNext: String,
    val btnGetStarted: String,
    val btnLetsGo: String,
    val btnSkipSetup: String,
    val btnSave: String,
    val btnCancel: String,
    val btnDone: String,
    val btnEnable: String,
    val btnDisable: String,
    val btnAdd: String,
    val btnDelete: String,
    val btnEdit: String,

    // ── Language picker (onboarding page 0) ──────────────────────────────────
    val langPickerTitle: String,
    val langPickerSubtitle: String,

    // ── Onboarding: Welcome ──────────────────────────────────────────────────
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeBody: String,
    val featureEnforcement: String,
    val featurePomodoro: String,
    val featureStats: String,

    // ── Onboarding: Privacy & Terms ──────────────────────────────────────────
    val privacyTitle: String,
    val privacySubtitle: String,
    val privacyLocalData: String,
    val privacyLocalDataDesc: String,
    val privacyProcessMonitoring: String,
    val privacyProcessMonitoringDesc: String,
    val privacyElevatedPrivileges: String,
    val privacyElevatedPrivilegesDesc: String,
    val privacyAcceptText: String,
    val privacyAcceptHint: String,

    // ── Onboarding: Permissions ──────────────────────────────────────────────
    val permissionsTitle: String,
    val permissionsSubtitle: String,

    // ── Onboarding: Goal ─────────────────────────────────────────────────────
    val goalTitle: String,
    val goalSubtitle: String,
    val goalSocialLabel: String,
    val goalSocialSub: String,
    val goalGamingLabel: String,
    val goalGamingSub: String,
    val goalWebLabel: String,
    val goalWebSub: String,
    val goalDeepLabel: String,
    val goalDeepSub: String,

    // ── Onboarding: Presets ──────────────────────────────────────────────────
    val presetsTitle: String,
    val presetsSubtitle: String,

    // ── Onboarding: Focus Duration ───────────────────────────────────────────
    val durationTitle: String,
    val durationSubtitle: String,
    val duration25Label: String,
    val duration25Sub: String,
    val duration45Label: String,
    val duration45Sub: String,
    val duration60Label: String,
    val duration60Sub: String,
    val duration90Label: String,
    val duration90Sub: String,
    val durationHint: String,

    // ── Onboarding: Guide ────────────────────────────────────────────────────
    val guideTitle: String,
    val guideSubtitle: String,

    // ── Settings: Language section ───────────────────────────────────────────
    val settingsLanguageTitle: String,
    val settingsLanguageDesc: String,
    val settingsLanguageApplied: String,

    // ── Dashboard ────────────────────────────────────────────────────────────
    val dashGreetingMorning: String,
    val dashGreetingAfternoon: String,
    val dashGreetingEvening: String,
    val dashStreak: String,
    val dashFocusToday: String,
    val dashTasksLabel: String,
    val dashBlockedLabel: String,
    val dashNoTasks: String,
    val dashAddTask: String,
    val dashStartFocus: String,
)
