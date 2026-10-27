package com.max.locked.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.data.ScheduleEntity
import com.max.locked.data.toDays
import com.max.locked.ui.common.TimeField
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    onBack: () -> Unit,
    viewModel: ScheduleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.schedule_add_title else R.string.schedule_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        TextButton(onClick = { confirmDelete = true }, enabled = !state.running) {
                            Text(stringResource(R.string.mode_delete))
                        }
                    }
                },
            )
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.running) {
            Text(stringResource(R.string.schedule_running_note), style = MaterialTheme.typography.bodyMedium)
        }

        Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WEEK_ORDER.forEach { day ->
                val on = day.value in days
                // On: solid fill with a check. Off: plain outline. Easy to tell apart at a glance.
                FilterChip(
                    selected = on,
                    onClick = { days = if (on) days - day.value else days + day.value },
                    label = { Text(if (on) "✓ ${shortDayName(day)}" else shortDayName(day)) },
                    enabled = editable,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        disabledSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = editable,
                        selected = on,
                        borderColor = MaterialTheme.colorScheme.outline,
                    ),
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
            color = if (days.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )

        Column {
            TimeField(
                label = stringResource(R.string.schedule_starts),
                minute = startMinute,
                onChange = { startMinute = it },
                enabled = editable,
            )
            TimeField(
                label = stringResource(R.string.schedule_ends),
                minute = endMinute,
                onChange = { endMinute = it },
                enabled = editable,
            )
        }

        when {
            startMinute == endMinute -> stringResource(R.string.schedule_same_time)
            endMinute < startMinute -> stringResource(R.string.schedule_overnight)
            else -> null
        }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        if (state.modes.size > 1) {
            Text(stringResource(R.string.lock_for_mode), style = MaterialTheme.typography.titleSmall)
            Column {
                state.modes.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = modeId == mode.id,
                                enabled = editable,
                                role = Role.RadioButton,
                                onClick = { modeId = mode.id },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = modeId == mode.id, onClick = null, enabled = editable)
                        Text(mode.name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }

        Button(
            onClick = { onSave(dayValues, startMinute, endMinute, modeId) },
            enabled = editable && valid,
        ) { Text(stringResource(R.string.save)) }
    }
}
