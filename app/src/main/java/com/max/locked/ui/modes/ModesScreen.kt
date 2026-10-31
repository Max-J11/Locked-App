package com.max.locked.ui.modes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.GroupItem
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.MainButton
import com.max.locked.ui.common.ScreenPadding

@Composable
fun ModesScreen(
    onBack: () -> Unit,
    onOpenMode: (Long) -> Unit,
    viewModel: ModesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    LockedScaffold(
        title = stringResource(R.string.modes_title),
        onBack = onBack,
        bottomButton = { MainButton(stringResource(R.string.modes_add), onClick = { adding = true }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = ScreenPadding) {
            itemsIndexed(state.modes, key = { _, mode -> mode.id }) { i, mode ->
                val details = buildList {
                    add(pluralStringResource(R.plurals.mode_app_count, mode.appCount, mode.appCount))
                    if (mode.siteCount > 0) {
                        add(pluralStringResource(R.plurals.mode_site_count, mode.siteCount, mode.siteCount))
                    }
                    if (mode.blockNotifications) add(stringResource(R.string.mode_hides_notifications))
                }
                GroupItem(i, state.modes.size) {
                    GroupRow(
                        title = mode.name,
                        subtitle = details.joinToString(" · "),
                        tag = if (mode.id in state.activeModeIds) stringResource(R.string.mode_in_use) else null,
                        onClick = { onOpenMode(mode.id) },
                    )
                }
            }
        }
    }

    if (adding) {
        ModeNameDialog(
            title = stringResource(R.string.modes_add_title),
            confirmLabel = stringResource(R.string.modes_add_confirm),
            onConfirm = { name ->
                adding = false
                viewModel.create(name, onCreated = onOpenMode)
            },
            onDismiss = { adding = false },
        )
    }
}
