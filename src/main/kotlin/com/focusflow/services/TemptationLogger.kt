package com.focusflow.services

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * TemptationLogger
 *
 * In-memory companion to Database.logTemptation().
 * Tracks attempts in the current session for the overlay stats display.
 *
 * Ported directly from Android's TemptationLogManager.kt —
 * the summary logic is identical (no Android APIs used there).
 */
object TemptationLogger {

    data class Entry(
        val processName: String,
        val displayName: String,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )

    // CopyOnWriteArrayList: safe for concurrent reads from the enforcement thread
    // and writes from the UI / session-end thread without ConcurrentModificationException.
    private val sessionLog = java.util.concurrent.CopyOnWriteArrayList<Entry>()

    /**
     * Per-process cooldown gate (1 s). Prevents inflated session attempt counts
     * when both the WinEventHook callback and the polling fallback fire for the
     * same foreground process within a single enforcement tick.
     *
     * ProcessMonitor.tryAcquireCooldown() guards the kill path, but its cooldown
     * window (800 ms) is measured in wall-clock milliseconds and uses the hook's
     * ConcurrentHashMap atomically — a vanishingly rare race between the hook
     * coroutine and the poll coroutine can still let both reach enforceBlock()
     * within the same tick. This 1-second gate is the final safety net.
     */
    private val cooldowns = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 1_000L

    fun log(processName: String, displayName: String) {
        val key = processName.lowercase()
        val now = System.currentTimeMillis()
        var allow = false
        cooldowns.compute(key) { _, last ->
            if (last == null || now - last >= COOLDOWN_MS) { allow = true; now }
            else last
        }
        if (!allow) return
        sessionLog.add(Entry(processName, displayName))
    }

    fun getSessionAttempts(): Int = sessionLog.size

    fun getSessionSummary(): String {
        val snapshot = sessionLog.toList()
        if (snapshot.isEmpty()) return "No blocked app attempts this session."
        val counts = snapshot.groupBy { it.displayName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
        val total = snapshot.size
        val lines = counts.take(5).joinToString("\n") { "• ${it.key}: ${it.value}×" }
        return "$total total attempts:\n$lines"
    }

    fun clearSession() {
        sessionLog.clear()
        // Clear cooldowns too — otherwise the first block of the next session
        // within 1 s of the last block of the previous session is silently dropped.
        cooldowns.clear()
    }
}
