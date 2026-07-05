package com.focusflow.services

import com.focusflow.data.Database
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * GlobalPin
 *
 * Persistent, always-active PIN gate (minimum 8 characters).
 * Unlike SessionPin (which is session-scoped), this PIN is permanent and
 * required to REMOVE or DISABLE anything in FocusFlow.
 * Adding is always free; removing always costs the PIN.
 *
 * Storage format: "saltHex:hashHex"
 *   - saltHex  — 32 hex characters (16 random bytes, generated per PIN)
 *   - hashHex  — 64 hex characters, SHA-256(saltBytes || pinUTF8)
 *
 * Legacy format (no colon, 64 hex chars): verified with unsalted SHA-256 for
 * backward compatibility, then transparently re-stored in the salted format on
 * first successful verify so no stored PIN stays unprotected.
 *
 * Plain text is never stored.
 */
object GlobalPin {

    private const val KEY          = "global_pin_hash"
    private const val DECLINED_KEY = "global_pin_skipped"

    fun isSet(): Boolean     = Database.getSetting(KEY)?.isNotBlank() == true
    fun isDeclined(): Boolean = Database.getSetting(DECLINED_KEY) == "true"
    fun setDeclined()         { Database.setSetting(DECLINED_KEY, "true") }

    fun set(rawPin: String) {
        require(rawPin.length >= 8) { "PIN must be at least 8 characters" }
        Database.setSetting(KEY, hashPin(rawPin))
    }

    /**
     * Auto-generate a random 10-character alphanumeric PIN, store its salted hash,
     * and return the plain-text once so the UI can display it.
     */
    fun autoGenerate(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val pin = (1..10).map { chars.random() }.joinToString("")
        Database.setSetting(KEY, hashPin(pin))
        return pin
    }

    /** Returns true if PIN is correct OR if no PIN is set (unguarded). */
    fun verify(rawPin: String): Boolean {
        val stored = Database.getSetting(KEY)
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
        Database.setSetting(KEY, "")
        return true
    }

    /**
     * Emergency recovery reset — clears the PIN hash without requiring the old PIN.
     * Intended for the "Forgot PIN" flow where the user confirms with a typed phrase.
     * Also resets the "declined" flag so the setup dialog will be offered again.
     */
    fun resetWithoutPin() {
        Database.setSetting(KEY, "")
        Database.setSetting(DECLINED_KEY, "false")
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
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
