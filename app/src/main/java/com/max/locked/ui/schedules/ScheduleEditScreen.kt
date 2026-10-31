package com.max.locked.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.data.ScheduleEntity
import com.max.locked.data.toDays
import com.max.locked.ui.common.Group
import com.max.locked.ui.common.GroupDivider
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.MainButton
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding
import com.max.locked.ui.common.SectionLabel
import com.max.locked.ui.common.TimeField
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleEditScreen(
    onBack: () -> Unit,
    viewModel: ScheduleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LockedScaffold(
        title = stringResource(if (state.isNew) R.string.schedule_add_title else R.string.schedule_edit_title),
        onBack = onBack,
        actions = {
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }, enabled = !state.running) {
                    Text(stringResource(R.string.mode_delete))
                }
            }
        },
    ) { padding ->
        val initial = state.initial
        if (!state.loaded || initial == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ScheduleForm(
                initial = initial,
                state = state,
                modifier = Modifier.padding(padding),
                onSave = { days, start, end, modeId -> viewModel.save(days, start, end, modeId, onDone = onBack) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.schedule_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone = onBack)
                }) { Text(stringResource(R.string.mode_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ScheduleForm(
    initial: ScheduleEntity,
    state: ScheduleEditUiState,
    onSave: (days: Set<DayOfWeek>, startMinute: Int, endMinute: Int, modeId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = !state.running
    var days by rememberSaveable { mutableStateOf(initial.days.toDays().map { it.value }.toSet()) }
    var modeId by rememberSaveable { mutableLongStateOf(initial.modeId) }
    var startMinute by rememberSaveable { mutableIntStateOf(initial.startMinute) }
    var endMinute by rememberSaveable { mutableIntStateOf(initial.endMinute) }

    val dayValues = days.map { DayOfWeek.of(it) }.toSet()
    val valid = days.isNotEmpty() && startMinute != endMinute

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        if (state.running) {
            NoteBox(stringResource(R.string.schedule_running_note))
            Spacer(Modifier.height(16.dp))
        }

        SectionLabel(stringResource(R.string.schedule_days))
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WEEK_ORDER.forEach { day ->
                        val on = day.value in days
                        DayCircle(
                            day = day,
                            on = on,
                            enabled = editable,
                            onToggle = { days = if (on) days - day.value else days + day.value },
                        )
                    }
                }
                Text(
                    text = if (days.isEmpty()) {
                        stringResource(R.string.schedule_pick_days)
                    } else {
                        stringResource(R.string.schedule_summary, formatDays(dayValues))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.schedule_time))
        Group {
            Column(Modifier.padding(horizontal = 20.dp)) {
                TimeField(
                    label = stringResource(R.string.schedule_starts),
                    minute = startMinute,
                    onChange = { startMinute = it },
                    enabled = editable,
                )
            }
            GroupDivider()
            Column(Modifier.padding(horizontal = 20.dp)) {
                TimeField(
                    label = stringResource(R.string.schedule_ends),
                    minute = endMinute,
                    onChange = { endMinute = it },
                    enabled = editable,
                )
            }
        }
        when {
            startMinute == endMinute -> stringResource(R.string.schedule_same_time)
            endMinute < startMinute -> stringResource(R.string.schedule_overnight)
            else -> null
        }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
        }

        if (state.modes.size > 1) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.lock_for_mode))
            Group {
                state.modes.forEachIndexed { i, mode ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        title = mode.name,
                        titleStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.selectable(
                            selected = modeId == mode.id,
                            enabled = editable,
                            role = Role.RadioButton,
                            onClick = { modeId = mode.id },
                        ),
                        leading = { RadioButton(selected = modeId == mode.id, onClick = null, enabled = editable) },
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        MainButton(
            text = stringResource(R.string.save),
            onClick = { onSave(dayValues, startMinute, endMinute, modeId) },
            enabled = editable && valid,
        )
    }
}

/** One day of the week as a circle: filled when chosen, outlined when not. */
@Composable
private fun DayCircle(day: DayOfWeek, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill = when {
        on && enabled -> colors.primary
        on -> colors.primary.copy(alpha = 0.38f)
        else -> colors.surfaceContainerLowest
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (on) fill else colors.outline, CircleShape)
            .toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            color = if (on) colors.onPrimary else colors.onSurface,
        )
    }
}
