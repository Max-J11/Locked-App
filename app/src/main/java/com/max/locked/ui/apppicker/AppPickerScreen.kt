package com.max.locked.ui.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.Group
import com.max.locked.ui.common.GroupDivider
import com.max.locked.ui.common.GroupItem
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding
import com.max.locked.ui.common.SectionLabel
import com.max.locked.ui.modes.ModeNameDialog

@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onOpenSites: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LockedScaffold(
        title = state.modeName,
        onBack = onBack,
        actions = {
            TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.mode_rename)) }
            if (!state.isDefaultMode) {
                TextButton(onClick = { confirmDelete = true }, enabled = state.canDelete) {
                    Text(stringResource(R.string.mode_delete))
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = ScreenPadding) {
            if (state.modeActive) {
                item {
                    NoteBox(stringResource(R.string.mode_active_note))
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                Group {
                    GroupRow(
                        title = stringResource(R.string.hide_notifications),
                        subtitle = if (state.hideNotificationsLocked) {
                            stringResource(R.string.hide_notifications_locked_note)
                        } else {
                            null
                        },
                        modifier = Modifier.toggleable(
                            value = state.hideNotifications,
                            enabled = !state.hideNotificationsLocked,
                            role = Role.Switch,
                            onValueChange = viewModel::setHideNotifications,
                        ),
                        trailing = {
                            Switch(
                                checked = state.hideNotifications,
                                onCheckedChange = null,
                                enabled = !state.hideNotificationsLocked,
                            )
                        },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.sites_title),
                        subtitle = sites.joinToString(", ").ifEmpty { stringResource(R.string.sites_none) },
                        onClick = onOpenSites,
                    )
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.app_picker_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.app_picker_apps))
            }
            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                itemsIndexed(state.apps, key = { _, app -> app.packageName }) { i, app ->
                    GroupItem(i, state.apps.size) {
                        AppRow(app, onToggle = { viewModel.setSelected(app.packageName, it) })
                    }
                }
            }
        }
    }

    if (renaming) {
        ModeNameDialog(
            title = stringResource(R.string.mode_rename_title),
            confirmLabel = stringResource(R.string.save),
            initial = state.modeName,
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mode_delete_title, state.modeName)) },
            text = { Text(stringResource(R.string.mode_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text(stringResource(R.string.mode_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AppRow(app: AppItem, onToggle: (Boolean) -> Unit) {
    val icon = app.icon
    GroupRow(
        title = app.label,
        titleStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.toggleable(
            value = app.selected,
            enabled = !app.locked,
            role = Role.Checkbox,
            onValueChange = onToggle,
        ),
        leading = {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
            } else {
                Spacer(Modifier.size(36.dp))
            }
        },
        trailing = { Checkbox(checked = app.selected, onCheckedChange = null, enabled = !app.locked) },
    )
}
