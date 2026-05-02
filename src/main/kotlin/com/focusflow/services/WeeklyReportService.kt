package com.focusflow.services

import com.focusflow.data.Database
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object WeeklyReportService {

    data class WeeklyReport(
        val weekLabel:         String,
        val totalMinutes:      Long,
        val sessionsCompleted: Int,
        val tasksCompleted:    Int,
        val blockedAttempts:   Int,
        val avgDailyMinutes:   Long,
        val currentStreakDays: Int,
        val generatedAt:       String
    ) {
        val hoursFormatted: String get() = "${totalMinutes / 60}h ${totalMinutes % 60}m"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var schedulerJob: Job? = null

    @Volatile var latestReport: WeeklyReport? = null
        private set

    @Volatile var hasNewReport: Boolean = false

    var onReportReady: ((WeeklyReport) -> Unit)? = null

    fun startScheduler() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            checkAndGenerate()
            while (isActive) {
                delay(24L * 60 * 60 * 1000)
                checkAndGenerate()
            }
        }
    }

    fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private suspend fun checkAndGenerate() {
        val lastStr = Database.getSetting("weekly_report_last_generated")
        val today   = LocalDate.now()
        val last    = lastStr?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }
        val due = last == null ||
            (today.dayOfWeek == DayOfWeek.MONDAY && today.isAfter(last))

        if (due) {
            val report = generate()
            latestReport = report
            hasNewReport = true
            onReportReady?.invoke(report)
            Database.setSetting(
                "weekly_report_last_generated",
                today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        }
    }

    fun generate(): WeeklyReport {
        val today     = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1)
        val weekEnd   = weekStart.plusDays(6)
        val fmt       = DateTimeFormatter.ofPattern("MMM d")
        val weekLabel = "${weekStart.format(fmt)} \u2013 ${weekEnd.format(fmt)}"

        val sessions = Database.getSessionsInRange(weekStart.toString(), weekEnd.toString())
        val tasks    = Database.getCompletedTasksInRange(weekStart.toString(), weekEnd.toString())
        val attempts = Database.getTemptationsInRange(weekStart.toString(), weekEnd.toString())
        val streak   = Database.getCurrentStreak()

        val totalMinutes = sessions.sumOf { it.actualMinutes.toLong() }

        return WeeklyReport(
            weekLabel         = weekLabel,
            totalMinutes      = totalMinutes,
            sessionsCompleted = sessions.size,
            tasksCompleted    = tasks,
            blockedAttempts   = attempts,
            avgDailyMinutes   = if (sessions.isEmpty()) 0L else totalMinutes / 7,
            currentStreakDays = streak,
            generatedAt       = today.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        )
    }

    fun dismissNewReportBadge() { hasNewReport = false }
}
