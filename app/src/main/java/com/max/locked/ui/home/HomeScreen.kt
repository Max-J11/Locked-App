package com.max.locked.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.ui.common.formatWhen
import com.max.locked.domain.COOLDOWN_MILLIS
import com.max.locked.domain.PASSES_PER_LOCK

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    notificationAccess: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onModes: () -> Unit,
    onSchedules: () -> Unit,
    onPucks: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingLockFor by rememberSaveable { mutableStateOf(false) }
    var confirmCooldown by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (state.isLocked) R.string.status_locked else R.string.status_unlocked),
            style = MaterialTheme.typography.displaySmall,
        )
        state.activeModeName?.let { name ->
            Text(
                text = stringResource(
                    if (state.fromSchedule) R.string.home_active_mode_scheduled else R.string.home_active_mode,
                    name,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        state.endsAt?.let { endsAt ->
            Text(
                text = stringResource(R.string.home_until, formatWhen(endsAt)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                state.isLocked && state.unlockPuckNames.isNotEmpty() ->
                    stringResource(R.string.home_hint_unlock_with, joinNames(state.unlockPuckNames))
                state.isLocked -> stringResource(R.string.home_hint_no_puck)
                state.puckCount == 0 -> stringResource(R.string.home_hint_pair)
                else -> stringResource(R.string.home_hint_lock)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (state.isLocked) {
            Text(
                text = state.passEndsAt?.let { stringResource(R.string.home_pass_open, formatWhen(it)) }
                    ?: pluralStringResource(R.plurals.home_passes_left, state.passesLeft, state.passesLeft, PASSES_PER_LOCK),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (!state.isLocked) {
            Button(onClick = { choosingLockFor = true }) { Text(stringResource(R.string.lock_for_button)) }
        } else if (state.cooldownEndsAt != null) {
            Text(
                text = stringResource(R.string.cooldown_running, formatWhen(state.cooldownEndsAt!!)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = viewModel::cancelCooldown) { Text(stringResource(R.string.cooldown_cancel)) }
        } else if (state.canStartCooldown) {
            OutlinedButton(onClick = { confirmCooldown = true }) {
                Text(stringResource(R.string.cooldown_button))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = pluralStringResource(R.plurals.modes_count, state.modes.size, state.modes.size),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onModes) { Text(stringResource(R.string.modes_title)) }

        Text(
            text = pluralStringResource(R.plurals.schedules_count, state.scheduleCount, state.scheduleCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onSchedules) { Text(stringResource(R.string.schedules_title)) }

        Text(
            text = pluralStringResource(R.plurals.pucks_paired, state.puckCount, state.puckCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onPucks) { Text(stringResource(R.string.manage_pucks)) }

        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(if (serviceEnabled) R.string.service_on else R.string.service_off),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.open_accessibility_settings))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (notificationAccess) R.string.notifications_on else R.string.notifications_off),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.open_notification_settings))
        }
    }

    if (choosingLockFor) {
        LockForDialog(
            modes = state.modes,
            pucks = state.pucks,
            onLock = { endsAt, modeId ->
                viewModel.lockUntil(endsAt, modeId)
                choosingLockFor = false
            },
            onDismiss = { choosingLockFor = false },
        )
    }

    if (confirmCooldown) {
        val minutes = (COOLDOWN_MILLIS / 60_000).toInt()
        AlertDialog(
            onDismissRequest = { confirmCooldown = false },
            title = { Text(stringResource(R.string.cooldown_title)) },
            text = { Text(stringResource(R.string.cooldown_body, minutes)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startCooldown()
                    confirmCooldown = false
                }) { Text(stringResource(R.string.cooldown_start)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCooldown = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** "Home", "Home or Car", "Home, Car or Desk". */
@Composable
internal fun joinNames(names: List<String>): String {
    if (names.size == 1) return names.single()
    return stringResource(R.string.names_or, names.dropLast(1).joinToString(", "), names.last())
}
