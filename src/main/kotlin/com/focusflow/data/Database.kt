package com.focusflow.data

import com.focusflow.data.models.*
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Database
 *
 * Owns the SQLite connection lifecycle: opening, integrity-checking, versioned
 * schema migration, and emergency recovery.  All domain read/write operations
 * live in the focused DAO extension-function files in the same package:
 *
 *   TaskDao.kt        — tasks
 *   SessionDao.kt     — focus sessions + daily completion stats
 *   BlockingDao.kt    — block rules, schedules, allowances, keywords, presets,
 *                       network cutoff rules, daily usage
 *   SettingsDao.kt    — key/value settings
 *   HabitDao.kt       — habits + habit entries
 *   ReportingDao.kt   — streaks, temptation log, notes, weekly-report queries
 *
 * Thread-safety: all member functions use @Synchronized (monitor = Database).
 * DAO extension functions use synchronized(this) with the same monitor.
 */
object Database {

    internal val dtFmt   = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    internal val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    // internal so DAO extension files (same module) can access it directly.
    internal lateinit var connection: Connection

    /** True once the DB has been opened and migrated successfully. */
    val isReady: Boolean get() = ::connection.isInitialized

    // ── Startup ───────────────────────────────────────────────────────────────

    fun init() {
        val dbDir  = java.io.File(System.getProperty("user.home") + "/.focusflow")
        val dbFile = java.io.File(dbDir, "focusflow.db")
        dbDir.mkdirs()

        if (!tryOpenAndMigrate(dbFile)) {
            safeBackupBrokenDb(dbDir, dbFile)
            if (!tryOpenAndMigrate(dbFile)) {
                dbFile.delete()
                tryOpenAndMigrate(dbFile)
            }
        }
    }

    /**
     * Opens an in-memory SQLite database with the full migrated schema.
     * For unit tests only — never call this in production code.
     * Closes any previously open connection before creating a new one so
     * each test starts with a guaranteed-empty schema.
     */
    fun initInMemory() {
        if (::connection.isInitialized) {
            try { connection.close() } catch (_: Exception) {}
        }
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite::memory:"
        val conn = ds.connection
        conn.autoCommit = true
        // In-memory SQLite does not support WAL mode; skip that pragma.
        connection = conn
        migrate()
    }

    private fun tryOpenAndMigrate(dbFile: java.io.File): Boolean {
        var localConn: java.sql.Connection? = null
        return try {
            val ds = SQLiteDataSource()
            // busy_timeout in the URL applies before any PRAGMA executes, ensuring
            // SQLite waits up to 5 s even for the very first statement on this connection.
            ds.url = "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=5000"
            localConn = ds.connection
            localConn.autoCommit = true

            localConn.createStatement().use { it.execute("PRAGMA busy_timeout=5000") }
            localConn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
            // PASSIVE checkpoint: best-effort, never blocks readers or writers.
            // TRUNCATE requires exclusive access and can throw SQLITE_BUSY on startup
            // if another process (or a prior crashed instance) still holds the file.
            localConn.createStatement().use { it.execute("PRAGMA wal_checkpoint(PASSIVE)") }

            val integrity = localConn.createStatement()
                .executeQuery("PRAGMA quick_check")
                .use { rs -> if (rs.next()) rs.getString(1) else "error" }
            if (integrity != "ok") {
                localConn.close()
                localConn = null
                return false
            }

            connection = localConn
            migrate()
            true
        } catch (e: Exception) {
            try { localConn?.close() } catch (_: Exception) {}
            val logFile = java.io.File(
                System.getProperty("user.home") + "/.focusflow/crash.log"
            )
            logFile.parentFile?.mkdirs()
            logFile.appendText(
                "[${java.time.LocalDateTime.now()}] DB open failed: ${e.message}\n${e.stackTraceToString()}\n\n"
            )
            com.focusflow.services.CrashReporter.reportCritical(
                source    = "Database.open",
                message   = "Database failed to open — enforcement rules may be inactive.\n**Cause:** ${e.message}",
                throwable = e
            )
            false
        }
    }

