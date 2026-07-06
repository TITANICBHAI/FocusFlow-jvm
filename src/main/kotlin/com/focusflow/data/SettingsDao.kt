package com.focusflow.data

// ── Settings ──────────────────────────────────────────────────────────────────
//
// Extension functions on Database so callers continue using Database.getSetting()
// / Database.setSetting() without any change. All functions synchronize on
// Database (the singleton instance) to preserve the same thread-safety contract
// as the original @Synchronized member functions.

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
