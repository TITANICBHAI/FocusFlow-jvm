package com.focusflow

import com.focusflow.data.models.BlockSchedule
import com.focusflow.services.isScheduleActive
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the top-level isScheduleActive() function extracted from
 * BlockScheduleService.  No database or coroutines involved — pure logic.
 *
 * Day-of-week convention matches Java ISO-8601 via LocalDateTime.dayOfWeek.value:
 *   1 = Monday … 7 = Sunday
 */
class ScheduleEvaluatorTest {

    private fun schedule(
        days:    List<Int>,
        startH:  Int, startM: Int,
        endH:    Int, endM:   Int,
        enabled: Boolean = true
    ) = BlockSchedule(
        id          = "test",
        name        = "Test Schedule",
        daysOfWeek  = days,
        startHour   = startH, startMinute = startM,
        endHour     = endH,   endMinute   = endM,
        enabled     = enabled
    )

    // ── Disabled ───────────────────────────────────────────────────────────────

    @Test
    fun `disabled schedule is never active`() {
        val s = schedule(listOf(1,2,3,4,5), 9,0, 17,0, enabled = false)
        // Monday 10:00 — clearly inside the window, but schedule is disabled
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T10:00")))
    }

    // ── Normal (non-overnight) windows ────────────────────────────────────────

    @Test
    fun `time inside window on matching day is active`() {
        val s = schedule(listOf(1), 9,0, 17,0)
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-15T10:30"))) // Mon 10:30
    }

    @Test
    fun `time before start on matching day is not active`() {
        val s = schedule(listOf(1), 9,0, 17,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T08:59"))) // Mon 08:59
    }

    @Test
    fun `start time is inclusive`() {
        val s = schedule(listOf(1), 9,0, 17,0)
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-15T09:00")))
    }

    @Test
    fun `end time is exclusive`() {
        val s = schedule(listOf(1), 9,0, 17,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T17:00")))
    }

    @Test
    fun `wrong day of week is not active`() {
        val s = schedule(listOf(2), 9,0, 17,0) // Tuesday only
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T10:00"))) // Monday
    }

    @Test
    fun `multi-day schedule matches any listed day`() {
        val s = schedule(listOf(1,3,5), 9,0, 17,0) // Mon, Wed, Fri
        assertTrue(isScheduleActive(s,  LocalDateTime.parse("2024-01-15T10:00"))) // Mon ✓
        assertTrue(isScheduleActive(s,  LocalDateTime.parse("2024-01-17T10:00"))) // Wed ✓
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-16T10:00"))) // Tue ✗
    }

    @Test
    fun `time after end on matching day is not active`() {
        val s = schedule(listOf(1), 9,0, 17,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T17:30")))
    }

    // ── Overnight windows ─────────────────────────────────────────────────────

    @Test
    fun `overnight head — on named day after start is active`() {
        // Monday 22:00 – 02:00 Tuesday
        val s = schedule(listOf(1), 22,0, 2,0)
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-15T23:00"))) // Mon 23:00
    }

    @Test
    fun `overnight tail — following day before end is active`() {
        // Monday 22:00 – 02:00; at Tuesday 01:30 prevDay=Monday is in schedule
        val s = schedule(listOf(1), 22,0, 2,0)
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-16T01:30"))) // Tue 01:30
    }

    @Test
    fun `overnight tail end is exclusive`() {
        val s = schedule(listOf(1), 22,0, 2,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-16T02:00"))) // exactly 02:00
    }

    @Test
    fun `overnight gap between end and next head is not active`() {
        val s = schedule(listOf(1), 22,0, 2,0) // Monday 22:00 – Tuesday 02:00
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-16T10:00"))) // Tue 10:00
    }

    @Test
    fun `overnight before start on named day is not active`() {
        val s = schedule(listOf(1), 22,0, 2,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T21:59"))) // Mon 21:59
    }

    @Test
    fun `overnight Sunday to Monday — day rollover from 7 to 1`() {
        // Sunday (7) 23:00 – Monday (1) 01:00
        val s = schedule(listOf(7), 23,0, 1,0)
        // Monday 00:30: prevDay = 7 (Sunday) which IS in the schedule
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-15T00:30"))) // Mon 00:30
    }

    @Test
    fun `overnight Sunday to Monday — on Sunday after start is active`() {
        val s = schedule(listOf(7), 23,0, 1,0)
        assertTrue(isScheduleActive(s, LocalDateTime.parse("2024-01-14T23:30"))) // Sun 23:30
    }

    @Test
    fun `overnight Sunday to Monday — on Monday at or after end is not active`() {
        val s = schedule(listOf(7), 23,0, 1,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T01:00"))) // Mon 01:00
    }

    // ── Edge: 24-hour (midnight to midnight) ─────────────────────────────────

    @Test
    fun `00 00 to 00 00 is treated as zero-width window`() {
        // start == end → isOvernight is false (no strict >). inWindow = currentTime >= 00:00
        // && currentTime < 00:00, which is always false.
        val s = schedule(listOf(1), 0,0, 0,0)
        assertFalse(isScheduleActive(s, LocalDateTime.parse("2024-01-15T12:00")))
    }
}
