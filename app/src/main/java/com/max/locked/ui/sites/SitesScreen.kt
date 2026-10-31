package com.max.locked.ui.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.EmptyText
import com.max.locked.ui.common.GroupItem
import com.max.locked.ui.common.GroupRow
import com.max.locked.ui.common.LockedScaffold
import com.max.locked.ui.common.NoteBox
import com.max.locked.ui.common.ScreenPadding

@Composable
fun SitesScreen(
    onBack: () -> Unit,
    viewModel: SitesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val add = { if (input.isNotBlank()) viewModel.add(input) { input = "" } }

    LockedScaffold(title = stringResource(R.string.sites_screen_title, state.modeName), onBack = onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = ScreenPadding) {
            item {
                Text(
                    stringResource(R.string.sites_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            viewModel.clearInvalid()
                        },
                        placeholder = { Text(stringResource(R.string.sites_add_hint)) },
                        isError = state.invalidInput,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = add, enabled = input.isNotBlank(), modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.sites_add))
                    }
                }
                if (state.invalidInput) {
                    Text(
                        stringResource(R.string.sites_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (state.modeActive) {
                    NoteBox(stringResource(R.string.sites_active_note))
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (state.sites.isEmpty()) {
                item { EmptyText(stringResource(R.string.sites_none)) }
            }
            itemsIndexed(state.sites, key = { _, site -> site }) { i, site ->
                GroupItem(i, state.sites.size) {
                    GroupRow(
                        title = site,
                        titleStyle = MaterialTheme.typography.bodyLarge,
                        trailing = {
                            TextButton(onClick = { viewModel.remove(site) }, enabled = !state.modeActive) {
                                Text(stringResource(R.string.sites_remove))
                            }
                        },
                    )
                }
            }
        }
    }
}
