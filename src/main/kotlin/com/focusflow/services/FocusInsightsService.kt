package com.focusflow.services

import com.focusflow.data.Database
import java.time.DayOfWeek

object FocusInsightsService {

    data class Insights(
        val mostProductiveHour: Int?     = null,
        val bestDayOfWeek: DayOfWeek?    = null,
        val avgSessionMinutes: Int        = 0,
        val completionRate: Float         = 0f,
        val longestStreak: Int            = 0,
        val totalHoursAllTime: Float      = 0f,
        val sessionsThisWeek: Int         = 0,
        val focusMinutesThisWeek: Int     = 0,
        val bestSessionMinutes: Int       = 0
    )

    fun compute(): Insights {
        val allSessions  = Database.getRecentSessions(300)
        val goodSessions = allSessions.filter { it.completed && it.actualMinutes > 0 }

        val totalMinutes   = goodSessions.sumOf { it.actualMinutes }
        val avgMinutes     = if (goodSessions.isEmpty()) 0 else totalMinutes / goodSessions.size
        val bestSession    = goodSessions.maxOfOrNull { it.actualMinutes } ?: 0
        val completionRate = if (allSessions.isEmpty()) 0f
                             else allSessions.count { it.completed }.toFloat() / allSessions.size

        // Peak focus hour (hour with most cumulative focus minutes)
        val hourMinutes = goodSessions
            .groupBy { it.startTime.hour }
            .mapValues { (_, v) -> v.sumOf { it.actualMinutes } }
        val bestHour = hourMinutes.maxByOrNull { it.value }?.key

        // Best day of week
        val dayMinutes = goodSessions
            .groupBy { it.startTime.dayOfWeek }
            .mapValues { (_, v) -> v.sumOf { it.actualMinutes } }
        val bestDay = dayMinutes.maxByOrNull { it.value }?.key

        // This-week stats
        val weekAgo = java.time.LocalDate.now().minusDays(6)
        val thisWeekSessions = goodSessions.filter {
            !it.startTime.toLocalDate().isBefore(weekAgo)
        }
        val sessionsThisWeek     = thisWeekSessions.size
        val focusMinutesThisWeek = thisWeekSessions.sumOf { it.actualMinutes }

        return Insights(
            mostProductiveHour   = bestHour,
            bestDayOfWeek        = bestDay,
            avgSessionMinutes    = avgMinutes,
            completionRate       = completionRate,
            longestStreak        = Database.getBestStreak(),
            totalHoursAllTime    = Database.getAllTimeFocusMinutes() / 60f,
            sessionsThisWeek     = sessionsThisWeek,
            focusMinutesThisWeek = focusMinutesThisWeek,
            bestSessionMinutes   = bestSession
        )
    }
}
