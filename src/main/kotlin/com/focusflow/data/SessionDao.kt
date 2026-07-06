package com.focusflow.data

import com.focusflow.data.models.DayCompletionStats
import com.focusflow.data.models.DayFocusStats
import com.focusflow.data.models.FocusSession
import java.time.LocalDate
import java.time.LocalDateTime

// ── Row mapper ────────────────────────────────────────────────────────────────

internal fun rowToSession(rs: java.sql.ResultSet): FocusSession = FocusSession(
    id             = rs.getString("id"),
    taskId         = rs.getString("task_id"),
    taskName       = rs.getString("task_name"),
    startTime      = LocalDateTime.parse(rs.getString("start_time"), Database.dtFmt),
    endTime        = rs.getString("end_time")?.let { LocalDateTime.parse(it, Database.dtFmt) },
    plannedMinutes = rs.getInt("planned_minutes"),
    actualMinutes  = rs.getInt("actual_minutes"),
    completed      = rs.getInt("completed") == 1,
    interrupted    = rs.getInt("interrupted") == 1,
    notes          = rs.getString("notes") ?: ""
)

// ── Queries ───────────────────────────────────────────────────────────────────

fun Database.insertSession(session: FocusSession) = synchronized(this) {
    // Transaction: session row + daily-focus-minutes update must be atomic.
    connection.autoCommit = false
    try {
        connection.prepareStatement("""
            INSERT OR REPLACE INTO focus_sessions
            (id, task_id, task_name, start_time, end_time, planned_minutes,
             actual_minutes, completed, interrupted, notes)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()).use { ps ->
            ps.setString(1, session.id); ps.setString(2, session.taskId)
            ps.setString(3, session.taskName)
            ps.setString(4, session.startTime.format(dtFmt))
            ps.setString(5, session.endTime?.format(dtFmt))
            ps.setInt(6, session.plannedMinutes); ps.setInt(7, session.actualMinutes)
            ps.setInt(8, if (session.completed) 1 else 0)
            ps.setInt(9, if (session.interrupted) 1 else 0)
            ps.setString(10, session.notes)
            ps.executeUpdate()
        }
        if (session.completed && session.actualMinutes > 0) {
            updateDailyFocusMinutes(session.startTime.toLocalDate())
        }
        connection.commit()
    } catch (e: Exception) {
        try { connection.rollback() } catch (_: Exception) {}
        throw e
    } finally {
        connection.autoCommit = true
    }
}

fun Database.getRecentSessions(limit: Int = 50): List<FocusSession> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM focus_sessions ORDER BY start_time DESC LIMIT ?"
    ).use { ps ->
        ps.setInt(1, limit)
        ps.executeQuery().use { rs ->
            val list = mutableListOf<FocusSession>()
            while (rs.next()) list.add(rowToSession(rs))
            list
        }
    }
}

fun Database.getSessionsInDateRange(start: LocalDate, end: LocalDate): List<FocusSession> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM focus_sessions WHERE DATE(start_time) BETWEEN ? AND ? ORDER BY start_time DESC"
    ).use { ps ->
        ps.setString(1, start.format(dateFmt)); ps.setString(2, end.format(dateFmt))
        ps.executeQuery().use { rs ->
            val list = mutableListOf<FocusSession>()
            while (rs.next()) list.add(rowToSession(rs))
            list
        }
    }
}

fun Database.getTotalFocusMinutesToday(): Int = synchronized(this) {
    val today = LocalDate.now().format(dateFmt)
    return connection.prepareStatement(
        "SELECT COALESCE(SUM(actual_minutes), 0) FROM focus_sessions WHERE DATE(start_time) = ? AND completed = 1"
    ).use { ps ->
        ps.setString(1, today)
        ps.executeQuery().use { it.getInt(1) }
    }
}

fun Database.getAllTimeFocusMinutes(): Int = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT COALESCE(SUM(actual_minutes), 0) FROM focus_sessions WHERE completed = 1"
    ).use { if (it.next()) it.getInt(1) else 0 }
}

fun Database.getAllTimeFocusSessions(): Int = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT COUNT(*) FROM focus_sessions WHERE completed = 1"
    ).use { if (it.next()) it.getInt(1) else 0 }
}

fun Database.getFocusMinutesByDay(days: Int = 7): List<DayFocusStats> = synchronized(this) {
    val today = LocalDate.now()
    return (days - 1 downTo 0).map { daysAgo ->
        val date    = today.minusDays(daysAgo.toLong())
        val dateStr = date.format(dateFmt)
        val result  = connection.prepareStatement("""
            SELECT COALESCE(SUM(actual_minutes), 0) AS mins, COUNT(*) AS cnt
            FROM focus_sessions WHERE DATE(start_time) = ? AND completed = 1
        """.trimIndent()).use { ps ->
            ps.setString(1, dateStr)
            ps.executeQuery().use { rs ->
                if (rs.next()) Pair(rs.getInt("mins"), rs.getInt("cnt")) else Pair(0, 0)
            }
        }
        DayFocusStats(date = date, totalMinutes = result.first, sessionsCount = result.second)
    }
}

fun Database.getRecentDayCompletions(days: Int = 84): List<DayCompletionStats> = synchronized(this) {
    val today = LocalDate.now()
    return (days - 1 downTo 0).map { d ->
        val date    = today.minusDays(d.toLong())
        val dateStr = date.format(dateFmt)
        val row = connection.prepareStatement(
            "SELECT completed_count, total_count, focus_minutes FROM daily_completions WHERE date = ?"
        ).use { ps ->
            ps.setString(1, dateStr)
            ps.executeQuery().use { rs ->
                if (rs.next())
                    Triple(rs.getInt("completed_count"), rs.getInt("total_count"), rs.getInt("focus_minutes"))
                else Triple(0, 0, 0)
            }
        }
        DayCompletionStats(date, row.first, row.second, row.third)
    }
}

// Internal helper — called by insertSession().
internal fun Database.updateDailyFocusMinutes(date: LocalDate) {
    val key = date.format(dateFmt)
    val total = connection.prepareStatement(
        "SELECT COALESCE(SUM(actual_minutes), 0) FROM focus_sessions" +
        " WHERE DATE(start_time) = ? AND completed = 1 AND actual_minutes > 0"
    ).use { ps ->
        ps.setString(1, key)
        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }
    connection.prepareStatement("""
        INSERT INTO daily_completions (date, focus_minutes) VALUES (?, ?)
        ON CONFLICT(date) DO UPDATE SET focus_minutes = excluded.focus_minutes
    """.trimIndent()).use { ps ->
        ps.setString(1, key); ps.setInt(2, total); ps.executeUpdate()
    }
}
