package com.focusflow

import com.focusflow.data.Database
import com.focusflow.data.models.DailyAllowance
import com.focusflow.data.upsertDailyAllowance
import com.focusflow.data.upsertDailyUsage
import com.focusflow.services.DailyAllowanceTracker
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for DailyAllowanceTracker.
 *
 * Each test:
 *  1. Calls Database.initInMemory() for an isolated schema.
 *  2. Calls DailyAllowanceTracker.clearStateForTesting() to wipe in-memory state.
 *  3. Calls start() + stop() + Thread.sleep(150) so the coroutine can finish
 *     initialisation before we make assertions.
 */
class DailyAllowanceTest {

    private val chrome  = DailyAllowance("chrome.exe",  "Google Chrome", 60)
    private val discord = DailyAllowance("discord.exe", "Discord",       30)

    @BeforeEach
    fun setUp() {
        DailyAllowanceTracker.clearStateForTesting()
        Database.initInMemory()
    }

    @AfterEach
    fun tearDown() {
        DailyAllowanceTracker.clearStateForTesting()
    }

    // ── Remaining / usage with zero recorded usage ────────────────────────────

    @Test
    fun `getRemainingMinutes returns full allowance when no usage recorded`() {
        Database.upsertDailyAllowance(chrome)
        startAndStop()
        assertEquals(60L, DailyAllowanceTracker.getRemainingMinutes(chrome))
    }

    @Test
    fun `getUsageMinutes returns 0 when no usage recorded`() {
        Database.upsertDailyAllowance(chrome)
        startAndStop()
        assertEquals(0L, DailyAllowanceTracker.getUsageMinutes("chrome.exe"))
    }

    // ── Usage loaded from DB ──────────────────────────────────────────────────

    @Test
    fun `getUsageMinutes returns persisted usage loaded from DB on start`() {
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe", 1800L) // 30 min
        startAndStop()
        assertEquals(30L, DailyAllowanceTracker.getUsageMinutes("chrome.exe"))
    }

    @Test
    fun `getRemainingMinutes decrements correctly given partial usage`() {
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe", 1800L) // 30 min used
        startAndStop()
        assertEquals(30L, DailyAllowanceTracker.getRemainingMinutes(chrome)) // 60 - 30
    }

    @Test
    fun `getRemainingMinutes returns 0 when usage exactly equals allowance`() {
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe", 3600L) // exactly 60 min
        startAndStop()
        assertEquals(0L, DailyAllowanceTracker.getRemainingMinutes(chrome))
    }

    @Test
    fun `getRemainingMinutes clamps to 0 when usage exceeds allowance`() {
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe", 5400L) // 90 min (over 60-min limit)
        startAndStop()
        assertEquals(0L, DailyAllowanceTracker.getRemainingMinutes(chrome))
    }

    // ── blockedProcesses population on start ─────────────────────────────────

    @Test
    fun `start marks app as blocked when usage meets allowance limit`() {
        Database.upsertDailyAllowance(discord)
        Database.upsertDailyUsage(LocalDate.now(), "discord.exe", 1800L) // exactly 30 min
        startAndStop()
        assertTrue(
            DailyAllowanceTracker.blockedProcesses.contains("discord.exe"),
            "discord.exe should be blocked after using its full allowance"
        )
    }

    @Test
    fun `start does NOT mark app as blocked when usage is under allowance`() {
        Database.upsertDailyAllowance(discord)
        Database.upsertDailyUsage(LocalDate.now(), "discord.exe", 900L) // 15 min (under 30-min limit)
        startAndStop()
        assertFalse(
            DailyAllowanceTracker.blockedProcesses.contains("discord.exe"),
            "discord.exe should NOT be blocked with half its allowance used"
        )
    }

    @Test
    fun `start does NOT block app with zero recorded usage`() {
        Database.upsertDailyAllowance(chrome)
        // No usage row inserted
        startAndStop()
        assertFalse(DailyAllowanceTracker.blockedProcesses.contains("chrome.exe"))
    }

    // ── State isolation — clearStateForTesting prevents bleed between tests ───

    @Test
    fun `state is clean after clearStateForTesting even if previous test blocked an app`() {
        // Simulate a previous test leaving state
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe", 3600L) // full allowance
        startAndStop()
        assertTrue(DailyAllowanceTracker.blockedProcesses.contains("chrome.exe"))

        // Simulate next test setup
        DailyAllowanceTracker.clearStateForTesting()
        Database.initInMemory()
        Database.upsertDailyAllowance(discord)
        // No usage → discord should NOT be blocked
        startAndStop()
        assertFalse(DailyAllowanceTracker.blockedProcesses.contains("chrome.exe"),
            "chrome.exe block from previous test must not bleed into this test")
        assertFalse(DailyAllowanceTracker.blockedProcesses.contains("discord.exe"),
            "discord.exe should not be blocked with zero usage")
    }

    // ── getUsageSummary ───────────────────────────────────────────────────────

    @Test
    fun `getUsageSummary returns correct usage minutes for all allowances`() {
        Database.upsertDailyAllowance(chrome)
        Database.upsertDailyAllowance(discord)
        Database.upsertDailyUsage(LocalDate.now(), "chrome.exe",  3000L) // 50 min
        Database.upsertDailyUsage(LocalDate.now(), "discord.exe",  600L) // 10 min
        startAndStop()

        val summary      = DailyAllowanceTracker.getUsageSummary()
        val chromeEntry  = summary.first { it.first.processName == "chrome.exe"  }
        val discordEntry = summary.first { it.first.processName == "discord.exe" }
        assertEquals(50L, chromeEntry.second,  "Chrome usage should be 50 minutes")
        assertEquals(10L, discordEntry.second, "Discord usage should be 10 minutes")
    }

    @Test
    fun `getUsageSummary is empty when no allowances configured`() {
        startAndStop()
        assertTrue(DailyAllowanceTracker.getUsageSummary().isEmpty())
    }

    // ── reload ────────────────────────────────────────────────────────────────

    @Test
    fun `reload picks up newly added allowance from DB`() {
        startAndStop()
        // Add allowance after start()
        Database.upsertDailyAllowance(chrome)
        DailyAllowanceTracker.reload()
        val summary = DailyAllowanceTracker.getUsageSummary()
        assertTrue(summary.any { it.first.processName == "chrome.exe" },
            "Newly added allowance should appear after reload()")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * start() + stop() + small sleep to let the coroutine initialise.
     * The background tick loop calls ProcessHandle.allProcesses() which we don't
     * want running during tests — stop() cancels the job immediately.
     */
    private fun startAndStop() {
        DailyAllowanceTracker.start()
        DailyAllowanceTracker.stop()
        Thread.sleep(100)
    }
}
