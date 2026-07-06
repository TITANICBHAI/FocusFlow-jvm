package com.focusflow.data

import com.focusflow.data.models.DailyNote
import com.focusflow.data.models.FocusSession
import com.focusflow.data.models.TemptationEntry
import java.time.LocalDate
import java.time.LocalDateTime

// ── Streak ────────────────────────────────────────────────────────────────────

fun Database.getCurrentStreak(): Int = synchronized(this) {
    val rows = connection.createStatement().executeQuery(
        "SELECT date FROM daily_completions WHERE completed_count > 0 ORDER BY date DESC LIMIT 90"
    ).use { rs ->
        val l = mutableListOf<LocalDate>()
        while (rs.next()) l.add(LocalDate.parse(rs.getString("date"), dateFmt))
        l
    }
    if (rows.isEmpty()) return 0
    var streak = 0; var expected = LocalDate.now()
    for (date in rows) {
        if (date == expected) { streak++; expected = expected.minusDays(1) }
        else if (date == LocalDate.now().minusDays(1) && streak == 0) {
            expected = date.minusDays(1); streak++
        }
        else break
    }
    return streak
}

fun Database.getBestStreak(): Int = synchronized(this) {
    val rows = connection.createStatement().executeQuery(
        "SELECT date FROM daily_completions WHERE completed_count > 0 ORDER BY date ASC"
    ).use { rs ->
        val l = mutableListOf<LocalDate>()
        while (rs.next()) l.add(LocalDate.parse(rs.getString("date"), dateFmt))
        l
    }
    if (rows.isEmpty()) return 0
    var best = 0; var current = 0; var prev: LocalDate? = null
    for (date in rows) {
        current = if (prev != null && date == prev.plusDays(1)) current + 1 else 1
        if (current > best) best = current
        prev = date
    }
    return best
}

// ── Temptation Log ────────────────────────────────────────────────────────────

fun Database.logTemptation(processName: String, displayName: String) = synchronized(this) {
    connection.prepareStatement(
        "INSERT INTO temptation_log (process_name, display_name, timestamp) VALUES (?,?,?)"
    ).use { ps ->
        ps.setString(1, processName); ps.setString(2, displayName)
        ps.setString(3, LocalDateTime.now().format(dtFmt)); ps.executeUpdate()
    }
    connection.createStatement().executeUpdate(
        "DELETE FROM temptation_log WHERE id NOT IN (SELECT id FROM temptation_log ORDER BY id DESC LIMIT 1000)"
    )
}

fun Database.getTemptationLog(sinceDays: Int = 7): List<TemptationEntry> = synchronized(this) {
    val cutoff = LocalDateTime.now().minusDays(sinceDays.toLong()).format(dtFmt)
    return connection.prepareStatement(
        "SELECT * FROM temptation_log WHERE timestamp >= ? ORDER BY timestamp DESC"
    ).use { ps ->
        ps.setString(1, cutoff)
        ps.executeQuery().use { rs ->
            val list = mutableListOf<TemptationEntry>()
            while (rs.next()) list.add(TemptationEntry(
                rs.getString("process_name"), rs.getString("display_name"),
                LocalDateTime.parse(rs.getString("timestamp"), dtFmt)
            ))
            list
        }
    }
}

// ── Daily Notes ───────────────────────────────────────────────────────────────

fun Database.getNote(date: LocalDate): DailyNote? = synchronized(this) {
    return connection.prepareStatement("SELECT * FROM daily_notes WHERE date = ?").use { ps ->
        ps.setString(1, date.format(dateFmt))
        ps.executeQuery().use { rs ->
            if (rs.next()) DailyNote(
                LocalDate.parse(rs.getString("date"), dateFmt),
                rs.getString("content"), rs.getInt("mood"),
                LocalDateTime.parse(rs.getString("updated_at"), dtFmt)
            ) else null
        }
    }
}

fun Database.upsertNote(note: DailyNote) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO daily_notes (date, content, mood, updated_at) VALUES (?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, note.date.format(dateFmt)); ps.setString(2, note.content)
        ps.setInt(3, note.mood); ps.setString(4, note.updatedAt.format(dtFmt))
        ps.executeUpdate()
    }
}

// ── Weekly Report Queries ─────────────────────────────────────────────────────

fun Database.getSessionsInRange(startDate: String, endDate: String): List<FocusSession> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM focus_sessions WHERE DATE(start_time) BETWEEN ? AND ? ORDER BY start_time ASC"
    ).use { ps ->
        ps.setString(1, startDate); ps.setString(2, endDate)
        ps.executeQuery().use { rs ->
            val list = mutableListOf<FocusSession>()
            while (rs.next()) list.add(rowToSession(rs))
            list
        }
    }
}

fun Database.getCompletedTasksInRange(startDate: String, endDate: String): Int = synchronized(this) {
    return connection.prepareStatement(
        "SELECT COUNT(*) FROM tasks WHERE completed = 1 AND DATE(completed_at) BETWEEN ? AND ?"
    ).use { ps ->
        ps.setString(1, startDate); ps.setString(2, endDate)
        ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
    }
}

fun Database.getTemptationsInRange(startDate: String, endDate: String): Int = synchronized(this) {
    return connection.prepareStatement(
        "SELECT COUNT(*) FROM temptation_log WHERE DATE(timestamp) BETWEEN ? AND ?"
    ).use { ps ->
        ps.setString(1, startDate); ps.setString(2, endDate)
        ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
    }
}

fun Database.getTemptationBreakdownInRange(
    startDate: String, endDate: String, limit: Int = 5
): List<Pair<String, Int>> = synchronized(this) {
    return connection.prepareStatement("""
        SELECT display_name, COUNT(*) AS cnt FROM temptation_log
        WHERE DATE(timestamp) BETWEEN ? AND ?
        GROUP BY display_name ORDER BY cnt DESC LIMIT ?
    """.trimIndent()).use { ps ->
        ps.setString(1, startDate); ps.setString(2, endDate); ps.setInt(3, limit)
        ps.executeQuery().use { rs ->
            val list = mutableListOf<Pair<String, Int>>()
            while (rs.next()) list.add(rs.getString("display_name") to rs.getInt("cnt"))
            list
        }
    }
}
