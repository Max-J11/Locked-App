package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class SitePassesTest {

    private val youtube = "com.google.android.youtube"
    private val now = 10_000_000L
    private val lockStart = now - 60_000
    private val sites = setOf("youtube.com", "reddit.com")

    private fun state(passTarget: String? = null, passEndsAt: Long? = null, passesUsed: Int = 0) = LockState(
        isLocked = true,
        activeModeId = DEFAULT_MODE_ID,
        startedAt = lockStart,
        endsAt = null,
        passPackage = passTarget,
        passEndsAt = passEndsAt,
        passesUsed = passesUsed,
        passSessionStart = lockStart,
    )

    private fun policy(s: LockState) = activePolicy(
        now, ZoneOffset.UTC, s, emptyList(),
        mapOf(DEFAULT_MODE_ID to ModeRule(apps = setOf(youtube), sites = sites)), emptySet(),
    )

    @Test
    fun `a site pass opens that site and its subdomains until it ends`() {
        val p = policy(state(sitePassTarget("youtube.com"), passEndsAt = now + 1_000, passesUsed = 1))
        assertFalse(p.isSiteBlocked("youtube.com", now))
        assertFalse(p.isSiteBlocked("m.youtube.com", now))
        assertTrue("other sites stay blocked", p.isSiteBlocked("reddit.com", now))
        assertTrue("blocked again once it ends", p.isSiteBlocked("m.youtube.com", now + 1_000))
    }

    @Test
    fun `a site pass doesn't open the app, and an app pass doesn't open the site`() {
        val sitePass = policy(state(sitePassTarget("youtube.com"), passEndsAt = now + 1_000, passesUsed = 1))
        assertTrue(sitePass.isBlocked(youtube, now))

        val appPass = policy(state(youtube, passEndsAt = now + 1_000, passesUsed = 1))
        assertTrue(appPass.isSiteBlocked("youtube.com", now))
    }

    @Test
    fun `site passes share the lock's passes with apps`() {
        val p = policy(state(passesUsed = DEFAULT_PASSES_PER_LOCK))
        assertFalse(canUseSitePass(p, "youtube.com", now))
        assertTrue(canUseSitePass(policy(state(passesUsed = 2)), "youtube.com", now))
    }

    @Test
    fun `no site pass for a site that isn't blocked`() {
        assertFalse(canUseSitePass(policy(state()), "wikipedia.org", now))
    }

    @Test
    fun `the pass covers the broadest blocked entry`() {
        assertEquals("youtube.com", blockingSite("m.youtube.com", setOf("m.youtube.com", "youtube.com")))
        assertNull(blockingSite("notyoutube.com", setOf("youtube.com")))
    }

    @Test
    fun `site pass targets can't be mistaken for packages`() {
        assertEquals("youtube.com", sitePassSite(sitePassTarget("youtube.com")))
        assertNull(sitePassSite(youtube))
        assertNull(sitePassSite(null))
    }
}
