package com.focusflow.services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * KeywordMatchLogger
 *
 * In-memory ring-buffer of the last [MAX_ENTRIES] keyword-triggered block events.
 * Each entry captures what keyword fired, which app was affected, and the window
 * title that was checked — giving the user full visibility into what triggered blocks.
 *
 * Thread-safe via CopyOnWriteArrayList — safe to write from the enforcement coroutine
 * and read from the Compose UI thread simultaneously.
 */
object KeywordMatchLogger {

    private const val MAX_ENTRIES = 50

    data class Match(
        val appDisplayName: String,
        val keyword: String,
        val windowTitle: String,
        val timestamp: LocalDateTime = LocalDateTime.now()
    ) {
        val timeLabel: String
            get() = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    private val log = java.util.concurrent.CopyOnWriteArrayList<Match>()

    fun record(appDisplayName: String, keyword: String, windowTitle: String) {
        if (log.size >= MAX_ENTRIES) log.removeAt(0)
        log.add(Match(appDisplayName, keyword, windowTitle.take(120)))
    }

    /** Returns up to [n] most-recent matches, newest first. */
    fun getRecent(n: Int = 20): List<Match> = log.toList().takeLast(n).reversed()

    fun hasEntries(): Boolean = log.isNotEmpty()

    fun clear() = log.clear()
}
