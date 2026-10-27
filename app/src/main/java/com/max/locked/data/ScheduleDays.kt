package com.max.locked.data

import com.max.locked.domain.ScheduleRule
import java.time.DayOfWeek

/** Stored as a bitmask: Monday = bit 0 ... Sunday = bit 6. */
fun Int.toDays(): Set<DayOfWeek> =
    DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun ScheduleEntity.toRule() = ScheduleRule(
    id = id,
    modeId = modeId,
    days = days.toDays(),
    startMinute = startMinute,
    endMinute = endMinute,
    enabled = enabled,
    skippedUntil = skippedUntil,
)
