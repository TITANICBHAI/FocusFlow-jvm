package com.focusflow.data

import com.focusflow.data.models.Habit
import com.focusflow.data.models.HabitEntry
import java.time.LocalDate

fun Database.getHabits(): List<Habit> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM habits ORDER BY created_at ASC"
    ).use { rs ->
        val list = mutableListOf<Habit>()
        while (rs.next()) list.add(Habit(
            id        = rs.getString("id"),
            name      = rs.getString("name"),
            emoji     = rs.getString("emoji") ?: "✅",
            createdAt = LocalDate.parse(rs.getString("created_at"), dateFmt)
        ))
        list
    }
}

fun Database.upsertHabit(habit: Habit) = synchronized(this) {
    connection.prepareStatement(
        "INSERT OR REPLACE INTO habits (id, name, emoji, created_at) VALUES (?,?,?,?)"
    ).use { ps ->
        ps.setString(1, habit.id); ps.setString(2, habit.name)
        ps.setString(3, habit.emoji); ps.setString(4, habit.createdAt.format(dateFmt))
        ps.executeUpdate()
    }
}

fun Database.deleteHabit(id: String) = synchronized(this) {
    // Transaction: habit row + its entries deleted atomically.
    connection.autoCommit = false
    try {
        connection.prepareStatement("DELETE FROM habits WHERE id = ?").use { ps ->
            ps.setString(1, id); ps.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM habit_entries WHERE habit_id = ?").use { ps ->
            ps.setString(1, id); ps.executeUpdate()
        }
        connection.commit()
    } catch (e: Exception) {
        try { connection.rollback() } catch (_: Exception) {}
        throw e
    } finally {
        connection.autoCommit = true
    }
}

fun Database.getHabitEntries(habitId: String, since: LocalDate): List<HabitEntry> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM habit_entries WHERE habit_id = ? AND date >= ? ORDER BY date ASC"
    ).use { ps ->
        ps.setString(1, habitId); ps.setString(2, since.format(dateFmt))
        ps.executeQuery().use { rs ->
            val list = mutableListOf<HabitEntry>()
            while (rs.next()) list.add(HabitEntry(
                habitId = rs.getString("habit_id"),
                date    = LocalDate.parse(rs.getString("date"), dateFmt),
                done    = rs.getInt("done") == 1
            ))
            list
        }
    }
}

fun Database.setHabitEntry(habitId: String, date: LocalDate, done: Boolean) = synchronized(this) {
    if (done) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO habit_entries (habit_id, date, done) VALUES (?,?,1)"
        ).use { ps ->
            ps.setString(1, habitId); ps.setString(2, date.format(dateFmt)); ps.executeUpdate()
        }
    } else {
        connection.prepareStatement(
            "DELETE FROM habit_entries WHERE habit_id = ? AND date = ?"
        ).use { ps ->
            ps.setString(1, habitId); ps.setString(2, date.format(dateFmt)); ps.executeUpdate()
        }
    }
}

fun Database.getHabitStreak(habitId: String): Int = synchronized(this) {
    val rows = connection.prepareStatement(
        "SELECT date FROM habit_entries WHERE habit_id = ? AND done = 1 ORDER BY date DESC LIMIT 90"
    ).use { ps ->
        ps.setString(1, habitId)
        ps.executeQuery().use { rs ->
            val l = mutableListOf<LocalDate>()
            while (rs.next()) l.add(LocalDate.parse(rs.getString("date"), dateFmt))
            l
        }
    }
    if (rows.isEmpty()) return 0
    var streak = 0; var expected = LocalDate.now()
    for (date in rows) {
        if (date == expected) { streak++; expected = expected.minusDays(1) }
        else break
    }
    return streak
}
