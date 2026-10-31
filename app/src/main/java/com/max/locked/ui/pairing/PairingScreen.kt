package com.max.locked.ui.pairing

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.max.locked.ui.common.LockedScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.nfc.WriteError

@Composable
fun PairingScreen(
    onDone: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val adapter = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it) } }
    var nfcOn by remember { mutableStateOf(adapter?.isEnabled == true) }

    // Re-check NFC whenever we come back (e.g. from NFC settings), and own the reader for the
    // whole time this screen is visible, so taps here never lock or unlock.
    LifecycleResumeEffect(adapter) {
        nfcOn = adapter?.isEnabled == true
        if (activity != null && adapter != null && nfcOn) {
            adapter.enableReaderMode(activity, viewModel::onTag, READER_FLAGS, null)
        }
        onPauseOrDispose {
            if (activity != null) adapter?.disableReaderMode(activity)
        }
    }

    val view = LocalView.current
    LaunchedEffect(state.step) {
        when (state.step) {
            is PairingStep.Done -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            is PairingStep.Failed -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            else -> Unit
        }
    }

    LockedScaffold(title = stringResource(R.string.pairing_title), onBack = onDone) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                adapter == null -> Message(stringResource(R.string.pairing_no_nfc))
                !nfcOn -> {
                    Message(stringResource(R.string.pairing_nfc_off))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { activity?.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                        Text(stringResource(R.string.pairing_open_nfc_settings))
                    }
                }
                else -> StepContent(state, viewModel, onDone)
            }
        }
    }
}

@Composable
private fun StepContent(state: PairingUiState, viewModel: PairingViewModel, onDone: () -> Unit) {
    when (val step = state.step) {
        PairingStep.EnterName -> {
            Message(stringResource(R.string.pairing_name_prompt))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.pairing_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::startWaiting) { Text(stringResource(R.string.pairing_next)) }
        }
        PairingStep.Waiting -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Message(stringResource(R.string.pairing_hold_tag))
        }
        PairingStep.TapAgain -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Message(stringResource(R.string.pairing_tap_again))
        }
        PairingStep.Writing -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Message(stringResource(R.string.pairing_writing))
        }
        is PairingStep.FoundExisting -> {
            Message(stringResource(R.string.pairing_found_existing))
            Spacer(Modifier.height(24.dp))
            Button(onClick = viewModel::useExisting) { Text(stringResource(R.string.pairing_use_existing)) }
            Note(stringResource(R.string.pairing_use_existing_note))
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = viewModel::writeNewKey) { Text(stringResource(R.string.pairing_write_new)) }
            Note(stringResource(R.string.pairing_write_new_note))
        }
        is PairingStep.Done -> {
            Message(stringResource(R.string.pairing_done, step.name))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone) { Text(stringResource(R.string.pairing_finish)) }
        }
        is PairingStep.Failed -> {
            Message(stringResource(errorText(step.error)))
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::startWaiting) { Text(stringResource(R.string.pairing_try_again)) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun errorText(error: WriteError): Int = when (error) {
    WriteError.NOT_SUPPORTED -> R.string.pairing_error_not_supported
    WriteError.READ_ONLY -> R.string.pairing_error_read_only
    WriteError.TOO_SMALL -> R.string.pairing_error_too_small
    WriteError.TAG_LOST -> R.string.pairing_error_tag_lost
    WriteError.VERIFY_FAILED, WriteError.IO -> R.string.pairing_error_io
}

private const val READER_FLAGS =
    NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V
