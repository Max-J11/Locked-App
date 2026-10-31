package com.max.locked.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.data.PairedKeyEntity
import com.max.locked.ui.common.Group
import com.max.locked.ui.common.GroupDivider
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.MainButton
import com.max.locked.ui.common.SectionLabel
import com.max.locked.ui.common.TimeField
import com.max.locked.ui.common.formatWhen
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val PRESET_MINUTES = listOf(30, 60, 120, 240)
private const val UNTIL_TIME = -1

/** A sheet from the bottom: pick how long and which mode, then Lock. Calls [onLock] with the end time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockForDialog(
    modes: List<ModeSummary>,
    pucks: List<PairedKeyEntity>,
    onLock: (endsAt: Long, modeId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var modeId by rememberSaveable { mutableLongStateOf(BrickRepository.DEFAULT_MODE_ID) }
    var untilMinute by rememberSaveable { mutableIntStateOf(((LocalTime.now().hour + 1) % 24) * 60) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.lock_for_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
            )

            SectionLabel(stringResource(R.string.lock_for_how_long))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_MINUTES.forEach { option ->
                    val (number, unit) = if (option < 60) {
                        option.toString() to stringResource(R.string.lock_for_unit_min)
                    } else {
                        val h = option / 60
                        h.toString() to pluralStringResource(R.plurals.lock_for_unit_hours, h)
                    }
                    DurationTile(
                        selected = minutes == option,
                        onSelect = { minutes = option },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(number, style = MaterialTheme.typography.headlineSmall, color = it)
                        Text(unit, style = MaterialTheme.typography.labelMedium, color = it)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DurationTile(
                selected = minutes == UNTIL_TIME,
                onSelect = { minutes = UNTIL_TIME },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.lock_for_until), style = MaterialTheme.typography.titleMedium, color = it)
            }
            if (minutes == UNTIL_TIME) {
                Spacer(Modifier.height(8.dp))
                Group {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        TimeField(
                            label = stringResource(R.string.lock_for_end_time),
                            minute = untilMinute,
                            onChange = { untilMinute = it },
                        )
                    }
                }
                Text(
                    stringResource(R.string.lock_for_ends, formatWhen(nextOccurrence(untilMinute / 60, untilMinute % 60))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                )
            }

            if (modes.size > 1) {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.lock_for_mode))
                Group {
                    modes.forEachIndexed { i, mode ->
                        if (i > 0) GroupDivider()
                        GroupRow(
                            title = mode.name,
                            titleStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.selectable(
                                selected = modeId == mode.id,
                                role = Role.RadioButton,
                                onClick = { modeId = mode.id },
                            ),
                            leading = { RadioButton(selected = modeId == mode.id, onClick = null) },
                        )
                    }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
            )

            MainButton(
                text = stringResource(R.string.lock_for_confirm),
                onClick = {
                    val endsAt = if (minutes == UNTIL_TIME) {
                        nextOccurrence(untilMinute / 60, untilMinute % 60)
                    } else {
                        System.currentTimeMillis() + minutes * 60_000L
                    }
                    onLock(endsAt, modeId)
                },
            )
        }
    }
}

/** A rounded option: filled deep sage when picked, light with an outline when not. */
@Composable
private fun DurationTile(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainerLow)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content(if (selected) colors.onPrimary else colors.onSurface)
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
