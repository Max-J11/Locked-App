package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {

    private val youtube = "com.google.android.youtube"
    private val now = 1_000_000L

    private fun locked(endsAt: Long? = null, cooldownEndsAt: Long? = null, source: String? = puckSource(1)) =
        LockState(
            isLocked = true,
            activeModeId = 1L,
            startedAt = now - 60_000,
            endsAt = endsAt,
            cooldownEndsAt = cooldownEndsAt,
            source = source,
        )

    // --- Which puck can unlock ---

    @Test
    fun `only the locking puck can unlock a puck lock`() {
        assertTrue(canUnlockWithPuck(puckSource(3), keyId = 3, keyModeId = 1, activeModeId = 1))
        assertFalse(canUnlockWithPuck(puckSource(3), keyId = 4, keyModeId = 1, activeModeId = 1))
    }

    @Test
    fun `a timed lock can be ended by pucks set to its mode`() {
        assertTrue(canUnlockWithPuck(SOURCE_TIMER, keyId = 4, keyModeId = 2, activeModeId = 2))
    }

    @Test
    fun `a timed lock can't be ended by pucks set to another mode`() {
        assertFalse(canUnlockWithPuck(SOURCE_TIMER, keyId = 4, keyModeId = 1, activeModeId = 2))
    }

    @Test
    fun `an unknown source falls back to the mode rule`() {
        assertTrue(canUnlockWithPuck(null, keyId = 4, keyModeId = 1, activeModeId = 1))
        assertFalse(canUnlockWithPuck("puck:garbage", keyId = 4, keyModeId = 1, activeModeId = 2))
    }

    @Test
    fun `locking puck id is read from the source`() {
        assertEquals(7L, lockingPuckId(puckSource(7)))
        assertNull(lockingPuckId(SOURCE_TIMER))
    }

    // --- Unlock without puck ---

    @Test
    fun `still locked while the cooldown is counting down`() {
        val policy = activePolicy(now, locked(cooldownEndsAt = now + 1), setOf(youtube), emptySet())
        assertTrue(policy.isBlocked(youtube, now))
        assertEquals(now + 1, policy.cooldownEndsAt)
    }

    @Test
    fun `unlocked once the cooldown has finished`() {
        val policy = activePolicy(now, locked(cooldownEndsAt = now), setOf(youtube), emptySet())
        assertFalse(policy.isActive(now))
    }

    @Test
    fun `cooldown ends on the clock even if the policy is stale`() {
        val policy = activePolicy(now, locked(cooldownEndsAt = now + 1_000), setOf(youtube), emptySet())
        assertTrue(policy.isBlocked(youtube, now + 999))
        assertFalse(policy.isBlocked(youtube, now + 1_000))
    }

    @Test
    fun `unlock without puck is offered for an open-ended lock`() {
        assertTrue(canStartCooldown(isActive = true, endsAt = null, cooldownEndsAt = null, now = now))
    }

    @Test
    fun `unlock without puck is offered when waiting beats the timer`() {
        assertTrue(canStartCooldown(true, endsAt = now + COOLDOWN_MILLIS + 1, cooldownEndsAt = null, now = now))
    }

    @Test
    fun `unlock without puck is hidden when the timer ends sooner`() {
        assertFalse(canStartCooldown(true, endsAt = now + COOLDOWN_MILLIS, cooldownEndsAt = null, now = now))
        assertFalse(canStartCooldown(true, endsAt = now + 60_000, cooldownEndsAt = null, now = now))
    }

    @Test
    fun `unlock without puck is hidden when unlocked or already counting down`() {
        assertFalse(canStartCooldown(false, endsAt = null, cooldownEndsAt = null, now = now))
        assertFalse(canStartCooldown(true, endsAt = null, cooldownEndsAt = now + 1_000, now = now))
    }

    // --- When the policy next changes on its own ---

    @Test
    fun `no scheduled change for an open-ended lock`() {
        assertNull(activePolicy(now, locked(), setOf(youtube), emptySet()).nextChangeAt(now))
    }

    @Test
    fun `next change is the earlier of timer and cooldown`() {
        val policy = activePolicy(now, locked(endsAt = now + 5_000, cooldownEndsAt = now + 2_000), emptySet(), emptySet())
        assertEquals(now + 2_000, policy.nextChangeAt(now))
    }

    @Test
    fun `next change is the timer end for a timed lock`() {
        val policy = activePolicy(now, locked(endsAt = now + 5_000, source = SOURCE_TIMER), emptySet(), emptySet())
        assertEquals(now + 5_000, policy.nextChangeAt(now))
    }

    @Test
    fun `no scheduled change once unlocked`() {
        assertNull(com.max.locked.data.Policy.UNLOCKED.nextChangeAt(now))
    }
}
