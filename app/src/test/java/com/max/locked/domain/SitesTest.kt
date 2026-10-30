package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitesTest {

    @Test
    fun `address bar text as Chrome and Brave show it`() {
        assertEquals("m.youtube.com", hostOf("m.youtube.com/watch?v=EALvDsom2Oc&list=RD"))
        assertEquals("reddit.com", hostOf("reddit.com/?solution=66d2&js_challenge=1"))
        assertEquals("youtube.com", hostOf("youtube.com"))
        assertEquals("app.honeydew.com", hostOf("app.honeydew.com"))
    }

    @Test
    fun `placeholders and searches aren't addresses`() {
        assertNull(hostOf("Search Google or type URL"))
        assertNull(hostOf("Search Brave or type URL"))
        assertNull(hostOf("youtube"))
        assertNull(hostOf(""))
    }

    @Test
    fun `typed sites are cleaned up`() {
        assertEquals("youtube.com", normalizeSite("https://www.YouTube.com/watch?v=1"))
        assertEquals("youtube.com", normalizeSite("  YOUTUBE.COM/ "))
        assertEquals("reddit.com", normalizeSite("http://reddit.com:443/r/all"))
        assertNull(normalizeSite("not a site"))
        assertNull(normalizeSite("reddit"))
    }

    @Test
    fun `a site covers its subdomains`() {
        val sites = setOf("youtube.com")
        assertTrue(isSiteBlocked("youtube.com", sites))
        assertTrue(isSiteBlocked("m.youtube.com", sites))
        assertTrue(isSiteBlocked("music.youtube.com", sites))
    }

    private val now = 10_000_000L

    private fun lockedIn(modeId: Long?, endsAt: Long? = null) = activePolicy(
        now, java.time.ZoneOffset.UTC,
        modeId?.let { LockState(isLocked = true, activeModeId = it, startedAt = now - 1, endsAt = endsAt) },
        emptyList(),
        mapOf(
            2L to ModeRule(setOf("app"), sites = setOf("youtube.com")),
            3L to ModeRule(emptySet(), sites = setOf("reddit.com")),
        ),
        emptySet(),
    )

    @Test
    fun `only the active mode's sites are blocked`() {
        val p = lockedIn(2L)
        assertTrue(p.isSiteBlocked("m.youtube.com", now))
        assertFalse(p.isSiteBlocked("reddit.com", now))
    }

    @Test
    fun `no sites are blocked while unlocked or after the timer`() {
        assertFalse(lockedIn(null).isSiteBlocked("youtube.com", now))
        assertFalse(lockedIn(2L, endsAt = now + 1).isSiteBlocked("youtube.com", now + 1))
    }

    @Test
    fun `lookalike sites aren't covered`() {
        val sites = setOf("youtube.com")
        assertFalse(isSiteBlocked("notyoutube.com", sites))
        assertFalse(isSiteBlocked("youtube.com.evil.net", sites))
        assertFalse(isSiteBlocked("google.com", sites))
    }
}
