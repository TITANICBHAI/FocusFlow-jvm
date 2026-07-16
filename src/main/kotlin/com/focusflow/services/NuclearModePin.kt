package com.focusflow.services

import com.focusflow.data.*
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * NuclearModePin
 *
 * Dedicated PIN gate for disabling Nuclear Mode from the UI.
 * Completely independent of GlobalPin (which guards block-rule edits) and
 * SessionPin (which guards ending a focus session early).
 *
 * Use case: user enables Nuclear Mode with maximum enforcement, then sets
 * this PIN so that turning it off requires authentication — prevents
 * a momentary lapse from bypassing enforcement in under 2 seconds.
 *
 * Design decisions vs. GlobalPin / SessionPin:
 *   • Minimum length is 4 characters (vs. GlobalPin's 8) — this is an
 *     in-session convenience gate, not a permanent security credential.
 *   • No autoGenerate() — user always chooses their own PIN so they can
 *     remember it when a boss calls an emergency meeting.
 *   • clear() does NOT require the old PIN (use clearWithPin() for that).
 *     clearWithoutPin() is the "I forgot" / settings-page management path.
 *
 * Storage format: "saltHex:hashHex"
 *   • saltHex — 32 hex characters (16 random bytes, new per PIN set)
 *   • hashHex — 64 hex characters, SHA-256(saltBytes || pinUTF8)
 *
 * Plain text is never stored.
 */
object NuclearModePin {

    private const val KEY = "nuclear_mode_pin_hash"
    private const val MIN_LENGTH = 4

    /** True when a PIN has been configured. */
    fun isSet(): Boolean = Database.getSetting(KEY)?.isNotBlank() == true

    /**
     * Store a new PIN. Throws [IllegalArgumentException] if shorter than [MIN_LENGTH].
     * Call this both for initial setup and for "change PIN" (just call set() again).
     */
    fun set(rawPin: String) {
        require(rawPin.length >= MIN_LENGTH) {
            "Nuclear Mode PIN must be at least $MIN_LENGTH characters"
        }
        Database.setSetting(KEY, hashPin(rawPin))
    }

    /**
     * Returns true if [rawPin] matches the stored hash, or if no PIN is set
     * (unguarded — callers should check [isSet] before showing a gate dialog).
     */
    fun verify(rawPin: String): Boolean {
        val stored = Database.getSetting(KEY)
        if (stored.isNullOrBlank()) return true
        val idx  = stored.indexOf(':')
        if (idx < 0) return false          // unexpected format — deny
        val salt = stored.substring(0, idx)
        val hash = stored.substring(idx + 1)
        return hash == sha256Salted(salt, rawPin)
    }

    /**
     * Remove the PIN after verifying the old one. Returns false if verification fails.
     */
    fun clearWithPin(rawPin: String): Boolean {
        if (!verify(rawPin)) return false
        Database.setSetting(KEY, "")
        return true
    }

    /**
     * Remove the PIN unconditionally — for the "forgot PIN" path in the setup UI.
     * Does NOT require the old PIN.
     */
    fun clearWithoutPin() {
        Database.setSetting(KEY, "")
    }

    // ── Hashing helpers ───────────────────────────────────────────────────────

    private fun hashPin(rawPin: String): String {
        val salt = generateSalt()
        return "$salt:${sha256Salted(salt, rawPin)}"
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Salted(saltHex: String, input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
