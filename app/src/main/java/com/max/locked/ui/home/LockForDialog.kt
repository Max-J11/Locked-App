package com.max.locked.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.data.PairedKeyEntity
import com.max.locked.ui.common.formatWhen
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val PRESET_MINUTES = listOf(30, 60, 120, 240)
private const val UNTIL_TIME = -1

/** Pick a mode and how long to lock for. Calls [onLock] with the end time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockForDialog(
    modes: List<ModeSummary>,
    pucks: List<PairedKeyEntity>,
    onLock: (endsAt: Long, modeId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by rememberSaveable { mutableStateOf(60) }
    var modeId by rememberSaveable { mutableLongStateOf(BrickRepository.DEFAULT_MODE_ID) }
    val context = LocalContext.current
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    val untilTime = rememberTimePickerState(
        initialHour = (LocalTime.now().hour + 1) % 24,
        initialMinute = 0,
        is24Hour = is24h,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_for_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (PRESET_MINUTES + UNTIL_TIME).forEach { option ->
                    RadioRow(
                        label = if (option == UNTIL_TIME) stringResource(R.string.lock_for_until) else durationLabel(option),
                        selected = minutes == option,
                        onSelect = { minutes = option },
                    )
                }
                if (minutes == UNTIL_TIME) {
                    Text(
                        stringResource(R.string.lock_for_ends, formatWhen(nextOccurrence(untilTime.hour, untilTime.minute))),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                    )
                    TimeInput(state = untilTime)
                }
                if (modes.size > 1) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.lock_for_mode), style = MaterialTheme.typography.titleSmall)
                    modes.forEach { mode ->
                        RadioRow(label = mode.name, selected = modeId == mode.id, onSelect = { modeId = mode.id })
                    }
                }
                val enders = pucks.filter { it.modeId == modeId }.map { it.name }
                Text(
                    text = if (enders.isEmpty()) {
                        stringResource(R.string.lock_for_no_puck)
                    } else {
                        stringResource(R.string.lock_for_ended_by, joinNames(enders))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                val endsAt = if (minutes == UNTIL_TIME) {
                    nextOccurrence(untilTime.hour, untilTime.minute)
                } else {
                    now + minutes * 60_000L
                }
                onLock(endsAt, modeId)
            }) { Text(stringResource(R.string.lock_for_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes < 60) {
        stringResource(R.string.duration_minutes, minutes)
    } else {
        val hours = minutes / 60
        androidx.compose.ui.res.pluralStringResource(R.plurals.duration_hours, hours, hours)
    }

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The next time the clock reads [hour]:[minute]: later today, or tomorrow if that's passed. */
private fun nextOccurrence(hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now(zone)
    var target = now.toLocalDate().atTime(hour, minute)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.atZone(zone).toInstant().toEpochMilli()
}
