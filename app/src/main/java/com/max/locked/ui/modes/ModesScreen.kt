package com.max.locked.ui.modes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(
    onBack: () -> Unit,
    onOpenMode: (Long) -> Unit,
    viewModel: ModesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.modes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.modes, key = { it.id }) { mode ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMode(mode.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(mode.name, style = MaterialTheme.typography.titleMedium)
                        val details = buildList {
                            add(pluralStringResource(R.plurals.mode_app_count, mode.appCount, mode.appCount))
                            if (mode.blockNotifications) add(stringResource(R.string.mode_hides_notifications))
                            if (mode.id in state.activeModeIds) add(stringResource(R.string.mode_in_use))
                        }
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.modes_add))
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
