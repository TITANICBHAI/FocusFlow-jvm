package com.focusflow.services

import com.focusflow.data.Database
import java.security.MessageDigest

/**
 * SessionPin
 *
 * SHA-256 PIN gate that must be satisfied before ending a session.
 * Every focus-mode session auto-generates a new PIN via autoGenerate().
 * The plain-text PIN is returned once (so the UI can show it) and never
 * stored — only the SHA-256 hash is persisted.
 */
object SessionPin {

    private const val KEY = "session_pin_hash"

    fun isSet(): Boolean = Database.getSetting(KEY)?.isNotBlank() == true

    fun set(rawPin: String) {
        require(rawPin.length >= 8) { "PIN must be at least 8 characters" }
        Database.setSetting(KEY, sha256(rawPin))
    }

    /**
     * Auto-generate a random 10-character alphanumeric PIN, store its hash,
     * and return the plain-text PIN so the UI can display it exactly once.
     * Every call produces a different PIN, so every session is different.
     */
    fun autoGenerate(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val pin = (1..10).map { chars.random() }.joinToString("")
        Database.setSetting(KEY, sha256(pin))
        return pin
    }

    fun verify(rawPin: String): Boolean {
        val stored = Database.getSetting(KEY)
        if (stored.isNullOrBlank()) return true // no PIN set = always pass
        return stored == sha256(rawPin)
    }

    fun clear(rawPin: String): Boolean {
        if (!verify(rawPin)) return false
        Database.setSetting(KEY, "") // isSet() treats blank as unset
        return true
    }

    /** Clear PIN unconditionally (called when a session ends naturally). */
    fun clearForced() {
        Database.setSetting(KEY, "")
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
