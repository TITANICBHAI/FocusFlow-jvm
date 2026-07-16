package com.focusflow.data

// ── Settings ──────────────────────────────────────────────────────────────────
//
// Extension functions on Database so callers continue using Database.getSetting()
// / Database.setSetting() without any change. All functions synchronize on
// Database (the singleton instance) to preserve the same thread-safety contract
// as the original @Synchronized member functions.

/**
 * Atomically increments an integer setting by 1 inside a single synchronized block.
 * Returns the new value. Uses a single SQL UPDATE so there is no read-modify-write race.
 */
fun Database.incrementSetting(key: String, default: Int = 0): Int {
    synchronized(this) {
        if (!isReady) return default
        return try {
            // Upsert: if the key doesn't exist yet, start from (default + 1)
            connection.prepareStatement(
                """INSERT INTO settings (key, value) VALUES (?, ?)
                   ON CONFLICT(key) DO UPDATE SET value = CAST(CAST(value AS INTEGER) + 1 AS TEXT)"""
            ).use { ps ->
                ps.setString(1, key)
                ps.setString(2, (default + 1).toString())
                ps.executeUpdate()
            }
            connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString("value").toIntOrNull() ?: default else default }
            }
        } catch (_: Exception) { default }
    }
}

fun Database.getSetting(key: String): String? {
    synchronized(this) {
        if (!isReady) return null
        return try {
            connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
            }
        } catch (_: Exception) { null }
    }
}

fun Database.setSetting(key: String, value: String) {
    synchronized(this) {
        if (!isReady) return
        try {
            connection.prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)"
            ).use { ps ->
                ps.setString(1, key); ps.setString(2, value); ps.executeUpdate()
            }
        } catch (_: Exception) {}
    }
}

// ── Keyword Blocker (stored as a comma-joined settings entry) ─────────────────

fun Database.getBlockedKeywords(): List<String> {
    val raw = getSetting("blocked_keywords") ?: return emptyList()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Synchronized fun Database.setBlockedKeywords(keywords: List<String>) {
    setSetting("blocked_keywords", keywords.joinToString(","))
}

fun Database.isKeywordBlockerEnabled(): Boolean =
    getSetting("keyword_blocker_enabled") == "true"

fun Database.setKeywordBlockerEnabled(enabled: Boolean) =
    setSetting("keyword_blocker_enabled", if (enabled) "true" else "false")
