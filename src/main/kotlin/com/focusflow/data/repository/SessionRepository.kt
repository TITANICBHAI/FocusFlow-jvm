package com.focusflow.data.repository

import com.focusflow.data.*
import com.focusflow.data.models.DayCompletionStats
import com.focusflow.data.models.DayFocusStats
import com.focusflow.data.models.FocusSession
import java.time.LocalDate

/**
 * SessionRepository
 *
 * Single access point for focus session data and daily completion stats.
 */
object SessionRepository {
    fun insertSession(session: FocusSession)                                = Database.insertSession(session)
    fun getRecentSessions(limit: Int = 50): List<FocusSession>             = Database.getRecentSessions(limit)
    fun getSessionsInDateRange(start: LocalDate, end: LocalDate)           = Database.getSessionsInDateRange(start, end)
    fun getTotalFocusMinutesToday(): Int                                    = Database.getTotalFocusMinutesToday()
    fun getAllTimeFocusMinutes(): Int                                        = Database.getAllTimeFocusMinutes()
    fun getAllTimeFocusSessions(): Int                                      = Database.getAllTimeFocusSessions()
    fun getFocusMinutesByDay(days: Int = 7): List<DayFocusStats>           = Database.getFocusMinutesByDay(days)
    fun getRecentDayCompletions(days: Int = 84): List<DayCompletionStats>  = Database.getRecentDayCompletions(days)
    // Weekly-report range queries (string dates for backward compat)
    fun getSessionsInRange(start: String, end: String): List<FocusSession> = Database.getSessionsInRange(start, end)
}
