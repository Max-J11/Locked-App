package com.max.locked.ui.pucks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.data.PairedKeyEntity
import com.max.locked.ui.common.EmptyText
import com.max.locked.ui.common.GroupItem
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.MainButton
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding
import com.max.locked.ui.modes.ModeNameDialog

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

    LockedScaffold(
        title = stringResource(R.string.pucks_title),
        onBack = onBack,
        bottomButton = { MainButton(stringResource(R.string.pucks_pair_new), onClick = onPair) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = ScreenPadding) {
            if (state.pucks.isEmpty()) {
                item { EmptyText(stringResource(R.string.pucks_empty)) }
            }
            if (state.isLocked && state.pucks.isNotEmpty()) {
                item {
                    NoteBox(stringResource(R.string.pucks_locked_note))
                    Spacer(Modifier.height(16.dp))
                }
            }
            itemsIndexed(state.pucks, key = { _, puck -> puck.id }) { i, puck ->
                GroupItem(i, state.pucks.size) {
                    GroupRow(
                        title = puck.name,
                        subtitle = stringResource(R.string.puck_mode, state.modeName(puck.modeId)),
                        onClick = { choosingMode = puck },
                        enabled = !state.isLocked,
                    )
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
                        TextButton(onClick = { renaming = puck }) { Text(stringResource(R.string.pucks_rename)) }
                        TextButton(onClick = { confirmDelete = puck }, enabled = !state.isLocked) {
                            Text(stringResource(R.string.pucks_remove))
                        }
                    }
                }
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
