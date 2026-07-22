package com.focusflow

import com.focusflow.data.Database
import com.focusflow.data.getSetting
import com.focusflow.data.setSetting
import com.focusflow.services.GlobalPin
import com.focusflow.services.NuclearModePin
import com.focusflow.services.SessionPin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SessionPin and GlobalPin.
 *
 * Each test gets a fresh in-memory SQLite database via Database.initInMemory(),
 * so there is no disk state to carry over between runs.
 */
class PinHashingTest {

    @BeforeEach
    fun setUp() {
        Database.initInMemory()
        SessionPin.clearForced()
        GlobalPin.resetWithoutPin()
        NuclearModePin.clearWithoutPin()
    }

    @AfterEach
    fun tearDown() {
        SessionPin.clearForced()
        GlobalPin.resetWithoutPin()
        NuclearModePin.clearWithoutPin()
    }

    // ── SessionPin — basic set / verify ───────────────────────────────────────

    @Test
    fun `SessionPin set then verify with correct PIN passes`() {
        SessionPin.set("MySecret1")
        assertTrue(SessionPin.verify("MySecret1"))
    }

    @Test
    fun `SessionPin verify with wrong PIN fails`() {
        SessionPin.set("MySecret1")
        assertFalse(SessionPin.verify("WrongPin1"))
    }

    @Test
    fun `SessionPin isSet returns false when no PIN stored`() {
        assertFalse(SessionPin.isSet())
    }

    @Test
    fun `SessionPin isSet returns true after set`() {
        SessionPin.set("MySecret1")
        assertTrue(SessionPin.isSet())
    }

    @Test
    fun `SessionPin clearForced then isSet returns false`() {
        SessionPin.set("MySecret1")
        SessionPin.clearForced()
        assertFalse(SessionPin.isSet())
    }

    // ── SessionPin — salted storage format ────────────────────────────────────

    @Test
    fun `SessionPin stored value uses salted format saltHex colon hashHex`() {
        SessionPin.set("MySecret1")
        val stored = Database.getSetting("session_pin_hash")!!
        assertTrue(':' in stored, "Expected salted format 'saltHex:hashHex' but got: $stored")
        val parts = stored.split(":")
        assertEquals(2,  parts.size,       "Should be exactly two colon-separated parts")
        assertEquals(32, parts[0].length,  "Salt should be 32 hex chars (16 bytes)")
        assertEquals(64, parts[1].length,  "Hash should be 64 hex chars (SHA-256)")
    }

