package com.max.locked.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A repeating lock window. [days] are the days a window STARTS on. If [endMinute] is at or before
 * [startMinute], the window runs past midnight and ends the next day.
 */
data class ScheduleRule(
    val id: Long,
    val modeId: Long,
    val days: Set<DayOfWeek>,
    /** Minutes after midnight, 0..1439. */
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    /** A puck ended the window that ends at this time ("ended today's session"). */
    val skippedUntil: Long? = null,
)

/** One concrete run of a schedule: [start] inclusive, [end] exclusive. */
data class Occurrence(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t >= start && t < end
}

/** The window that starts on [date], if the schedule runs that day. */
fun ScheduleRule.occurrenceOn(date: LocalDate, zone: ZoneId): Occurrence? {
    if (date.dayOfWeek !in days) return null
    val start = date.atTime(minuteToTime(startMinute)).atZone(zone).toInstant().toEpochMilli()
    val endDate = if (endMinute > startMinute) date else date.plusDays(1)
    val end = endDate.atTime(minuteToTime(endMinute)).atZone(zone).toInstant().toEpochMilli()
    return Occurrence(start, end)
}

/** The window containing [now], if any (today's, or last night's still running). */
fun ScheduleRule.occurrenceAt(now: Long, zone: ZoneId): Occurrence? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return listOf(today.minusDays(1), today)
        .mapNotNull { occurrenceOn(it, zone) }
        .firstOrNull { now in it }
}

/** Window starts and ends after [now], looking a week ahead. Used to know when to recompute. */
fun ScheduleRule.boundariesAfter(now: Long, zone: ZoneId): List<Long> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return (-1L..7L)
        .mapNotNull { occurrenceOn(today.plusDays(it), zone) }
        .flatMap { listOf(it.start, it.end) }
        .filter { it > now }
}

/** This window was ended early by a puck. */
fun ScheduleRule.isSkipped(occ: Occurrence): Boolean =
    skippedUntil != null && skippedUntil > occ.start && skippedUntil <= occ.end

/**
 * "Unlock without puck" ends the schedule windows that were already running when the wait
 * started. Windows that start during or after the wait aren't affected.
 */
fun endedByCooldown(occ: Occurrence, cooldownEndsAt: Long?, now: Long): Boolean =
    cooldownEndsAt != null &&
        now >= cooldownEndsAt &&
        cooldownEndsAt in occ &&
        occ.start <= cooldownEndsAt - COOLDOWN_MILLIS

private fun minuteToTime(minute: Int): LocalTime = LocalTime.of((minute / 60) % 24, minute % 60)
