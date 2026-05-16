package com.focusflow.enforcement

import com.focusflow.data.Database
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * KillSwitchService — emergency enforcement pause with a 5-minute daily budget.
 *
 * The user gets exactly 300 seconds of relief per calendar day.
 * Activating the kill switch pauses ALL enforcement layers (ProcessMonitor,
 * NuclearMode) for as long as the budget lasts or until the user manually
 * deactivates it. When the budget runs out, enforcement resumes automatically.
 *
 * Budget resets at midnight (first access after the calendar date changes).
 * Remaining time is persisted to the database so restarts don't refill the
 * budget mid-day.
 */
object KillSwitchService {

    const val DAILY_BUDGET_SECONDS = 300

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var countdownJob: Job? = null

    private val _remainingSecondsToday = MutableStateFlow(DAILY_BUDGET_SECONDS)
    val remainingSecondsToday: StateFlow<Int> = _remainingSecondsToday

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    val isExhausted: Boolean get() = _remainingSecondsToday.value <= 0

    fun loadFromDb() {
        val savedDate  = Database.getSetting("killswitch_reset_date") ?: ""
        val today      = LocalDate.now().toString()
        val remaining  = if (savedDate == today) {
            Database.getSetting("killswitch_remaining_today")?.toIntOrNull()
                ?: DAILY_BUDGET_SECONDS
        } else {
            DAILY_BUDGET_SECONDS
        }
        _remainingSecondsToday.value = remaining.coerceIn(0, DAILY_BUDGET_SECONDS)
        saveToDb()
    }

    /**
     * Activate the kill switch.
     * Returns true if enforcement was paused, false if the daily budget is already
     * exhausted (the caller should notify the user).
     */
    fun activate(): Boolean {
        if (_isActive.value) return true
        if (isExhausted) return false

        _isActive.value = true
        ProcessMonitor.killSwitchActive = true

        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (_isActive.value && _remainingSecondsToday.value > 0) {
                delay(1_000L)
                val newVal = (_remainingSecondsToday.value - 1).coerceAtLeast(0)
                _remainingSecondsToday.value = newVal
                if (newVal % 30 == 0 || newVal <= 10) saveToDb()
                if (newVal == 0) deactivate()
            }
        }
        return true
    }

    fun deactivate() {
        if (!_isActive.value) return
        countdownJob?.cancel()
        _isActive.value = false
        ProcessMonitor.killSwitchActive = false
        saveToDb()
    }

    /**
     * Toggle: deactivate if active, activate otherwise.
     * Returns false only when budget is exhausted and activation is impossible.
     */
    fun toggle(): Boolean =
        if (_isActive.value) { deactivate(); true } else activate()

    private fun saveToDb() {
        runCatching {
            Database.setSetting("killswitch_remaining_today", _remainingSecondsToday.value.toString())
            Database.setSetting("killswitch_reset_date", LocalDate.now().toString())
        }
    }
}
