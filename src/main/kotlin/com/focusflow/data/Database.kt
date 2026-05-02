package com.focusflow.data

import com.focusflow.data.models.*
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object Database {

    private val dtFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    private lateinit var connection: Connection

    fun init() {
        val dbPath = System.getProperty("user.home") + "/.focusflow/focusflow.db"
        java.io.File(dbPath).parentFile.mkdirs()
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite:$dbPath"
        connection = ds.connection
        connection.autoCommit = true
        migrate()
    }

    private fun migrate() {
        connection.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    duration_minutes INTEGER DEFAULT 25,
                    scheduled_date TEXT,
                    scheduled_time TEXT,
                    completed INTEGER DEFAULT 0,
                    recurring INTEGER DEFAULT 0,
                    recurring_type TEXT,
                    priority TEXT DEFAULT 'medium',
                    tags TEXT DEFAULT '[]',
                    created_at TEXT NOT NULL,
                    completed_at TEXT
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS focus_sessions (
                    id TEXT PRIMARY KEY,
                    task_id TEXT,
                    task_name TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT,
                    planned_minutes INTEGER NOT NULL,
                    actual_minutes INTEGER DEFAULT 0,
                    completed INTEGER DEFAULT 0,
                    interrupted INTEGER DEFAULT 0,
                    notes TEXT DEFAULT ''
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS block_rules (
                    id TEXT PRIMARY KEY,
                    process_name TEXT NOT NULL UNIQUE,
                    display_name TEXT NOT NULL,
                    enabled INTEGER DEFAULT 1,
                    block_network INTEGER DEFAULT 0
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_notes (
                    date TEXT PRIMARY KEY,
                    content TEXT NOT NULL,
                    mood INTEGER DEFAULT 3,
                    updated_at TEXT NOT NULL
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS temptation_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    process_name TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    timestamp TEXT NOT NULL
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent())

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_completions (
                    date TEXT PRIMARY KEY,
                    completed_count INTEGER DEFAULT 0,
                    total_focus_minutes INTEGER DEFAULT 0
                )
            """.trimIndent())
        }
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    fun getTasks(date: LocalDate? = null): List<Task> {
        val sql = if (date != null)
            "SELECT * FROM tasks WHERE scheduled_date = ? OR scheduled_date IS NULL ORDER BY created_at DESC"
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

    fun getTasksForDate(date: LocalDate): List<Task> {
        val sql = "SELECT * FROM tasks WHERE scheduled_date = ? ORDER BY scheduled_time ASC"
        return connection.prepareStatement(sql).use { ps ->
            ps.setString(1, date.format(dateFmt))
            ps.executeQuery().use { rs ->
                val list = mutableListOf<Task>()
                while (rs.next()) list.add(rowToTask(rs))
                list
            }
        }
    }

    fun upsertTask(task: Task) {
        val sql = """
            INSERT OR REPLACE INTO tasks
            (id, title, description, duration_minutes, scheduled_date, scheduled_time,
             completed, recurring, recurring_type, priority, tags, created_at, completed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, task.id)
            ps.setString(2, task.title)
            ps.setString(3, task.description)
            ps.setInt(4, task.durationMinutes)
            ps.setString(5, task.scheduledDate?.format(dateFmt))
            ps.setString(6, task.scheduledTime)
            ps.setInt(7, if (task.completed) 1 else 0)
            ps.setInt(8, if (task.recurring) 1 else 0)
            ps.setString(9, task.recurringType)
            ps.setString(10, task.priority)
            ps.setString(11, task.tags.joinToString(","))
            ps.setString(12, task.createdAt.format(dtFmt))
            ps.setString(13, task.completedAt?.format(dtFmt))
            ps.executeUpdate()
        }
    }

    fun deleteTask(id: String) {
        connection.prepareStatement("DELETE FROM tasks WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun completeTask(id: String) {
        val now = LocalDateTime.now().format(dtFmt)
        connection.prepareStatement(
            "UPDATE tasks SET completed = 1, completed_at = ? WHERE id = ?"
        ).use { ps ->
            ps.setString(1, now)
            ps.setString(2, id)
            ps.executeUpdate()
        }
        recordDailyCompletion(LocalDate.now())
    }

    private fun recordDailyCompletion(date: LocalDate) {
        val key = date.format(dateFmt)
        connection.prepareStatement("""
            INSERT INTO daily_completions (date, completed_count) VALUES (?, 1)
            ON CONFLICT(date) DO UPDATE SET completed_count = completed_count + 1
        """.trimIndent()).use { ps ->
            ps.setString(1, key)
            ps.executeUpdate()
        }
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    fun insertSession(session: FocusSession) {
        connection.prepareStatement("""
            INSERT INTO focus_sessions
            (id, task_id, task_name, start_time, end_time, planned_minutes,
             actual_minutes, completed, interrupted, notes)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()).use { ps ->
            ps.setString(1, session.id)
            ps.setString(2, session.taskId)
            ps.setString(3, session.taskName)
            ps.setString(4, session.startTime.format(dtFmt))
            ps.setString(5, session.endTime?.format(dtFmt))
            ps.setInt(6, session.plannedMinutes)
            ps.setInt(7, session.actualMinutes)
            ps.setInt(8, if (session.completed) 1 else 0)
            ps.setInt(9, if (session.interrupted) 1 else 0)
            ps.setString(10, session.notes)
            ps.executeUpdate()
        }
    }

    fun getRecentSessions(limit: Int = 50): List<FocusSession> {
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

    fun getTotalFocusMinutesToday(): Int {
        val today = LocalDate.now().format(dateFmt)
        return connection.prepareStatement(
            "SELECT COALESCE(SUM(actual_minutes), 0) FROM focus_sessions WHERE DATE(start_time) = ?"
        ).use { ps ->
            ps.setString(1, today)
            ps.executeQuery().use { it.getInt(1) }
        }
    }

    // ── Block Rules ───────────────────────────────────────────────────────────

    fun getBlockRules(): List<BlockRule> {
        return connection.createStatement().executeQuery(
            "SELECT * FROM block_rules ORDER BY display_name"
        ).use { rs ->
            val list = mutableListOf<BlockRule>()
            while (rs.next()) list.add(rowToBlockRule(rs))
            list
        }
    }

    fun getEnabledBlockProcesses(): Set<String> {
        return connection.createStatement().executeQuery(
            "SELECT process_name FROM block_rules WHERE enabled = 1"
        ).use { rs ->
            val set = mutableSetOf<String>()
            while (rs.next()) set.add(rs.getString("process_name").lowercase())
            set
        }
    }

    fun upsertBlockRule(rule: BlockRule) {
        connection.prepareStatement("""
            INSERT OR REPLACE INTO block_rules (id, process_name, display_name, enabled, block_network)
            VALUES (?,?,?,?,?)
        """.trimIndent()).use { ps ->
            ps.setString(1, rule.id)
            ps.setString(2, rule.processName)
            ps.setString(3, rule.displayName)
            ps.setInt(4, if (rule.enabled) 1 else 0)
            ps.setInt(5, if (rule.blockNetwork) 1 else 0)
            ps.executeUpdate()
        }
    }

    fun deleteBlockRule(id: String) {
        connection.prepareStatement("DELETE FROM block_rules WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun getSetting(key: String): String? {
        return connection.prepareStatement(
            "SELECT value FROM settings WHERE key = ?"
        ).use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("value") else null
            }
        }
    }

    fun setSetting(key: String, value: String) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)"
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    // ── Temptation Log ────────────────────────────────────────────────────────

    fun logTemptation(processName: String, displayName: String) {
        connection.prepareStatement(
            "INSERT INTO temptation_log (process_name, display_name, timestamp) VALUES (?,?,?)"
        ).use { ps ->
            ps.setString(1, processName)
            ps.setString(2, displayName)
            ps.setString(3, LocalDateTime.now().format(dtFmt))
            ps.executeUpdate()
        }
        // Keep only last 500 entries
        connection.createStatement().executeUpdate(
            "DELETE FROM temptation_log WHERE id NOT IN (SELECT id FROM temptation_log ORDER BY id DESC LIMIT 500)"
        )
    }

    fun getTemptationLog(sinceDays: Int = 7): List<TemptationEntry> {
        val cutoff = LocalDateTime.now().minusDays(sinceDays.toLong()).format(dtFmt)
        return connection.prepareStatement(
            "SELECT * FROM temptation_log WHERE timestamp >= ? ORDER BY timestamp DESC"
        ).use { ps ->
            ps.setString(1, cutoff)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<TemptationEntry>()
                while (rs.next()) list.add(
                    TemptationEntry(
                        rs.getString("process_name"),
                        rs.getString("display_name"),
                        LocalDateTime.parse(rs.getString("timestamp"), dtFmt)
                    )
                )
                list
            }
        }
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    fun getCurrentStreak(): Int {
        val rows = connection.createStatement().executeQuery(
            "SELECT date FROM daily_completions WHERE completed_count > 0 ORDER BY date DESC LIMIT 60"
        ).use { rs ->
            val list = mutableListOf<LocalDate>()
            while (rs.next()) list.add(LocalDate.parse(rs.getString("date"), dateFmt))
            list
        }
        if (rows.isEmpty()) return 0
        var streak = 0
        var expected = LocalDate.now()
        for (date in rows) {
            if (date == expected) { streak++; expected = expected.minusDays(1) }
            else if (date == expected.minusDays(1)) { expected = expected.minusDays(2) }
            else break
        }
        return streak
    }

    // ── Daily Notes ───────────────────────────────────────────────────────────

    fun getNote(date: LocalDate): DailyNote? {
        return connection.prepareStatement(
            "SELECT * FROM daily_notes WHERE date = ?"
        ).use { ps ->
            ps.setString(1, date.format(dateFmt))
            ps.executeQuery().use { rs ->
                if (rs.next()) DailyNote(
                    date = LocalDate.parse(rs.getString("date"), dateFmt),
                    content = rs.getString("content"),
                    mood = rs.getInt("mood"),
                    updatedAt = LocalDateTime.parse(rs.getString("updated_at"), dtFmt)
                ) else null
            }
        }
    }

    fun upsertNote(note: DailyNote) {
        connection.prepareStatement("""
            INSERT OR REPLACE INTO daily_notes (date, content, mood, updated_at)
            VALUES (?,?,?,?)
        """.trimIndent()).use { ps ->
            ps.setString(1, note.date.format(dateFmt))
            ps.setString(2, note.content)
            ps.setInt(3, note.mood)
            ps.setString(4, note.updatedAt.format(dtFmt))
            ps.executeUpdate()
        }
    }

    // ── Weekly Report queries ─────────────────────────────────────────────────

    fun getSessionsInRange(startDate: String, endDate: String): List<FocusSession> {
        return connection.prepareStatement(
            "SELECT * FROM focus_sessions WHERE DATE(start_time) BETWEEN ? AND ? ORDER BY start_time ASC"
        ).use { ps ->
            ps.setString(1, startDate)
            ps.setString(2, endDate)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<FocusSession>()
                while (rs.next()) list.add(rowToSession(rs))
                list
            }
        }
    }

    fun getCompletedTasksInRange(startDate: String, endDate: String): Int {
        return connection.prepareStatement(
            "SELECT COUNT(*) FROM tasks WHERE completed = 1 AND DATE(completed_at) BETWEEN ? AND ?"
        ).use { ps ->
            ps.setString(1, startDate)
            ps.setString(2, endDate)
            ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
    }

    fun getTemptationsInRange(startDate: String, endDate: String): Int {
        return connection.prepareStatement(
            "SELECT COUNT(*) FROM temptation_log WHERE DATE(timestamp) BETWEEN ? AND ?"
        ).use { ps ->
            ps.setString(1, startDate)
            ps.setString(2, endDate)
            ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    private fun rowToTask(rs: java.sql.ResultSet): Task = Task(
        id = rs.getString("id"),
        title = rs.getString("title"),
        description = rs.getString("description") ?: "",
        durationMinutes = rs.getInt("duration_minutes"),
        scheduledDate = rs.getString("scheduled_date")?.let { LocalDate.parse(it, dateFmt) },
        scheduledTime = rs.getString("scheduled_time"),
        completed = rs.getInt("completed") == 1,
        recurring = rs.getInt("recurring") == 1,
        recurringType = rs.getString("recurring_type"),
        priority = rs.getString("priority") ?: "medium",
        tags = rs.getString("tags")?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        createdAt = LocalDateTime.parse(rs.getString("created_at"), dtFmt),
        completedAt = rs.getString("completed_at")?.let { LocalDateTime.parse(it, dtFmt) }
    )

    private fun rowToSession(rs: java.sql.ResultSet): FocusSession = FocusSession(
        id = rs.getString("id"),
        taskId = rs.getString("task_id"),
        taskName = rs.getString("task_name"),
        startTime = LocalDateTime.parse(rs.getString("start_time"), dtFmt),
        endTime = rs.getString("end_time")?.let { LocalDateTime.parse(it, dtFmt) },
        plannedMinutes = rs.getInt("planned_minutes"),
        actualMinutes = rs.getInt("actual_minutes"),
        completed = rs.getInt("completed") == 1,
        interrupted = rs.getInt("interrupted") == 1,
        notes = rs.getString("notes") ?: ""
    )

    private fun rowToBlockRule(rs: java.sql.ResultSet): BlockRule = BlockRule(
        id = rs.getString("id"),
        processName = rs.getString("process_name"),
        displayName = rs.getString("display_name"),
        enabled = rs.getInt("enabled") == 1,
        blockNetwork = rs.getInt("block_network") == 1
    )
}
