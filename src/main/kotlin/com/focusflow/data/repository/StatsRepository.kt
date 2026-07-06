package com.focusflow.data.repository

import com.focusflow.data.*
import com.focusflow.data.models.TemptationEntry

/**
 * StatsRepository
 *
 * Single access point for user-facing statistics: streaks, temptation log,
 * and weekly-report aggregation queries.
 */
object StatsRepository {
    fun getCurrentStreak(): Int                                              = Database.getCurrentStreak()
    fun getBestStreak(): Int                                                 = Database.getBestStreak()

    fun logTemptation(processName: String, displayName: String)             = Database.logTemptation(processName, displayName)
    fun getTemptationLog(sinceDays: Int = 7): List<TemptationEntry>         = Database.getTemptationLog(sinceDays)

    /** Returns the count of blocked attempts in a date range (YYYY-MM-DD strings). */
    fun getTemptationsInRange(startDate: String, endDate: String): Int      = Database.getTemptationsInRange(startDate, endDate)
    fun getTemptationBreakdownInRange(start: String, end: String, limit: Int = 5) =
        Database.getTemptationBreakdownInRange(start, end, limit)

    fun getCompletedTasksInRange(startDate: String, endDate: String): Int   = Database.getCompletedTasksInRange(startDate, endDate)
}