    @Test
    fun `SessionPin two different PINs produce different stored values`() {
        SessionPin.set("MySecret1")
        val hash1 = Database.getSetting("session_pin_hash")!!
        SessionPin.set("MySecret2")
        val hash2 = Database.getSetting("session_pin_hash")!!
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `SessionPin same PIN set twice produces different salts`() {
        // Each set() call generates a fresh random salt → different stored value even for same PIN
        SessionPin.set("MySecret1")
        val hash1 = Database.getSetting("session_pin_hash")!!
        SessionPin.set("MySecret1")
        val hash2 = Database.getSetting("session_pin_hash")!!
        assertNotEquals(hash1, hash2, "Same PIN should yield different stored values due to different salts")
    }

    // ── SessionPin — autoGenerate ─────────────────────────────────────────────

    @Test
    fun `SessionPin autoGenerate returns a 10-char PIN that verifies`() {
        val pin = SessionPin.autoGenerate()
        assertEquals(10, pin.length, "Auto-generated PIN must be 10 characters")
        assertTrue(SessionPin.isSet())
        assertTrue(SessionPin.verify(pin))
    }

    // ── SessionPin — passthrough when no PIN set ──────────────────────────────

    @Test
    fun `SessionPin verify passes through when no PIN is set`() {
        assertFalse(SessionPin.isSet())
        // When no PIN is configured, verify() returns true for any input
        assertTrue(SessionPin.verify("anything"))
    }

    // ── SessionPin — legacy hash migration ───────────────────────────────────

    @Test
    fun `SessionPin legacy unsalted hash is accepted and upgraded on successful verify`() {
        // Write a raw SHA-256 hash (no colon, legacy format) directly to the DB
        val legacyPin  = "legacypin1"
        val legacyHash = sha256Hex(legacyPin)
        Database.setSetting("session_pin_hash", legacyHash)

        // verify() should accept it and atomically re-store in salted format
        assertTrue(SessionPin.verify(legacyPin))
        val updated = Database.getSetting("session_pin_hash")!!
        assertTrue(':' in updated, "Legacy hash should have been upgraded to salted format on success")
    }

    @Test
    fun `SessionPin legacy hash wrong PIN is rejected and NOT migrated`() {
        val legacyPin  = "legacypin1"
        val legacyHash = sha256Hex(legacyPin)
        Database.setSetting("session_pin_hash", legacyHash)

        assertFalse(SessionPin.verify("wrongpin1"))
        // The stored hash must be left unchanged (not upgraded to salted format)
        assertEquals(legacyHash, Database.getSetting("session_pin_hash"),
            "Failed verification must NOT migrate the stored hash")
    }

    // ── SessionPin — clear with PIN ───────────────────────────────────────────

    @Test
    fun `SessionPin clear with correct PIN removes hash`() {
        SessionPin.set("MySecret1")
        assertTrue(SessionPin.clear("MySecret1"))
        assertFalse(SessionPin.isSet())
    }

    @Test
    fun `SessionPin clear with wrong PIN returns false and preserves hash`() {
        SessionPin.set("MySecret1")
        assertFalse(SessionPin.clear("WrongPin1"))
        assertTrue(SessionPin.isSet())
    }

    // ── GlobalPin — basic set / verify ───────────────────────────────────────

    @Test
    fun `GlobalPin set then verify with correct PIN passes`() {
        GlobalPin.set("GlobalPin1")
        assertTrue(GlobalPin.verify("GlobalPin1"))
    }

    @Test
    fun `GlobalPin verify with wrong PIN fails`() {
        GlobalPin.set("GlobalPin1")
        assertFalse(GlobalPin.verify("BadGlobal1"))
    }

    @Test
    fun `GlobalPin verify passes through when no PIN set`() {
        assertFalse(GlobalPin.isSet())
        assertTrue(GlobalPin.verify("anything"))
    }

    @Test
    fun `GlobalPin stored value uses salted format`() {
        GlobalPin.set("GlobalPin1")
        val stored = Database.getSetting("global_pin_hash")!!
        assertTrue(':' in stored)
        val parts = stored.split(":")
        assertEquals(32, parts[0].length, "Salt must be 32 hex chars")
        assertEquals(64, parts[1].length, "Hash must be 64 hex chars")
    }

    // ── GlobalPin — isDeclined / setDeclined ─────────────────────────────────

    @Test
    fun `GlobalPin resetWithoutPin clears hash and declined flag`() {
        GlobalPin.set("GlobalPin1")
        GlobalPin.setDeclined()
        GlobalPin.resetWithoutPin()
        assertFalse(GlobalPin.isSet())
        assertFalse(GlobalPin.isDeclined())
    }

    @Test
    fun `GlobalPin setDeclined persists across reads`() {
        GlobalPin.setDeclined()
        assertTrue(GlobalPin.isDeclined())
    }

    // ── GlobalPin — clear with PIN ────────────────────────────────────────────

    @Test
    fun `GlobalPin clear with correct PIN removes hash`() {
        GlobalPin.set("GlobalPin1")
        assertTrue(GlobalPin.clear("GlobalPin1"))
        assertFalse(GlobalPin.isSet())
    }

    @Test
    fun `GlobalPin clear with wrong PIN returns false and preserves hash`() {
        GlobalPin.set("GlobalPin1")
        assertFalse(GlobalPin.clear("WrongPin1"))
        assertTrue(GlobalPin.isSet())
    }

    // ── NuclearModePin — basic set / verify ──────────────────────────────────

    @Test
    fun `NuclearModePin set then verify with correct PIN passes`() {
        NuclearModePin.set("nuke")
        assertTrue(NuclearModePin.verify("nuke"))
    }

    @Test
    fun `NuclearModePin verify with wrong PIN fails`() {
        NuclearModePin.set("nuke")
        assertFalse(NuclearModePin.verify("wrong"))
    }

    @Test
    fun `NuclearModePin isSet returns false when no PIN stored`() {
        assertFalse(NuclearModePin.isSet())
    }

    @Test
    fun `NuclearModePin isSet returns true after set`() {
        NuclearModePin.set("nuke")
        assertTrue(NuclearModePin.isSet())
    }

    @Test
    fun `NuclearModePin verify passes through when no PIN set`() {
        assertFalse(NuclearModePin.isSet())
        assertTrue(NuclearModePin.verify("anything"))
    }

    @Test
    fun `NuclearModePin verify rejects unexpected stored format without colon`() {
        // Corrupt the stored value to have no colon — must deny, not pass through
        Database.setSetting("nuclear_mode_pin_hash", "notavalidhash")
        assertFalse(NuclearModePin.verify("anything"))
    }

    // ── NuclearModePin — minimum length ──────────────────────────────────────

    @Test
    fun `NuclearModePin set accepts exactly 4-char PIN`() {
        NuclearModePin.set("ab1!")           // exactly MIN_LENGTH
        assertTrue(NuclearModePin.isSet())
        assertTrue(NuclearModePin.verify("ab1!"))
    }

    @Test
    fun `NuclearModePin set throws for PIN shorter than 4 chars`() {
        var threw = false
        try {
            NuclearModePin.set("abc")        // 3 chars — below minimum
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "Expected IllegalArgumentException for PIN shorter than 4 chars")
        assertFalse(NuclearModePin.isSet())  // no PIN should have been stored
    }

    // ── NuclearModePin — salted storage format ────────────────────────────────

    @Test
    fun `NuclearModePin stored value uses salted format saltHex colon hashHex`() {
        NuclearModePin.set("nuke1234")
        val stored = Database.getSetting("nuclear_mode_pin_hash")!!
        assertTrue(':' in stored, "Expected salted format 'saltHex:hashHex' but got: $stored")
        val parts = stored.split(":")
        assertEquals(2,  parts.size,      "Should be exactly two colon-separated parts")
        assertEquals(32, parts[0].length, "Salt should be 32 hex chars (16 bytes)")
        assertEquals(64, parts[1].length, "Hash should be 64 hex chars (SHA-256)")
    }

    @Test
    fun `NuclearModePin same PIN set twice produces different salts`() {
        NuclearModePin.set("nuke1234")
        val hash1 = Database.getSetting("nuclear_mode_pin_hash")!!
        NuclearModePin.set("nuke1234")
        val hash2 = Database.getSetting("nuclear_mode_pin_hash")!!
        assertNotEquals(hash1, hash2, "Same PIN should yield different stored values due to different salts")
    }

    // ── NuclearModePin — clearWithPin ─────────────────────────────────────────

    @Test
    fun `NuclearModePin clearWithPin with correct PIN removes hash`() {
        NuclearModePin.set("nuke1234")
        assertTrue(NuclearModePin.clearWithPin("nuke1234"))
        assertFalse(NuclearModePin.isSet())
    }

    @Test
    fun `NuclearModePin clearWithPin with wrong PIN returns false and preserves hash`() {
        NuclearModePin.set("nuke1234")
        assertFalse(NuclearModePin.clearWithPin("wrongpin"))
        assertTrue(NuclearModePin.isSet())
    }

    // ── NuclearModePin — clearWithoutPin ──────────────────────────────────────

    @Test
    fun `NuclearModePin clearWithoutPin removes PIN unconditionally`() {
        NuclearModePin.set("nuke1234")
        NuclearModePin.clearWithoutPin()
        assertFalse(NuclearModePin.isSet())
    }

    @Test
    fun `NuclearModePin clearWithoutPin is safe when no PIN is set`() {
        assertFalse(NuclearModePin.isSet())
        NuclearModePin.clearWithoutPin()     // must not throw
        assertFalse(NuclearModePin.isSet())
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Replicates the legacy unsalted SHA-256 hash to set up migration test data. */
    private fun sha256Hex(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
