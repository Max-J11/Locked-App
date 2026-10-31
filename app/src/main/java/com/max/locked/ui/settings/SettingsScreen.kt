package com.max.locked.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.domain.MAX_PASSES_PER_LOCK
import com.max.locked.domain.PASS_MILLIS
import com.max.locked.ui.common.Group
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding

private val PASS_MINUTES = (PASS_MILLIS / 60_000).toInt()

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingPasses by rememberSaveable { mutableStateOf(false) }

    LockedScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Group {
                GroupRow(
                    title = stringResource(R.string.strict_mode),
                    subtitle = stringResource(R.string.strict_mode_summary),
                    modifier = Modifier.toggleable(
                        value = state.strictMode,
                        enabled = !state.strictModeLocked,
                        role = Role.Switch,
                        onValueChange = viewModel::setStrictMode,
                    ),
                    trailing = {
                        Switch(checked = state.strictMode, onCheckedChange = null, enabled = !state.strictModeLocked)
                    },
                )
            }
            if (state.strictModeLocked) NoteBox(stringResource(R.string.strict_mode_locked_note))

            Group {
                GroupRow(
                    title = stringResource(R.string.passes_per_lock),
                    subtitle = passesSummary(state.passCap),
                    onClick = { choosingPasses = true },
                    // Nothing lower to pick while locked at 0.
                    enabled = !(state.passCapLocked && state.passCap == 0),
                    trailing = {
                        Text(
                            state.passCap.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            if (state.passCapLocked) NoteBox(stringResource(R.string.passes_per_lock_locked_note))
        }
    }

    if (choosingPasses) {
        PassCapDialog(
            current = state.passCap,
            locked = state.passCapLocked,
            onPick = { viewModel.setPassCap(it); choosingPasses = false },
            onDismiss = { choosingPasses = false },
        )
    }
}

@Composable
private fun passesSummary(count: Int): String =
    if (count == 0) {
        stringResource(R.string.passes_per_lock_none)
    } else {
        pluralStringResource(R.plurals.passes_per_lock_summary, count, count, PASS_MINUTES)
    }

/** 0 to 5. While locked, numbers above the current one are greyed out. */
@Composable
private fun PassCapDialog(current: Int, locked: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.passes_per_lock_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.passes_per_lock_body, PASS_MINUTES),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                (0..MAX_PASSES_PER_LOCK).forEach { n ->
                    val allowed = !locked || n <= current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = n == current,
                                enabled = allowed,
                                role = Role.RadioButton,
                                onClick = { onPick(n) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = n == current, onClick = null, enabled = allowed)
                        Text(
                            n.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (locked) {
                    Text(
                        stringResource(R.string.passes_per_lock_locked_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
