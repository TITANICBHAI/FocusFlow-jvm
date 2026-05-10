package com.focusflow.data.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val durationMinutes: Int = 25,
    val scheduledDate: LocalDate? = null,
    val scheduledTime: String? = null,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val recurring: Boolean = false,
    val recurringType: String? = null,
    val priority: String = "medium",
    val tags: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
    val focusMode: Boolean = false,
    val focusIntensity: String = "standard",
    val focusBlockedApps: List<String> = emptyList(),
    val focusRequirePin: Boolean = false
)

data class FocusSession(
    val id: String,
    val taskId: String?,
    val taskName: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime?,
    val plannedMinutes: Int,
    val actualMinutes: Int = 0,
    val completed: Boolean = false,
    val interrupted: Boolean = false,
    val notes: String = ""
)

data class BlockRule(
    val id: String,
    val processName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val blockNetwork: Boolean = false
)

data class BlockSchedule(
    val id: String,
    val name: String,
    val daysOfWeek: List<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val processNames: List<String> = emptyList()
)

data class DailyAllowance(
    val processName: String,
    val displayName: String,
    val allowanceMinutes: Int
)

data class DailyNote(
    val date: LocalDate,
    val content: String,
    val mood: Int = 3,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

data class TemptationEntry(
    val processName: String,
    val displayName: String,
    val timestamp: LocalDateTime
)

data class AppSettings(
    val sessionPinHash: String? = null,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroSessionsBeforeLongBreak: Int = 4,
    val soundAversion: Boolean = false,
    val weeklyReportEnabled: Boolean = true,
    val alwaysOnEnforcement: Boolean = false,
    val startWithWindows: Boolean = false,
    val overlayMessage: String = "Stay focused. You've got this.",
    val theme: String = "dark",
    val userDisplayName: String = "",
    val dailyFocusGoalMinutes: Int = 120
)

data class Habit(
    val id: String,
    val name: String,
    val emoji: String = "✅",
    val createdAt: java.time.LocalDate = java.time.LocalDate.now()
)

data class HabitEntry(
    val habitId: String,
    val date: java.time.LocalDate,
    val done: Boolean = true
)

enum class NetworkRuleMode { DOMAIN, KEYWORD }

data class NetworkCutoffRule(
    val id: String,
    val pattern: String,
    val mode: NetworkRuleMode,
    val targetProcess: String? = null,
    val targetDisplayName: String? = null,
    val enabled: Boolean = true
)

enum class Screen {
    DASHBOARD, TASKS, FOCUS, BLOCK_APPS, STATS, NOTES, HABITS, REPORTS, PROFILE, SETTINGS,
    ACTIVE, KEYWORD_BLOCKER, BLOCK_DEFENSE, HOW_TO_USE, CHANGELOG, WINDOWS_SETUP, VPN_NETWORK
}

data class SessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val taskName: String = "",
    val totalSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val blockedProcesses: List<String> = emptyList()
)

data class DayFocusStats(
    val date: LocalDate,
    val totalMinutes: Int,
    val sessionsCount: Int
)

data class DayCompletionStats(
    val date: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val focusMinutes: Int
)

data class StandaloneBlock(
    val processNames: List<String>,
    val untilMs: Long
)
