package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset

class PassesTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val now = 10_000_000L
    private val lockStart = now - 60_000

    private fun state(
        passPackage: String? = null,
        passEndsAt: Long? = null,
        passesUsed: Int = 0,
        passSessionStart: Long? = lockStart,
        startedAt: Long = lockStart,
    ) = LockState(
        isLocked = true,
        activeModeId = DEFAULT_MODE_ID,
        startedAt = startedAt,
        endsAt = null,
        passPackage = passPackage,
        passEndsAt = passEndsAt,
        passesUsed = passesUsed,
        passSessionStart = passSessionStart,
    )

    private fun policy(s: LockState, at: Long = now) =
        activePolicy(at, s, setOf(youtube, instagram), emptySet())

    @Test
    fun `a new lock has all its passes`() {
        val p = policy(state(passSessionStart = null))
        assertEquals(DEFAULT_PASSES_PER_LOCK, p.passesLeft)
        assertTrue(canUsePass(p, youtube, now))
    }

    @Test
    fun `a pass opens only that app until it ends`() {
        val p = policy(state(passPackage = youtube, passEndsAt = now + 1_000, passesUsed = 1))
        assertFalse(p.isBlocked(youtube, now))
        assertTrue(p.isBlocked(instagram, now))
        assertTrue("blocked again once the pass ends", p.isBlocked(youtube, now + 1_000))
    }

    @Test
    fun `passes used are counted`() {
        assertEquals(1, policy(state(passesUsed = 2)).passesLeft)
    }

    @Test
    fun `no pass once they're used up`() {
        val p = policy(state(passesUsed = DEFAULT_PASSES_PER_LOCK))
        assertEquals(0, p.passesLeft)
        assertFalse(canUsePass(p, youtube, now))
    }

    @Test
    fun `no pass for an app that isn't blocked`() {
        assertFalse(canUsePass(policy(state()), "com.spotify.music", now))
    }

    @Test
    fun `passes come back with a new lock`() {
        // Count belongs to an earlier lock; this lock started later.
        val p = policy(state(passesUsed = DEFAULT_PASSES_PER_LOCK, passSessionStart = lockStart - 3_600_000))
        assertEquals(DEFAULT_PASSES_PER_LOCK, p.passesLeft)
    }

    @Test
    fun `a pass from an earlier lock doesn't carry over`() {
        val p = policy(state(passPackage = youtube, passEndsAt = now + 60_000, passSessionStart = lockStart - 1))
        assertTrue(p.isBlocked(youtube, now))
        assertNull(p.passPackage)
    }

    @Test
    fun `pass end is when the policy next changes`() {
        val p = policy(state(passPackage = youtube, passEndsAt = now + 5_000, passesUsed = 1))
        assertEquals(now + 5_000, p.nextChangeAt(now))
    }

    @Test
    fun `passes are shared across a lock and a schedule running together`() {
        val schedule = ScheduleRule(
            id = 1, modeId = DEFAULT_MODE_ID, days = DayOfWeek.entries.toSet(),
            startMinute = 0, endMinute = 23 * 60 + 59,
        )
        // Schedule window started at midnight UTC, before the manual lock.
        val p = activePolicy(
            now, ZoneOffset.UTC, state(passSessionStart = null), listOf(schedule),
            mapOf(DEFAULT_MODE_ID to ModeRule(setOf(youtube))), emptySet(),
        )
        assertEquals("stretch starts at the earliest running lock", 0L, p.lockedSince)
    }

    @Test
    fun `unlocked has no passes`() {
        val p = policy(state().copy(isLocked = false))
        assertEquals(0, p.passesLeft)
        assertFalse(canUsePass(p, youtube, now))
    }

    private fun policyWithCap(s: LockState, cap: Int) =
        activePolicy(now, s, setOf(youtube, instagram), emptySet(), passCap = cap)

    @Test
    fun `her chosen number of passes is used`() {
        val p = policyWithCap(state(passesUsed = 1), cap = 5)
        assertEquals(5, p.passCap)
        assertEquals(1, p.passesUsed)
        assertEquals(4, p.passesLeft)
    }

    @Test
    fun `zero passes means none can be used`() {
        val p = policyWithCap(state(passSessionStart = null), cap = 0)
        assertEquals(0, p.passesLeft)
        assertFalse(canUsePass(p, youtube, now))
    }

    @Test
    fun `lowering below what was already used leaves none, never negative`() {
        val p = policyWithCap(state(passesUsed = 3), cap = 1)
        assertEquals(0, p.passesLeft)
        assertEquals(3, p.passesUsed)
    }

    @Test
    fun `passes per lock can only go down while locked`() {
        val locked = policy(state())
        assertTrue(canSetPassCap(locked, now, current = 3, new = 1))
        assertTrue(canSetPassCap(locked, now, current = 3, new = 3))
        assertFalse(canSetPassCap(locked, now, current = 3, new = 4))
    }

    @Test
    fun `passes per lock can be anything from 0 to 5 while unlocked`() {
        val unlocked = policy(state().copy(isLocked = false))
        assertTrue(canSetPassCap(unlocked, now, current = 1, new = 5))
        assertTrue(canSetPassCap(unlocked, now, current = 3, new = 0))
        assertFalse(canSetPassCap(unlocked, now, current = 3, new = 6))
        assertFalse(canSetPassCap(unlocked, now, current = 3, new = -1))
    }
}
