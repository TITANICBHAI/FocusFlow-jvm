package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.BlockSchedule
import com.focusflow.enforcement.ProcessMonitor
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

object BlockScheduleService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var schedulerJob: Job? = null

    @Volatile var activeScheduleNames: List<String> = emptyList()
        private set

    fun start() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            while (isActive) {
                tick()
                delay(60_000)
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private fun tick() {
        try {
            val schedules = Database.getBlockSchedules().filter { it.enabled }
            val now = LocalDateTime.now()
            val dayOfWeek = now.dayOfWeek.value
            val currentTime = LocalTime.of(now.hour, now.minute)

            val active = mutableListOf<String>()
            val blockedProcesses = mutableSetOf<String>()

            for (schedule in schedules) {
                if (dayOfWeek !in schedule.daysOfWeek) continue
                val start = LocalTime.of(schedule.startHour, schedule.startMinute)
                val end   = LocalTime.of(schedule.endHour,   schedule.endMinute)
                val inWindow = if (start <= end) {
                    currentTime >= start && currentTime < end
                } else {
                    currentTime >= start || currentTime < end
                }
                if (inWindow) {
                    active.add(schedule.name)
                    blockedProcesses.addAll(schedule.processNames.map { it.lowercase() })
                }
            }

            activeScheduleNames = active
            ProcessMonitor.scheduleBlockedProcesses = blockedProcesses
        } catch (_: Exception) {}
    }

    fun forceCheck() = scope.launch { tick() }
}
