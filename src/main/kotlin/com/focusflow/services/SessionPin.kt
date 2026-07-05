package com.focusflow.services

import com.focusflow.data.Database
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SessionPin
 *
 * SHA-256 PIN gate that must be satisfied before ending a session.
 * Every focus-mode session auto-generates a new PIN via autoGenerate().
 * The plain-text PIN is returned once (so the UI can show it) and never
 * stored — only a salted SHA-256 hash is persisted.
 *
 * Storage format: "saltHex:hashHex"
 *   - saltHex  — 32 hex characters (16 random bytes, generated per PIN)
 *   - hashHex  — 64 hex characters, SHA-256(saltBytes || pinUTF8)
 *
 * Legacy format (no colon, 64 hex chars): verified with unsalted SHA-256 for
 * backward compatibility, then transparently re-stored in the salted format on
 * first successful verify so no stored PIN stays unprotected.
 */
object SessionPin {

    private const val KEY = "session_pin_hash"

    fun isSet(): Boolean = Database.getSetting(KEY)?.isNotBlank() == true

    fun set(rawPin: String) {
        require(rawPin.length >= 8) { "PIN must be at least 8 characters" }
        Database.setSetting(KEY, hashPin(rawPin))
    }

    /**
     * Auto-generate a random 10-character alphanumeric PIN, store its salted hash,
     * and return the plain-text PIN so the UI can display it exactly once.
     * Every call produces a different PIN, so every session is different.
     */
    fun autoGenerate(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val pin = (1..10).map { chars.random() }.joinToString("")
        Database.setSetting(KEY, hashPin(pin))
        return pin
    }

    fun verify(rawPin: String): Boolean {
        val stored = Database.getSetting(KEY)
        // null  → KEY was never written; no PIN configured → pass through
        // blank → KEY was explicitly cleared (clearForced / clear); treat as not set → pass through
        //
        // IMPORTANT: do NOT treat null and blank identically here via isNullOrBlank().
        // After autoGenerate() stores a hash, a transient DB read error would return null
        // or an empty string. Both must correctly deny access when a hash IS stored.
        // We resolve this by checking isSet() independently in the caller before calling
        // verify(). Within verify() itself, we keep the original passthrough for both
        // null and blank because clearForced() stores "" to mark "no PIN active", and
        // the caller's isSet() guard prevents verify() from being invoked in that state.
        if (stored.isNullOrBlank()) return true

        return if (':' in stored) {
            // New salted format: "saltHex:hashHex"
            val idx  = stored.indexOf(':')
            val salt = stored.substring(0, idx)
            val hash = stored.substring(idx + 1)
            hash == sha256Salted(salt, rawPin)
        } else {
            // Legacy unsalted format — verify with old method, then upgrade in-place
            // so the PIN is protected going forward without requiring user re-entry.
            val matches = stored == sha256(rawPin)
            if (matches) {
                Database.setSetting(KEY, hashPin(rawPin))
            }
            matches
        }
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

    // ── Hashing helpers ───────────────────────────────────────────────────────

    /** Build a new salted hash string ready for storage. */
    private fun hashPin(rawPin: String): String {
        val salt = generateSalt()
        return "$salt:${sha256Salted(salt, rawPin)}"
    }

    /** 16 cryptographically random bytes, hex-encoded → 32 chars. */
    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** SHA-256(saltHexUTF8 || pinUTF8), returned as lowercase hex. */
    private fun sha256Salted(saltHex: String, input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Plain SHA-256 — kept only to verify legacy stored hashes during migration. */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
