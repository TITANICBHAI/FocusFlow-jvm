package com.focusflow.services

import com.focusflow.data.*
import com.focusflow.data.models.BlockSchedule
import com.focusflow.enforcement.EnforcementLog
import com.focusflow.enforcement.ProcessMonitor
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure function: determines whether [schedule] is active at [now].
 *
 * Extracted from BlockScheduleService.tick() so the window-evaluation
 * logic can be unit-tested independently of coroutines and the database.
 *
 * Handles overnight windows (e.g. "Monday 22:00 – 02:00") correctly:
 * - Head: today is the named day and current time ≥ start
 * - Tail: yesterday is the named day and current time < end
 */
internal fun isScheduleActive(schedule: BlockSchedule, now: LocalDateTime): Boolean {
    if (!schedule.enabled) return false

    val dayOfWeek   = now.dayOfWeek.value           // 1 = Mon … 7 = Sun
    val currentTime = LocalTime.of(now.hour, now.minute)
    val start       = LocalTime.of(schedule.startHour, schedule.startMinute)
    val end         = LocalTime.of(schedule.endHour,   schedule.endMinute)
    val isOvernight = start > end
    val prevDay     = if (dayOfWeek == 1) 7 else dayOfWeek - 1

    return if (!isOvernight) {
        dayOfWeek in schedule.daysOfWeek && currentTime >= start && currentTime < end
    } else {
        (dayOfWeek in schedule.daysOfWeek && currentTime >= start) ||
        (prevDay   in schedule.daysOfWeek && currentTime < end)
    }
}

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
            val now = LocalDateTime.now()
            val active = mutableListOf<String>()
            val blockedProcesses = mutableSetOf<String>()

            // Delegates to the top-level isScheduleActive() pure function defined
            // at the top of this file.  It handles normal and overnight windows
            // and is unit-tested independently of the coroutine scheduler.
            for (schedule in Database.getBlockSchedules()) {
                if (isScheduleActive(schedule, now)) {
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
