package com.focusflow.services

import com.focusflow.data.Database
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BackupService {

    fun exportToCsv(): String? {
        val frame = Frame()
        val dialog = FileDialog(frame, "Save Session Export", FileDialog.SAVE).apply {
            file = "focusflow_sessions_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.csv"
            isVisible = true
        }
        val dir  = dialog.directory
        val name = dialog.file
        dialog.dispose()
        frame.dispose()
        dir  ?: return null
        name ?: return null
        val path = File(dir, name).absolutePath

        val sessions = Database.getRecentSessions(1000)
        val sb = StringBuilder()
        sb.appendLine("id,task_name,start_time,end_time,planned_minutes,actual_minutes,completed,interrupted")
        for (s in sessions) {
            sb.appendLine("${s.id},\"${s.taskName.replace("\"","'")}\",${s.startTime},${s.endTime ?: ""},${s.plannedMinutes},${s.actualMinutes},${s.completed},${s.interrupted}")
        }
        File(path).writeText(sb.toString())
        return path
    }

    fun exportTasksToCsv(): String? {
        val frame = Frame()
        val dialog = FileDialog(frame, "Save Tasks Export", FileDialog.SAVE).apply {
            file = "focusflow_tasks_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.csv"
            isVisible = true
        }
        val dir  = dialog.directory
        val name = dialog.file
        dialog.dispose()
        frame.dispose()
        dir  ?: return null
        name ?: return null
        val path = File(dir, name).absolutePath

        val tasks = Database.getTasks()
        val sb = StringBuilder()
        sb.appendLine("id,title,description,duration_minutes,scheduled_date,scheduled_time,completed,priority,tags,created_at")
        for (t in tasks) {
            sb.appendLine("${t.id},\"${t.title.replace("\"","'")}\",\"${t.description.replace("\"","'")}\",${t.durationMinutes},${t.scheduledDate ?: ""},${t.scheduledTime ?: ""},${t.completed},${t.priority},\"${t.tags.joinToString("|")}\",${t.createdAt}")
        }
        File(path).writeText(sb.toString())
        return path
    }

    fun clearAllData() {
        Database.clearAllSessions()
        Database.clearAllTasks()
        Database.clearTemptationLog()
        Database.clearNotes()
    }
}