    private fun safeBackupBrokenDb(dbDir: java.io.File, dbFile: java.io.File) {
        val ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        listOf(dbFile,
               java.io.File(dbDir, "focusflow.db-shm"),
               java.io.File(dbDir, "focusflow.db-wal")).forEach { f ->
            if (f.exists()) {
                val backup = java.io.File(dbDir, "${f.name}.broken_$ts")
                f.copyTo(backup, overwrite = true)
                f.delete()
            }
        }
        java.io.File(dbDir, "crash.log").appendText(
            "[${java.time.LocalDateTime.now()}] Corrupt DB backed up as focusflow.db.broken_$ts — starting fresh.\n\n"
        )
    }

    // ── Versioned schema migration ─────────────────────────────────────────────
    //
    // PRAGMA user_version tracks which migrations have been applied.
    // Every new schema change gets its own numbered migrate_vN() function.
    // Never edit an existing migrate_vN() — add a new one and bump TARGET_VERSION.

    private val TARGET_VERSION = 6

    private fun migrate() {
        val current = connection.createStatement()
            .executeQuery("PRAGMA user_version")
            .use { rs -> if (rs.next()) rs.getInt(1) else 0 }

        if (current >= TARGET_VERSION) return

        // All steps in one transaction — partial failure rolls back fully.
        connection.autoCommit = false
        try {
            if (current < 1) migrateV1()
            if (current < 2) migrateV2()
            if (current < 3) migrateV3()
            if (current < 4) migrateV4()
            if (current < 5) migrateV5()
            if (current < 6) migrateV6()

            connection.createStatement()
                .executeUpdate("PRAGMA user_version = $TARGET_VERSION")
            connection.commit()
        } catch (e: Exception) {
            try { connection.rollback() } catch (_: Exception) {}
            val logFile = java.io.File(
                System.getProperty("user.home") + "/.focusflow/crash.log"
            )
            logFile.parentFile?.mkdirs()
            logFile.appendText(
                "[${java.time.LocalDateTime.now()}] Migration failed at schema v$current: ${e.message}\n${e.stackTraceToString()}\n\n"
            )
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    // v1 — full baseline schema
    private fun migrateV1() {
        connection.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY, title TEXT NOT NULL,
                    description TEXT DEFAULT '', duration_minutes INTEGER DEFAULT 25,
                    scheduled_date TEXT, scheduled_time TEXT,
                    completed INTEGER DEFAULT 0, skipped INTEGER DEFAULT 0,
                    recurring INTEGER DEFAULT 0, recurring_type TEXT,
                    priority TEXT DEFAULT 'medium', tags TEXT DEFAULT '',
                    created_at TEXT NOT NULL, completed_at TEXT,
                    focus_mode INTEGER DEFAULT 0, focus_intensity TEXT DEFAULT 'standard'
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS focus_sessions (
                    id TEXT PRIMARY KEY, task_id TEXT, task_name TEXT NOT NULL,
                    start_time TEXT NOT NULL, end_time TEXT,
                    planned_minutes INTEGER NOT NULL, actual_minutes INTEGER DEFAULT 0,
                    completed INTEGER DEFAULT 0, interrupted INTEGER DEFAULT 0,
                    notes TEXT DEFAULT ''
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS block_rules (
                    id TEXT PRIMARY KEY, process_name TEXT NOT NULL UNIQUE,
                    display_name TEXT NOT NULL, enabled INTEGER DEFAULT 1,
                    block_network INTEGER DEFAULT 0
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS block_schedules (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    days_of_week TEXT NOT NULL, start_hour INTEGER NOT NULL,
                    start_minute INTEGER NOT NULL, end_hour INTEGER NOT NULL,
                    end_minute INTEGER NOT NULL, enabled INTEGER DEFAULT 1,
                    process_names TEXT DEFAULT ''
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_allowances (
                    process_name TEXT PRIMARY KEY, display_name TEXT NOT NULL,
                    allowance_minutes INTEGER NOT NULL
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_notes (
                    date TEXT PRIMARY KEY, content TEXT NOT NULL,
                    mood INTEGER DEFAULT 3, updated_at TEXT NOT NULL
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS temptation_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    process_name TEXT NOT NULL, display_name TEXT NOT NULL,
                    timestamp TEXT NOT NULL
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY, value TEXT NOT NULL
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_completions (
                    date TEXT PRIMARY KEY, completed_count INTEGER DEFAULT 0,
                    total_count INTEGER DEFAULT 0, focus_minutes INTEGER DEFAULT 0
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS habits (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    emoji TEXT DEFAULT '✅', created_at TEXT NOT NULL
                )
            """.trimIndent())
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS habit_entries (
                    habit_id TEXT NOT NULL, date TEXT NOT NULL,
                    done INTEGER DEFAULT 1, PRIMARY KEY (habit_id, date)
                )
            """.trimIndent())
            // Additive column guards — safe on DBs that already have these columns
            try { st.executeUpdate("ALTER TABLE tasks ADD COLUMN skipped INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { st.executeUpdate("ALTER TABLE tasks ADD COLUMN focus_mode INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { st.executeUpdate("ALTER TABLE tasks ADD COLUMN focus_intensity TEXT DEFAULT 'standard'") } catch (_: Exception) {}
            try { st.executeUpdate("ALTER TABLE daily_completions ADD COLUMN total_count INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { st.executeUpdate("ALTER TABLE daily_completions ADD COLUMN focus_minutes INTEGER DEFAULT 0") } catch (_: Exception) {}
            // Indexes
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_date ON tasks(scheduled_date)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sessions_start ON focus_sessions(start_time)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_temptation_ts ON temptation_log(timestamp)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_date ON daily_notes(date)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_habit_entries ON habit_entries(habit_id, date)")
        }
    }

    // v2 — onboarding preset tracking
    private fun migrateV2() {
        connection.createStatement().use { st ->
            try { st.executeUpdate("ALTER TABLE block_rules ADD COLUMN source TEXT DEFAULT 'manual'") } catch (_: Exception) {}
        }
    }

    // v3 — per-task focus blocked apps + require-pin flag
    private fun migrateV3() {
        connection.createStatement().use { st ->
            try { st.executeUpdate("ALTER TABLE tasks ADD COLUMN focus_blocked_apps TEXT DEFAULT ''") } catch (_: Exception) {}
            try { st.executeUpdate("ALTER TABLE tasks ADD COLUMN focus_require_pin INTEGER DEFAULT 0") } catch (_: Exception) {}
        }
    }

    // v4 — network cutoff rules
    private fun migrateV4() {
        connection.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS network_cutoff_rules (
                    id TEXT PRIMARY KEY, pattern TEXT NOT NULL, mode TEXT NOT NULL,
                    target_process TEXT, target_display_name TEXT, enabled INTEGER DEFAULT 1
                )
            """.trimIndent())
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_net_rules_mode ON network_cutoff_rules(mode)")
        }
    }

    // v5 — user-created block presets
    private fun migrateV5() {
        connection.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS custom_block_presets (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    emoji TEXT NOT NULL DEFAULT '🚫',
                    process_names TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL
                )
            """.trimIndent())
        }
    }

    // v6 — persist daily allowance usage across reboots
    private fun migrateV6() {
        connection.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daily_usage (
                    date TEXT NOT NULL, process_name TEXT NOT NULL,
                    seconds_used INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (date, process_name)
                )
            """.trimIndent())
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_daily_usage_date ON daily_usage(date)")
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Safe backup via SQLite VACUUM INTO — WAL-free, consistent snapshot. */
    @Synchronized fun vacuumInto(destPath: String) {
        connection.createStatement().use { st ->
            st.execute("VACUUM INTO '${destPath.replace("'", "''")}'")
        }
    }

    @Synchronized fun clearAllTasks() {
        connection.createStatement().executeUpdate("DELETE FROM tasks")
    }

    @Synchronized fun clearAllSessions() {
        connection.createStatement().executeUpdate("DELETE FROM focus_sessions")
        connection.createStatement().executeUpdate("DELETE FROM daily_completions")
    }

    @Synchronized fun clearNotes() {
        connection.createStatement().executeUpdate("DELETE FROM daily_notes")
    }

    @Synchronized fun clearTemptationLog() {
        connection.createStatement().executeUpdate("DELETE FROM temptation_log")
    }
}
