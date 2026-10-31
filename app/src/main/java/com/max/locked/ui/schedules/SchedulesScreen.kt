package com.max.locked.ui.schedules

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.EmptyText
import com.max.locked.ui.common.GroupItem
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.MainButton
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding

@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LockedScaffold(
        title = stringResource(R.string.schedules_title),
        onBack = onBack,
        bottomButton = { MainButton(stringResource(R.string.schedules_add), onClick = onAdd) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = ScreenPadding) {
            if (!state.loading && state.schedules.isEmpty()) {
                item { EmptyText(stringResource(R.string.schedules_empty)) }
            }
            itemsIndexed(state.schedules, key = { _, item -> item.id }) { i, item ->
                GroupItem(i, state.schedules.size) {
                    GroupRow(
                        title = formatRange(item.startMinute, item.endMinute),
                        titleStyle = MaterialTheme.typography.titleLarge,
                        subtitle = "${formatDays(item.days)} · ${item.modeName}",
                        tag = if (item.running) stringResource(R.string.schedule_running) else null,
                        onClick = { onOpen(item.id) },
                        trailing = {
                            // A running schedule can't be switched off (it can always be switched on).
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { viewModel.setEnabled(item.id, it) },
                                enabled = !item.running,
                            )
                        },
                    )
                }
            }
            if (state.schedules.any { it.running }) {
                item {
                    Spacer(Modifier.height(16.dp))
                    NoteBox(stringResource(R.string.schedules_running_note))
                }
            }
        }
    }
}
