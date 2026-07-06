package com.focusflow.data.repository

import com.focusflow.data.*
import com.focusflow.data.models.Task
import java.time.LocalDate

/**
 * TaskRepository
 *
 * Single access point for task CRUD operations.
 * UI screens and services should call this instead of Database directly.
 */
object TaskRepository {
    fun getTasks(date: LocalDate? = null): List<Task>                    = Database.getTasks(date)
    fun getTasksForDate(date: LocalDate): List<Task>                     = Database.getTasksForDate(date)
    fun getTasksInRange(start: LocalDate, end: LocalDate): List<Task>    = Database.getTasksInRange(start, end)
    fun getRecurringTemplates(): List<Task>                              = Database.getRecurringTemplates()
    fun upsertTask(task: Task)                                           = Database.upsertTask(task)
    fun deleteTask(id: String)                                           = Database.deleteTask(id)
    fun completeTask(id: String)                                         = Database.completeTask(id)
    fun skipTask(id: String)                                             = Database.skipTask(id)
}
