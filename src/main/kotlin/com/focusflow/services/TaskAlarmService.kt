package com.focusflow.services

import com.focusflow.data.Database
import kotlinx.coroutines.*
import java.awt.TrayIcon
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TaskAlarmService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var schedulerJob: Job? = null
    private val firedToday = mutableSetOf<String>()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun start() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            while (isActive) {
                checkAlarms()
                delay(30_000)
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private fun checkAlarms() {
        try {
            val now = LocalDateTime.now()
            val today = LocalDate.now()
            val tasks = Database.getTasksForDate(today)
                .filter { !it.completed && it.scheduledTime != null }

            for (task in tasks) {
                if (task.id in firedToday) continue
                val scheduledTime = try {
                    LocalTime.parse(task.scheduledTime!!, timeFmt)
                } catch (_: Exception) { continue }

                val scheduled = LocalDateTime.of(today, scheduledTime)
                val diffSeconds = java.time.Duration.between(now, scheduled).seconds

                if (diffSeconds in -60L..0L) {
                    firedToday.add(task.id)
                    SystemTrayManager.showNotification(
                        title   = "Task due: ${task.title}",
                        message = "Scheduled for ${task.scheduledTime} • ${task.durationMinutes}m",
                        type    = TrayIcon.MessageType.INFO
                    )
                    if (SoundAversion.isEnabled) SoundAversion.playSessionStart()
                } else if (diffSeconds in 1L..300L) {
                    val minsLeft = (diffSeconds / 60).toInt() + 1
                    if (minsLeft == 5 || minsLeft == 1) {
                        val reminderKey = "${task.id}_${minsLeft}min"
                        if (reminderKey !in firedToday) {
                            firedToday.add(reminderKey)
                            SystemTrayManager.showNotification(
                                title   = "Starting soon: ${task.title}",
                                message = "Due in $minsLeft minute${if (minsLeft == 1) "" else "s"}",
                                type    = TrayIcon.MessageType.INFO
                            )
                        }
                    }
                }
            }

            if (now.hour == 0 && now.minute < 1) {
                firedToday.clear()
            }
        } catch (_: Exception) {}
    }

    fun testAlarm(taskTitle: String) {
        SystemTrayManager.showNotification(
            title   = "Test Alarm: $taskTitle",
            message = "Alarms are working correctly.",
            type    = TrayIcon.MessageType.INFO
        )
    }
}
