package com.max.locked.ui.modes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.max.locked.R

const val MODE_NAME_MAX = 30

/** Asks for a name. Used for adding and renaming modes, and renaming pucks. */
@Composable
fun ModeNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MODE_NAME_MAX) },
                label = { Text(stringResource(R.string.pairing_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
