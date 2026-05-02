package com.focusflow.services

import com.focusflow.data.Database
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BreakPhase { IDLE, SESSION, SHORT_BREAK, LONG_BREAK }

data class PomodoroState(
    val phase: BreakPhase = BreakPhase.IDLE,
    val cycleNumber: Int = 0,
    val breakSecondsRemaining: Int = 0,
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4
)

object BreakEnforcer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var breakJob: Job? = null

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state

    val isInBreak: Boolean get() = _state.value.phase == BreakPhase.SHORT_BREAK ||
                                    _state.value.phase == BreakPhase.LONG_BREAK

    var onBreakComplete: (() -> Unit)? = null

    fun loadSettings() {
        val work  = Database.getSetting("pomodoro_work")?.toIntOrNull()  ?: 25
        val short = Database.getSetting("pomodoro_short")?.toIntOrNull() ?: 5
        val long  = Database.getSetting("pomodoro_long")?.toIntOrNull()  ?: 15
        val cycles = Database.getSetting("pomodoro_cycles")?.toIntOrNull() ?: 4
        _state.value = _state.value.copy(
            workMinutes = work,
            shortBreakMinutes = short,
            longBreakMinutes = long,
            cyclesBeforeLongBreak = cycles
        )
    }

    fun saveSettings(work: Int, short: Int, long: Int, cycles: Int) {
        Database.setSetting("pomodoro_work",   work.toString())
        Database.setSetting("pomodoro_short",  short.toString())
        Database.setSetting("pomodoro_long",   long.toString())
        Database.setSetting("pomodoro_cycles", cycles.toString())
        _state.value = _state.value.copy(
            workMinutes = work,
            shortBreakMinutes = short,
            longBreakMinutes = long,
            cyclesBeforeLongBreak = cycles
        )
    }

    fun onSessionCompleted() {
        val current = _state.value
        val newCycle = current.cycleNumber + 1
        val isLong = newCycle % current.cyclesBeforeLongBreak == 0
        val breakMins = if (isLong) current.longBreakMinutes else current.shortBreakMinutes
        val phase = if (isLong) BreakPhase.LONG_BREAK else BreakPhase.SHORT_BREAK

        _state.value = current.copy(
            phase = phase,
            cycleNumber = newCycle,
            breakSecondsRemaining = breakMins * 60
        )

        NotificationService.breakStarted(breakMins, isLong)
        startBreakCountdown()
    }

    fun skipBreak() {
        breakJob?.cancel()
        _state.value = _state.value.copy(
            phase = BreakPhase.IDLE,
            breakSecondsRemaining = 0
        )
        onBreakComplete?.invoke()
    }

    fun reset() {
        breakJob?.cancel()
        _state.value = PomodoroState(
            workMinutes          = _state.value.workMinutes,
            shortBreakMinutes    = _state.value.shortBreakMinutes,
            longBreakMinutes     = _state.value.longBreakMinutes,
            cyclesBeforeLongBreak = _state.value.cyclesBeforeLongBreak
        )
    }

    private fun startBreakCountdown() {
        breakJob?.cancel()
        breakJob = scope.launch {
            while (isActive) {
                delay(1000)
                val s = _state.value.breakSecondsRemaining - 1
                if (s <= 0) {
                    _state.value = _state.value.copy(
                        phase = BreakPhase.IDLE,
                        breakSecondsRemaining = 0
                    )
                    NotificationService.breakEnded()
                    onBreakComplete?.invoke()
                    return@launch
                }
                _state.value = _state.value.copy(breakSecondsRemaining = s)
            }
        }
    }
}
