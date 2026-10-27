package com.max.locked.domain

import com.max.locked.data.ManualLock
import com.max.locked.data.Policy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldDismissNotificationTest {

    private val instagram = "com.instagram.android"
    private val spotify = "com.spotify.music"
    private val now = 1_000_000L

    private fun policy(
        locked: Boolean = true,
        blockNotifications: Boolean = true,
        endsAt: Long? = null,
    ) = Policy(
        manual = if (locked) ManualLock(modeId = 1L, startedAt = now - 1_000, endsAt = endsAt, source = null) else null,
        blockedPackages = setOf(instagram),
        notificationPackages = if (blockNotifications) setOf(instagram) else emptySet(),
    )

    @Test
    fun `blocked app is dismissed`() {
        assertTrue(shouldDismissNotification(policy(), instagram, isOngoing = false, now = now))
    }

    @Test
    fun `app that isn't blocked is kept`() {
        assertFalse(shouldDismissNotification(policy(), spotify, isOngoing = false, now = now))
    }

    @Test
    fun `ongoing notification is kept`() {
        assertFalse(shouldDismissNotification(policy(), instagram, isOngoing = true, now = now))
    }

    @Test
    fun `unlocked keeps everything`() {
        assertFalse(shouldDismissNotification(Policy.UNLOCKED, instagram, isOngoing = false, now = now))
        assertFalse(shouldDismissNotification(policy(locked = false), instagram, isOngoing = false, now = now))
    }

    @Test
    fun `mode with notification blocking off keeps everything`() {
        assertFalse(shouldDismissNotification(policy(blockNotifications = false), instagram, false, now))
    }

    @Test
    fun `timed lock past its end keeps everything`() {
        assertFalse(shouldDismissNotification(policy(endsAt = now), instagram, isOngoing = false, now = now))
    }
}
