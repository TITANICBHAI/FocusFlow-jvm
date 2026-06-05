package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.BlockSchedule
import com.focusflow.enforcement.EnforcementLog
import com.focusflow.enforcement.ProcessMonitor
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

object BlockScheduleService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // @Volatile: start() writes on the Compose application thread; stop() reads on the
    // "FocusFlow-Shutdown" daemon thread (Main.kt). Without @Volatile the shutdown
    // thread may see a stale null, leaving schedule enforcement running after teardown.
    @Volatile private var schedulerJob: Job? = null

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

            // Previous calendar day — used for overnight schedules (see below).
            val prevDayValue = if (dayOfWeek == 1) 7 else dayOfWeek - 1

            for (schedule in schedules) {
                val start = LocalTime.of(schedule.startHour, schedule.startMinute)
                val end   = LocalTime.of(schedule.endHour,   schedule.endMinute)
                val isOvernight = start > end

                // Overnight schedule bug fix:
                // A window like "Monday 22:00 – 02:00" has start > end. At 01:30 on
                // Tuesday the old code checked `dayOfWeek (Tuesday) !in daysOfWeek
                // (Monday)` and skipped the schedule, ending enforcement at midnight.
                //
                // Correct logic: the tail of an overnight window (00:00 → end) is
                // owned by the PREVIOUS calendar day's schedule entry.
                val inWindow = if (!isOvernight) {
                    dayOfWeek in schedule.daysOfWeek &&
                            currentTime >= start && currentTime < end
                } else {
                    // Head: today is the named day and we're past start
                    (dayOfWeek in schedule.daysOfWeek && currentTime >= start) ||
                    // Tail: yesterday is the named day and we're before end
                    (prevDayValue in schedule.daysOfWeek && currentTime < end)
                }

                if (inWindow) {
                    active.add(schedule.name)
                    blockedProcesses.addAll(schedule.processNames.map { it.lowercase() })
                }
            }

            activeScheduleNames = active
            ProcessMonitor.scheduleBlockedProcesses = blockedProcesses
        } catch (e: Exception) {
            EnforcementLog.warn(
                "BlockScheduleService",
                "tick() threw — schedule enforcement may be stale until next tick",
                e
            )
        }
    }

    fun forceCheck() = scope.launch { tick() }
}
