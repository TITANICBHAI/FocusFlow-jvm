package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.Task
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * RecurringTaskService
 *
 * Mirrors Android's RecurringTaskManager.
 * On startup (and every day at midnight) it inspects all recurring task
 * templates and creates a fresh copy for today when none already exists.
 *
 * Recurrence rules (stored in Task.recurringType):
 *   "daily"    — every calendar day
 *   "weekdays" — Monday–Friday only
 *   "weekly"   — once a week, on the same day-of-week as the template
 */
object RecurringTaskService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            generateForToday()
            while (isActive) {
                val now = java.time.LocalTime.now()
                // Sleep until the next midnight  +1 minute so rollover is clean
                val secondsUntilMidnight = (24 * 3600L) - (now.toSecondOfDay().toLong()) + 60L
                delay(secondsUntilMidnight * 1000L)
                generateForToday()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Public so SettingsScreen / tests can trigger a manual refresh. */
    fun generateForToday() {
        try {
            val today     = LocalDate.now()
            val templates = Database.getRecurringTemplates()   // all tasks where recurring=true
            val existing  = Database.getTasksForDate(today).map { it.title.lowercase().trim() }.toSet()

            for (template in templates) {
                if (!shouldGenerateToday(template, today)) continue
                if (template.title.lowercase().trim() in existing) continue

                val newTask = template.copy(
                    id            = UUID.randomUUID().toString(),
                    scheduledDate = today,
                    scheduledTime = template.scheduledTime,
                    completed     = false,
                    skipped       = false,
                    createdAt     = LocalDateTime.now()
                )
                Database.upsertTask(newTask)
            }
        } catch (_: Exception) {}
    }

    private fun shouldGenerateToday(template: Task, today: LocalDate): Boolean {
        return when (template.recurringType?.lowercase()) {
            "daily"    -> true
            "weekdays" -> today.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            "weekly"   -> {
                val origin = template.scheduledDate ?: return true
                today.dayOfWeek == origin.dayOfWeek
            }
            else       -> true  // treat unknown type as daily
        }
    }
}
