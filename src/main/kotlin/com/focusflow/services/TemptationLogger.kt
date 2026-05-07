package com.focusflow.services

import java.time.LocalDateTime

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

    fun log(processName: String, displayName: String) {
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

    fun clearSession() = sessionLog.clear()
}
