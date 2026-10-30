package com.max.locked.ui.pucks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.data.PairedKeyEntity
import com.max.locked.ui.modes.ModeNameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PucksScreen(
    onBack: () -> Unit,
    onPair: () -> Unit,
    viewModel: PucksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<PairedKeyEntity?>(null) }
    var choosingMode by remember { mutableStateOf<PairedKeyEntity?>(null) }
    var renaming by remember { mutableStateOf<PairedKeyEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pucks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (state.pucks.isEmpty()) {
                Text(stringResource(R.string.pucks_empty), style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.pucks, key = { it.id }) { puck ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isLocked) { choosingMode = puck }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(puck.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.puck_mode, state.modeName(puck.modeId)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = { renaming = puck }) {
                            Text(stringResource(R.string.pucks_rename))
                        }
                        TextButton(onClick = { confirmDelete = puck }, enabled = !state.isLocked) {
                            Text(stringResource(R.string.pucks_remove))
                        }
                    }
                }
            }
            if (state.isLocked && state.pucks.isNotEmpty()) {
                Text(
                    stringResource(R.string.pucks_locked_note),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Button(onClick = onPair, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.pucks_pair_new))
            }
        }
    }

    choosingMode?.let { puck ->
        AlertDialog(
            onDismissRequest = { choosingMode = null },
            title = { Text(stringResource(R.string.puck_choose_mode_title, puck.name)) },
            text = {
                Column {
                    state.modes.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode.id == puck.modeId,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setMode(puck.id, mode.id)
                                        choosingMode = null
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode.id == puck.modeId, onClick = null)
                            Text(mode.name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingMode = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    renaming?.let { puck ->
        ModeNameDialog(
            title = stringResource(R.string.pucks_rename_title),
            confirmLabel = stringResource(R.string.save),
            initial = puck.name,
            onConfirm = { viewModel.rename(puck.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    confirmDelete?.let { puck ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.pucks_remove_title, puck.name)) },
            text = { Text(stringResource(R.string.pucks_remove_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(puck.id); confirmDelete = null }) {
                    Text(stringResource(R.string.pucks_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
