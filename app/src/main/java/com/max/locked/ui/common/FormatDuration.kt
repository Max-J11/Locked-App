package com.max.locked.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.max.locked.R
import com.max.locked.domain.hoursAndMinutes

/** "4 h 10 m", or "25 m" under an hour. */
@Composable
fun formatDuration(millis: Long): String {
    val (h, m) = hoursAndMinutes(millis)
    return if (h > 0) stringResource(R.string.duration_hm, h, m) else stringResource(R.string.duration_m, m)
}
