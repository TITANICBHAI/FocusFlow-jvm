package com.focusflow.data.repository

import com.focusflow.data.*
import com.focusflow.data.models.Habit
import com.focusflow.data.models.HabitEntry
import java.time.LocalDate

/**
 * HabitRepository
 *
 * Single access point for habit tracking data.
 */
object HabitRepository {
    fun getHabits(): List<Habit>                                           = Database.getHabits()
    fun upsertHabit(habit: Habit)                                         = Database.upsertHabit(habit)
    fun deleteHabit(id: String)                                           = Database.deleteHabit(id)
    fun getHabitEntries(habitId: String, since: LocalDate): List<HabitEntry> =
        Database.getHabitEntries(habitId, since)
    fun setHabitEntry(habitId: String, date: LocalDate, done: Boolean)   = Database.setHabitEntry(habitId, date, done)
    fun getHabitStreak(habitId: String): Int                             = Database.getHabitStreak(habitId)
}
