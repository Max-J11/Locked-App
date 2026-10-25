package com.max.locked.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.max.locked.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** "2:25 PM" today, "tomorrow at 2:25 PM", or "Mon 2:25 PM" further out. */
@Composable
fun formatWhen(millis: Long): String {
    val context = LocalContext.current
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> time
        today.plusDays(1) -> stringResource(R.string.when_tomorrow, time)
        else -> android.text.format.DateUtils.formatDateTime(
            context,
            millis,
            android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or
                android.text.format.DateUtils.FORMAT_ABBREV_WEEKDAY or
                android.text.format.DateUtils.FORMAT_SHOW_TIME,
        )
    }
}
