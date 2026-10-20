package com.max.locked.domain

import com.max.locked.data.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePolicyTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val dialer = "com.samsung.android.dialer"
    private val now = 1_000_000L

    private fun locked(endsAt: Long? = null) = LockState(
        isLocked = true,
        activeModeId = 1L,
        startedAt = now - 60_000,
        endsAt = endsAt,
    )

    @Test
    fun `no stored state blocks nothing`() {
        assertEquals(Policy.UNLOCKED, activePolicy(now, null, setOf(youtube), emptySet()))
    }

    @Test
    fun `unlocked blocks nothing`() {
        val state = locked().copy(isLocked = false)
        val policy = activePolicy(now, state, setOf(youtube), emptySet())
        assertFalse(policy.isBlocked(youtube, now))
    }

    @Test
    fun `locked blocks the mode's apps only`() {
        val policy = activePolicy(now, locked(), setOf(youtube, instagram), emptySet())
        assertTrue(policy.isBlocked(youtube, now))
        assertTrue(policy.isBlocked(instagram, now))
        assertFalse(policy.isBlocked("com.spotify.music", now))
    }

    @Test
    fun `never-block apps are never blocked even if in the mode`() {
        val policy = activePolicy(now, locked(), setOf(youtube, dialer), setOf(dialer))
        assertTrue(policy.isBlocked(youtube, now))
        assertFalse(policy.isBlocked(dialer, now))
    }

    @Test
    fun `locked with an empty mode blocks nothing`() {
        val policy = activePolicy(now, locked(), emptySet(), emptySet())
        assertTrue(policy.isLocked)
        assertFalse(policy.isBlocked(youtube, now))
    }

    @Test
    fun `notification blocking carries through only while locked`() {
        val locked = activePolicy(now, locked(), setOf(youtube), emptySet(), modeBlocksNotifications = true)
        assertTrue(locked.blockNotifications)

        val unlocked = activePolicy(
            now, locked().copy(isLocked = false), setOf(youtube), emptySet(), modeBlocksNotifications = true,
        )
        assertFalse(unlocked.blockNotifications)
    }

    @Test
    fun `notification blocking is off when the mode has it off`() {
        val policy = activePolicy(now, locked(), setOf(youtube), emptySet(), modeBlocksNotifications = false)
        assertFalse(policy.blockNotifications)
    }

    @Test
    fun `timed lock that already ended is unlocked`() {
        assertEquals(Policy.UNLOCKED, activePolicy(now, locked(endsAt = now), setOf(youtube), emptySet()))
    }

    @Test
    fun `timed lock stops blocking once the clock passes endsAt`() {
        val policy = activePolicy(now, locked(endsAt = now + 1_000), setOf(youtube), emptySet())
        assertTrue(policy.isBlocked(youtube, now))
        assertFalse(policy.isBlocked(youtube, now + 1_000))
    }
}
