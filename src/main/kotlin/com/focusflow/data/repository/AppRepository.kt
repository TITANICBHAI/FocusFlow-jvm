package com.focusflow.data.repository

import com.focusflow.data.*

/**
 * AppRepository
 *
 * Single access point for application-level settings stored in the key/value
 * settings table.  Provides typed accessors so screens never hard-code setting
 * key strings or parse values themselves.
 */
object AppRepository {

    // ── Raw key/value pass-through (for settings not yet typed below) ─────────
    fun getSetting(key: String): String?            = Database.getSetting(key)
    fun setSetting(key: String, value: String)      = Database.setSetting(key, value)

    // ── Typed accessors ───────────────────────────────────────────────────────

    fun getUserName(): String =
        Database.getSetting("user_name") ?: ""

    fun setUserName(name: String) =
        Database.setSetting("user_name", name)

    fun getDailyFocusGoal(): Int =
        Database.getSetting("daily_focus_goal")?.toIntOrNull() ?: 120

    fun setDailyFocusGoal(minutes: Int) =
        Database.setSetting("daily_focus_goal", minutes.toString())

    fun getThemeMode(): String =
        Database.getSetting("theme_mode") ?: "dark"

    fun setThemeMode(mode: String) =
        Database.setSetting("theme_mode", mode)

    fun isAlwaysOnEnforcementEnabled(): Boolean =
        Database.getSetting("always_on_enforcement") == "true"

    fun setAlwaysOnEnforcement(enabled: Boolean) =
        Database.setSetting("always_on_enforcement", enabled.toString())

    fun isPomodoroMode(): Boolean =
        Database.getSetting("pomodoro_mode") == "true"

    fun setPomodoroMode(enabled: Boolean) =
        Database.setSetting("pomodoro_mode", enabled.toString())

    fun isSoundAversionEnabled(): Boolean =
        Database.getSetting("sound_aversion") != "false"   // default = enabled

    fun isOnboardingComplete(): Boolean =
        Database.getSetting("onboarding_complete") == "true"

    fun getLastSeenVersion(): String? =
        Database.getSetting("last_seen_version")

    fun setLastSeenVersion(version: String) =
        Database.setSetting("last_seen_version", version)

    fun getAppOpenCount(): Int =
        Database.getSetting("app_open_count")?.toIntOrNull() ?: 0

    fun incrementAppOpenCount(): Int {
        val next = getAppOpenCount() + 1
        Database.setSetting("app_open_count", next.toString())
        return next
    }

    fun isCrashReportsEnabled(): Boolean? =
        Database.getSetting("crash_reports_enabled")?.let { it == "true" }

    fun isFocusLockUntilTimer(): Boolean =
        Database.getSetting("focus_lock_until_timer") == "true"
}
