package com.focusflow.data.models

import java.time.LocalDate
import java.time.LocalDateTime

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val durationMinutes: Int = 25,
    val scheduledDate: LocalDate? = null,
    val scheduledTime: String? = null,
    val completed: Boolean = false,
    val recurring: Boolean = false,
    val recurringType: String? = null,
    val priority: String = "medium",
    val tags: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null
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
    val theme: String = "dark"
)

enum class Screen {
    DASHBOARD, TASKS, FOCUS, STATS, NOTES, SETTINGS
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
