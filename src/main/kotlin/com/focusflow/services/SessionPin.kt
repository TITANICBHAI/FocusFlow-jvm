package com.focusflow.services

import com.focusflow.data.Database
import java.security.MessageDigest

/**
 * SessionPin
 *
 * SHA-256 PIN gate that must be satisfied before ending a session.
 * Ported directly from Android's SessionPinModule.kt —
 * uses java.security.MessageDigest which is standard JVM (not Android).
 *
 * Security note vs Android:
 *   On Android, the PIN check is enforced at the Kotlin native layer — even if
 *   someone bypasses the React Native JS layer, the Kotlin code still rejects them.
 *   On desktop JVM, a user with admin rights can attach a debugger or kill the JVM
 *   process. This is a soft deterrent, not a hard one.
 */
object SessionPin {

    private const val KEY = "session_pin_hash"

    fun isSet(): Boolean = Database.getSetting(KEY) != null

    fun set(rawPin: String) {
        require(rawPin.isNotBlank()) { "PIN must not be empty" }
        Database.setSetting(KEY, sha256(rawPin))
    }

    fun verify(rawPin: String): Boolean {
        val stored = Database.getSetting(KEY) ?: return true // no PIN set = always pass
        return stored == sha256(rawPin)
    }

    fun clear(rawPin: String): Boolean {
        if (!verify(rawPin)) return false
        Database.setSetting(KEY, "")
        return true
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
