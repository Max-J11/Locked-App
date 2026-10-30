package com.max.locked.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset

class StrictnessTest {

    private val work = 2L
    private val sleep = 3L
    private val now = 10_000_000L

    private val unlocked = activePolicy(now, null, emptySet(), emptySet())

    private fun lockedIn(modeId: Long, endsAt: Long? = null, cooldownEndsAt: Long? = null) = activePolicy(
        now, ZoneOffset.UTC,
        LockState(
            isLocked = true, activeModeId = modeId, startedAt = now - 1_000, endsAt = endsAt,
            cooldownEndsAt = cooldownEndsAt, source = "puck:1",
        ),
        emptyList(), mapOf(modeId to ModeRule(setOf("app"))), emptySet(),
    )

    /** Schedule 7 in [sleep] mode, running all day. */
    private val scheduleRunning = activePolicy(
        now, ZoneOffset.UTC, null,
        listOf(ScheduleRule(7, sleep, DayOfWeek.entries.toSet(), 0, 23 * 60 + 59)),
        mapOf(sleep to ModeRule(setOf("app"))), emptySet(),
    )

    @Test
    fun `everything can change while unlocked`() {
        assertTrue(canRemoveApp(unlocked, work, now))
        assertTrue(canStopHidingNotifications(unlocked, work, now))
        assertTrue(canDeleteMode(unlocked, work, now))
        assertTrue(canChangePucks(unlocked, now))
        assertTrue(canChangeSchedule(unlocked, 7, now))
    }

    @Test
    fun `the mode in use can't lose apps or stop hiding notifications`() {
        val p = lockedIn(work)
        assertFalse(canRemoveApp(p, work, now))
        assertFalse(canStopHidingNotifications(p, work, now))
    }

    @Test
    fun `other modes can still be loosened`() {
        val p = lockedIn(work)
        assertTrue(canRemoveApp(p, sleep, now))
        assertTrue(canStopHidingNotifications(p, sleep, now))
    }

    @Test
    fun `no mode can be deleted while locked`() {
        // Pucks on a deleted mode move to Default, which could let them end a Default lock.
        val p = lockedIn(DEFAULT_MODE_ID)
        assertFalse(canDeleteMode(p, work, now))
        assertFalse(canDeleteMode(scheduleRunning, work, now))
    }

    @Test
    fun `Default is never deleted`() {
        assertFalse(canDeleteMode(unlocked, DEFAULT_MODE_ID, now))
    }

    @Test
    fun `pucks are frozen while locked, by a puck or a schedule`() {
        assertFalse(canChangePucks(lockedIn(work), now))
        assertFalse(canChangePucks(scheduleRunning, now))
    }

    @Test
    fun `a running schedule is frozen, others aren't`() {
        assertFalse(canChangeSchedule(scheduleRunning, 7, now))
        assertTrue(canChangeSchedule(scheduleRunning, 8, now))
        assertTrue(canChangeSchedule(lockedIn(work), 7, now))
    }

    @Test
    fun `strict mode can only be turned off while unlocked`() {
        assertTrue(canTurnOffStrictMode(unlocked, now))
        assertFalse(canTurnOffStrictMode(lockedIn(work), now))
        assertFalse(canTurnOffStrictMode(scheduleRunning, now))
    }

    @Test
    fun `a running schedule's mode is in use`() {
        assertFalse(canRemoveApp(scheduleRunning, sleep, now))
    }

    @Test
    fun `everything loosens again once a timer ends`() {
        val p = lockedIn(work, endsAt = now + 1_000)
        assertFalse(canRemoveApp(p, work, now))
        assertTrue(canRemoveApp(p, work, now + 1_000))
        assertTrue(canChangePucks(p, now + 1_000))
    }

    @Test
    fun `everything loosens again once a wait finishes`() {
        val p = lockedIn(work, cooldownEndsAt = now + 1_000)
        assertFalse(canChangePucks(p, now))
        assertTrue(canChangePucks(p, now + 1_000))
    }
}
