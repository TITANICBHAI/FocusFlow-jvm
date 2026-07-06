package com.focusflow.data

import com.focusflow.data.models.Task
import java.time.LocalDate
import java.time.LocalDateTime

// ── Row mapper ────────────────────────────────────────────────────────────────

internal fun rowToTask(rs: java.sql.ResultSet): Task = Task(
    id               = rs.getString("id"),
    title            = rs.getString("title"),
    description      = rs.getString("description") ?: "",
    durationMinutes  = rs.getInt("duration_minutes"),
    scheduledDate    = rs.getString("scheduled_date")
                         ?.let { LocalDate.parse(it, Database.dateFmt) },
    scheduledTime    = rs.getString("scheduled_time"),
    completed        = rs.getInt("completed") == 1,
    skipped          = rs.getInt("skipped") == 1,
    recurring        = rs.getInt("recurring") == 1,
    recurringType    = rs.getString("recurring_type"),
    priority         = rs.getString("priority") ?: "medium",
    tags             = rs.getString("tags")
                         ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    createdAt        = LocalDateTime.parse(rs.getString("created_at"), Database.dtFmt),
    completedAt      = rs.getString("completed_at")
                         ?.let { LocalDateTime.parse(it, Database.dtFmt) },
    focusMode        = rs.getInt("focus_mode") == 1,
    focusIntensity   = rs.getString("focus_intensity") ?: "standard",
    focusBlockedApps = try {
        rs.getString("focus_blocked_apps")
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    } catch (_: Exception) { emptyList() },
    focusRequirePin  = try { rs.getInt("focus_require_pin") == 1 } catch (_: Exception) { false }
)

// ── Queries ───────────────────────────────────────────────────────────────────

fun Database.getTasks(date: LocalDate? = null): List<Task> = synchronized(this) {
    val sql = if (date != null)
        "SELECT * FROM tasks WHERE scheduled_date = ? ORDER BY scheduled_time ASC NULLS LAST, created_at DESC"
    else
        "SELECT * FROM tasks ORDER BY created_at DESC"
    return connection.prepareStatement(sql).use { ps ->
        if (date != null) ps.setString(1, date.format(dateFmt))
        ps.executeQuery().use { rs ->
            val list = mutableListOf<Task>()
            while (rs.next()) list.add(rowToTask(rs))
            list
        }
    }
}

fun Database.getTasksForDate(date: LocalDate): List<Task> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM tasks WHERE scheduled_date = ? ORDER BY scheduled_time ASC NULLS LAST"
    ).use { ps ->
        ps.setString(1, date.format(dateFmt))
        ps.executeQuery().use { rs ->
            val list = mutableListOf<Task>()
            while (rs.next()) list.add(rowToTask(rs))
            list
        }
    }
}

fun Database.getTasksInRange(startDate: LocalDate, endDate: LocalDate): List<Task> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM tasks WHERE scheduled_date BETWEEN ? AND ? ORDER BY scheduled_date ASC, scheduled_time ASC NULLS LAST"
    ).use { ps ->
        ps.setString(1, startDate.format(dateFmt)); ps.setString(2, endDate.format(dateFmt))
        ps.executeQuery().use { rs ->
            val list = mutableListOf<Task>()
            while (rs.next()) list.add(rowToTask(rs))
            list
        }
    }
}

fun Database.getRecurringTemplates(): List<Task> = synchronized(this) {
    return connection.prepareStatement(
        "SELECT * FROM tasks WHERE recurring = 1 ORDER BY created_at ASC"
    ).use { ps ->
        ps.executeQuery().use { rs ->
            val list = mutableListOf<Task>()
            while (rs.next()) list.add(rowToTask(rs))
            list
        }
    }
}

fun Database.upsertTask(task: Task) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO tasks
        (id, title, description, duration_minutes, scheduled_date, scheduled_time,
         completed, skipped, recurring, recurring_type, priority, tags, created_at,
         completed_at, focus_mode, focus_intensity, focus_blocked_apps, focus_require_pin)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, task.id); ps.setString(2, task.title)
        ps.setString(3, task.description); ps.setInt(4, task.durationMinutes)
        ps.setString(5, task.scheduledDate?.format(dateFmt))
        ps.setString(6, task.scheduledTime)
        ps.setInt(7, if (task.completed) 1 else 0)
        ps.setInt(8, if (task.skipped) 1 else 0)
        ps.setInt(9, if (task.recurring) 1 else 0)
        ps.setString(10, task.recurringType); ps.setString(11, task.priority)
        ps.setString(12, task.tags.joinToString(","))
        ps.setString(13, task.createdAt.format(dtFmt))
        ps.setString(14, task.completedAt?.format(dtFmt))
        ps.setInt(15, if (task.focusMode) 1 else 0)
        ps.setString(16, task.focusIntensity)
        ps.setString(17, task.focusBlockedApps.joinToString(","))
        ps.setInt(18, if (task.focusRequirePin) 1 else 0)
        ps.executeUpdate()
    }
}

fun Database.deleteTask(id: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM tasks WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}

fun Database.completeTask(id: String) = synchronized(this) {
    val now = LocalDateTime.now().format(dtFmt)
    // Transaction: task update + daily completion counter must succeed together.
    connection.autoCommit = false
    try {
        connection.prepareStatement(
            "UPDATE tasks SET completed = 1, completed_at = ? WHERE id = ?"
        ).use { ps -> ps.setString(1, now); ps.setString(2, id); ps.executeUpdate() }
        recordDailyCompletion(LocalDate.now())
        connection.commit()
    } catch (e: Exception) {
        try { connection.rollback() } catch (_: Exception) {}
        throw e
    } finally {
        connection.autoCommit = true
    }
}

fun Database.skipTask(id: String) = synchronized(this) {
    connection.prepareStatement("UPDATE tasks SET skipped = 1 WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}

// Internal helper — called by completeTask() and SessionDao.insertSession().
internal fun Database.recordDailyCompletion(date: LocalDate) {
    val key = date.format(dateFmt)
    connection.prepareStatement("""
        INSERT INTO daily_completions (date, completed_count, total_count) VALUES (?, 1, 1)
        ON CONFLICT(date) DO UPDATE SET completed_count = completed_count + 1,
        total_count = MAX(total_count, completed_count + 1)
    """.trimIndent()).use { ps -> ps.setString(1, key); ps.executeUpdate() }
}
