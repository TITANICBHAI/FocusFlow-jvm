package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.FocusSession
import com.focusflow.data.models.SessionState
import com.focusflow.enforcement.NetworkBlocker
import com.focusflow.enforcement.ProcessMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.util.UUID

/**
 * FocusSessionService
 *
 * Manages the lifecycle of a focus session:
 *   start() → pause() → resume() → end()
 *
 * Owns the countdown timer coroutine and drives ProcessMonitor.
 * On session start, activates ProcessMonitor so blocked apps are killed.
 * On session end, deactivates ProcessMonitor and cleans up firewall rules.
 *
 * JVM equivalent of Android's ForegroundTaskService + ForegroundLaunchModule.
 */
object FocusSessionService {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state

    private var sessionStartTime: LocalDateTime? = null
    private var sessionId: String? = null
    private var taskId: String? = null
    private var taskName: String = ""
    private var plannedMinutes: Int = 0

    fun start(
        name: String,
        minutes: Int,
        tid: String? = null,
        blockedProcesses: List<String> = emptyList()
    ) {
        if (_state.value.isActive) return

        sessionId = UUID.randomUUID().toString()
        taskName = name
        plannedMinutes = minutes
        taskId = tid
        sessionStartTime = LocalDateTime.now()

        _state.value = SessionState(
            isActive = true,
            isPaused = false,
            taskName = name,
            totalSeconds = minutes * 60,
            elapsedSeconds = 0,
            blockedProcesses = blockedProcesses
        )

        ProcessMonitor.sessionActive = true
        ProcessMonitor.start()

        startTimer()

        // Save session start to DB
        Database.insertSession(
            FocusSession(
                id = sessionId!!,
                taskId = taskId,
                taskName = taskName,
                startTime = sessionStartTime!!,
                endTime = null,
                plannedMinutes = plannedMinutes,
                actualMinutes = 0,
                completed = false,
                interrupted = false
            )
        )
    }

    fun pause() {
        if (!_state.value.isActive || _state.value.isPaused) return
        timerJob?.cancel()
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        if (!_state.value.isActive || !_state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = false)
        startTimer()
    }

    fun end(completed: Boolean = false) {
        timerJob?.cancel()
        ProcessMonitor.sessionActive = false
        NetworkBlocker.removeAllRules()

        val elapsed = _state.value.elapsedSeconds
        val endTime = LocalDateTime.now()

        // Update session in DB
        sessionId?.let { sid ->
            Database.insertSession(
                FocusSession(
                    id = sid,
                    taskId = taskId,
                    taskName = taskName,
                    startTime = sessionStartTime ?: LocalDateTime.now(),
                    endTime = endTime,
                    plannedMinutes = plannedMinutes,
                    actualMinutes = elapsed / 60,
                    completed = completed,
                    interrupted = !completed
                )
            )
        }

        _state.value = SessionState()
        sessionId = null
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (isActive && _state.value.isActive && !_state.value.isPaused) {
                delay(1000)
                val current = _state.value
                val newElapsed = current.elapsedSeconds + 1
                if (newElapsed >= current.totalSeconds) {
                    // Session complete
                    withContext(Dispatchers.Main) {
                        end(completed = true)
                    }
                    return@launch
                }
                _state.value = current.copy(elapsedSeconds = newElapsed)
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
