package com.max.locked.ui.schedules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.loading && state.schedules.isEmpty()) {
                Text(
                    stringResource(R.string.schedules_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.schedules, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(formatRange(item.startMinute, item.endMinute), style = MaterialTheme.typography.titleMedium)
                            val details = buildList {
                                add(formatDays(item.days))
                                add(item.modeName)
                                if (item.running) add(stringResource(R.string.schedule_running))
                            }
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        }
                        // A running schedule can't be switched off (it can always be switched on).
                        Switch(
                            checked = item.enabled,
                            onCheckedChange = { viewModel.setEnabled(item.id, it) },
                            enabled = !item.running,
                        )
                    }
                }
            }
            if (state.schedules.any { it.running }) {
                Text(
                    stringResource(R.string.schedules_running_note),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Button(onClick = onAdd, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.schedules_add))
            }
        }
    }
}
