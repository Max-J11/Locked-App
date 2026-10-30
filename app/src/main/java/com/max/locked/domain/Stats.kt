package com.max.locked.domain

import java.time.LocalDate
import java.time.ZoneId

/** A locked stretch, for stats. [end] is null while it's still going, and counts up to now. */
data class LockedSpan(val start: Long, val end: Long?)

/** Spans as closed intervals, overlaps merged so no time is counted twice. Sorted by start. */
fun mergeSpans(spans: List<LockedSpan>, now: Long): List<Pair<Long, Long>> {
    val intervals = spans
        .map { it.start to (it.end ?: now).coerceAtLeast(it.start) }
        .filter { it.second > it.first }
        .sortedBy { it.first }
    val merged = mutableListOf<Pair<Long, Long>>()
    for (i in intervals) {
        val last = merged.lastOrNull()
        if (last != null && i.first <= last.second) {
            merged[merged.lastIndex] = last.first to maxOf(last.second, i.second)
        } else {
            merged += i
        }
    }
    return merged
}

/** Time locked between [from] and [to]. Spans crossing either edge are clipped. */
fun lockedMillisBetween(spans: List<LockedSpan>, from: Long, to: Long, now: Long): Long =
    mergeSpans(spans, now).sumOf { (s, e) -> (minOf(e, to) - maxOf(s, from)).coerceAtLeast(0) }

/** Time locked on each of [days] (local calendar days in [zone]), in the same order. */
fun lockedMillisPerDay(spans: List<LockedSpan>, days: List<LocalDate>, zone: ZoneId, now: Long): List<Long> {
    val merged = mergeSpans(spans, now).map { LockedSpan(it.first, it.second) }
    return days.map { day ->
        lockedMillisBetween(merged, startOfDay(day, zone), startOfDay(day.plusDays(1), zone), now)
    }
}

/** The last [count] days ending with [today], oldest first. */
fun lastDays(today: LocalDate, count: Int): List<LocalDate> =
    (count - 1 downTo 0).map { today.minusDays(it.toLong()) }

fun startOfDay(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

/** Locks that started in [from, to). */
fun locksStartedBetween(spans: List<LockedSpan>, from: Long, to: Long): Int =
    spans.count { it.start in from until to }

/** Whole hours and leftover whole minutes in [millis]. */
fun hoursAndMinutes(millis: Long): Pair<Long, Long> {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return minutes / 60 to minutes % 60
}
