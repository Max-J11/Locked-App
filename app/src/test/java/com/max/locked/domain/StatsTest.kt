package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 30)
    private val midnight = startOfDay(day, zone)
    private fun at(hour: Int, minute: Int = 0) = midnight + hour * HOUR + minute * MINUTE

    @Test
    fun `a finished span counts its length`() {
        val spans = listOf(LockedSpan(at(9), at(11)))
        assertEquals(2 * HOUR, lockedMillisBetween(spans, midnight, midnight + DAY, now = at(20)))
    }

    @Test
    fun `an open span counts up to now`() {
        val spans = listOf(LockedSpan(at(9), null))
        assertEquals(90 * MINUTE, lockedMillisBetween(spans, midnight, midnight + DAY, now = at(10, 30)))
    }

    @Test
    fun `overlapping spans are not counted twice`() {
        val spans = listOf(LockedSpan(at(9), at(11)), LockedSpan(at(10), at(12)))
        assertEquals(3 * HOUR, lockedMillisBetween(spans, midnight, midnight + DAY, now = at(20)))
    }

    @Test
    fun `a span crossing midnight is split between the two days`() {
        val spans = listOf(LockedSpan(at(22), at(22) + 4 * HOUR)) // 10 PM to 2 AM
        val perDay = lockedMillisPerDay(spans, listOf(day, day.plusDays(1)), zone, now = at(22) + DAY)
        assertEquals(listOf(2 * HOUR, 2 * HOUR), perDay)
    }

    @Test
    fun `days with no locks are zero`() {
        val spans = listOf(LockedSpan(at(9), at(10)))
        val perDay = lockedMillisPerDay(spans, lastDays(day, 3), zone, now = at(20))
        assertEquals(listOf(0L, 0L, HOUR), perDay)
    }

    @Test
    fun `last days end with today, oldest first`() {
        assertEquals(listOf(day.minusDays(2), day.minusDays(1), day), lastDays(day, 3))
    }

    @Test
    fun `a span ending before it starts counts as nothing`() {
        val spans = listOf(LockedSpan(at(10), at(9)))
        assertEquals(0L, lockedMillisBetween(spans, midnight, midnight + DAY, now = at(20)))
    }

    @Test
    fun `locks are counted by when they started`() {
        val spans = listOf(LockedSpan(midnight - HOUR, at(1)), LockedSpan(at(9), at(10)))
        assertEquals(1, locksStartedBetween(spans, midnight, midnight + DAY))
    }

    @Test
    fun `hours and minutes round down`() {
        assertEquals(4L to 10L, hoursAndMinutes(4 * HOUR + 10 * MINUTE + 59_000))
        assertEquals(0L to 0L, hoursAndMinutes(-5))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
