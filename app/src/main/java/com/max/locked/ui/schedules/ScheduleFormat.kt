package com.max.locked.ui.schedules

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.max.locked.R
import com.max.locked.ui.common.formatMinute
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** Days in display order: the week starts on Sunday. */
val WEEK_ORDER: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

/** "Every day", "Weekdays", "Weekends", or "Mon, Wed, Fri". */
@Composable
fun formatDays(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> stringResource(R.string.days_every_day)
    WEEKDAYS -> stringResource(R.string.days_weekdays)
    WEEKEND -> stringResource(R.string.days_weekends)
    else -> WEEK_ORDER.filter { it in days }.joinToString(", ") { shortDayName(it) }
}

fun shortDayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "10:00 PM – 7:00 AM". */
@Composable
fun formatRange(startMinute: Int, endMinute: Int): String =
    stringResource(R.string.schedule_range, formatMinute(startMinute), formatMinute(endMinute))
