package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId

class SchedulePolicyTest {

    private val zone = ZoneId.of("America/Chicago")
    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val dialer = "com.samsung.android.dialer"

    private val workMode = 2L
    private val sleepMode = 3L
    private val modes = mapOf(
        DEFAULT_MODE_ID to ModeRule(setOf(youtube)),
        workMode to ModeRule(setOf(instagram), blockNotifications = true),
        sleepMode to ModeRule(setOf(youtube, dialer)),
    )

    // 2026-10-26 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun schedule(
        days: Set<DayOfWeek>,
        start: Int,
        end: Int,
        modeId: Long = workMode,
        id: Long = 1,
        enabled: Boolean = true,
        skippedUntil: Long? = null,
    ) = ScheduleRule(id, modeId, days, start * 60, end * 60, enabled, skippedUntil)

    private fun policy(now: Long, vararg schedules: ScheduleRule, state: LockState? = null) =
        activePolicy(now, zone, state, schedules.toList(), modes, setOf(dialer))

    // --- Windows ---

    @Test
    fun `blocks during a daytime window on a chosen day`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertTrue(policy(at(26, 10), work).isBlocked(instagram, at(26, 10)))
    }

    @Test
    fun `window start is inclusive and end is exclusive`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertTrue(policy(at(26, 9), work).isActive(at(26, 9)))
        assertFalse(policy(at(26, 17), work).isActive(at(26, 17)))
    }

    @Test
    fun `nothing outside the window or on other days`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertFalse(policy(at(26, 8), work).isActive(at(26, 8)))
        assertFalse(policy(at(27, 10), work).isActive(at(27, 10)))
    }

    @Test
    fun `overnight window runs into the next morning`() {
        val sleep = schedule(setOf(MONDAY), 22, 7, modeId = sleepMode)
        assertTrue(policy(at(26, 23), sleep).isActive(at(26, 23)))
        assertTrue(policy(at(27, 3), sleep).isActive(at(27, 3)))
        assertFalse(policy(at(27, 7), sleep).isActive(at(27, 7)))
    }

    @Test
    fun `overnight window belongs to the day it starts`() {
        // Starts Monday night only, so early Monday morning (Sunday night's window) is free.
        val sleep = schedule(setOf(MONDAY), 22, 7, modeId = sleepMode)
        assertFalse(policy(at(26, 3), sleep).isActive(at(26, 3)))
        val withSunday = sleep.copy(days = setOf(SUNDAY, MONDAY))
        assertTrue(policy(at(26, 3), withSunday).isActive(at(26, 3)))
    }

    @Test
    fun `switched-off schedule does nothing`() {
        val work = schedule(setOf(MONDAY), 9, 17, enabled = false)
        assertFalse(policy(at(26, 10), work).isActive(at(26, 10)))
    }

    @Test
    fun `never-block apps stay open during a schedule`() {
        val sleep = schedule(setOf(MONDAY), 22, 7, modeId = sleepMode)
        val p = policy(at(26, 23), sleep)
        assertTrue(p.isBlocked(youtube, at(26, 23)))
        assertFalse(p.isBlocked(dialer, at(26, 23)))
    }

    // --- Combining ---

    @Test
    fun `overlapping schedules combine their apps`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        val sleep = schedule(setOf(MONDAY), 12, 13, modeId = sleepMode, id = 2)
        val p = policy(at(26, 12, 30), work, sleep)
        assertEquals(setOf(instagram, youtube), p.blockedPackages)
        assertEquals(setOf(workMode, sleepMode), p.activeModeIds)
    }

    @Test
    fun `manual lock and schedule combine`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        val manual = LockState(true, DEFAULT_MODE_ID, at(26, 8), null, source = puckSource(1))
        val p = policy(at(26, 10), work, state = manual)
        assertEquals(setOf(youtube, instagram), p.blockedPackages)
        assertNull("open-ended manual lock means no end time", p.endsAt)
    }

    @Test
    fun `notifications are hidden only for modes that ask for it`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        val sleep = schedule(setOf(MONDAY), 9, 17, modeId = sleepMode, id = 2)
        val p = policy(at(26, 10), work, sleep)
        assertEquals(setOf(instagram), p.notificationPackages)
        assertTrue(shouldDismissNotification(p, instagram, isOngoing = false, now = at(26, 10)))
        assertFalse(shouldDismissNotification(p, youtube, isOngoing = false, now = at(26, 10)))
    }

    @Test
    fun `end time is the latest end of everything running`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        val lunch = schedule(setOf(MONDAY), 12, 13, modeId = sleepMode, id = 2)
        assertEquals(at(26, 17), policy(at(26, 12, 30), work, lunch).endsAt)
    }

    // --- Ending early ---

    @Test
    fun `a puck-ended window stays ended until its normal end`() {
        val work = schedule(setOf(MONDAY, TUESDAY), 9, 17, skippedUntil = at(26, 17))
        assertFalse(policy(at(26, 10), work).isActive(at(26, 10)))
        // Next day's window runs as normal.
        assertTrue(policy(at(27, 10), work).isActive(at(27, 10)))
    }

    @Test
    fun `finished unlock-without-puck ends windows that were running when it started`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        val cooldownEnd = at(26, 10, 15)
        val state = LockState(false, null, null, null, cooldownEndsAt = cooldownEnd)
        assertTrue("still waiting", policy(at(26, 10, 10), work, state = state).isActive(at(26, 10, 10)))
        assertFalse("wait finished", policy(at(26, 10, 20), work, state = state).isActive(at(26, 10, 20)))
    }

    @Test
    fun `unlock-without-puck doesn't end a window that started during the wait`() {
        val work = schedule(setOf(MONDAY), 10, 17) // starts at 10:00
        val state = LockState(false, null, null, null, cooldownEndsAt = at(26, 10, 5)) // wait began 9:50
        assertTrue(policy(at(26, 10, 30), work, state = state).isActive(at(26, 10, 30)))
    }

    @Test
    fun `a finished wait doesn't end a lock started after it`() {
        val state = LockState(true, DEFAULT_MODE_ID, at(26, 11), null, cooldownEndsAt = at(26, 10))
        assertTrue(policy(at(26, 12), state = state).isActive(at(26, 12)))
    }

    // --- When to recompute ---

    @Test
    fun `next change is the next window start while unlocked`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertEquals(at(26, 9), policy(at(26, 7), work).nextChangeAt(at(26, 7)))
    }

    @Test
    fun `next change is the window end while it runs`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertEquals(at(26, 17), policy(at(26, 10), work).nextChangeAt(at(26, 10)))
    }

    @Test
    fun `next change looks ahead to next week`() {
        val work = schedule(setOf(MONDAY), 9, 17)
        assertEquals(at(26, 9).plusDays(7), policy(at(26, 18), work).nextChangeAt(at(26, 18)))
    }

    // --- Daylight saving ---

    @Test
    fun `window follows the wall clock across a DST change`() {
        // US clocks fall back on Sunday 2026-11-01. A 9-to-5 window that Sunday is 9 hours long.
        val sunday = schedule(setOf(SUNDAY), 9, 17)
        val nine = LocalDateTime.of(2026, 11, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(policy(nine, sunday).isActive(nine))
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 17, 0).atZone(zone).toInstant().toEpochMilli(),
            policy(nine, sunday).endsAt,
        )
    }

    private fun Long.plusDays(days: Int): Long =
        java.time.Instant.ofEpochMilli(this).atZone(zone).plusDays(days.toLong()).toInstant().toEpochMilli()
}
