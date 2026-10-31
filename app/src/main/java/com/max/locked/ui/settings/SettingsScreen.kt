package com.max.locked.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.Group
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        }
    }
}
