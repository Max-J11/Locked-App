package com.max.locked.ui.home

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.domain.COOLDOWN_MILLIS
import com.max.locked.domain.PASSES_PER_LOCK
import com.max.locked.domain.PartOfDay
import com.max.locked.service.SetupStatus
import com.max.locked.ui.common.formatDuration
import com.max.locked.ui.common.formatWhen

@Composable
fun HomeScreen(
    setup: SetupStatus,
    taglineIndex: Int,
    onOpenSettings: (Intent) -> Unit,
    onModes: () -> Unit,
    onSchedules: () -> Unit,
    onPucks: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingLockFor by rememberSaveable { mutableStateOf(false) }
    var confirmCooldown by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // At least screen-tall, so the status sits in the middle and the links at the bottom;
        // scrolls if the setup card makes it longer.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Greeting(state.partOfDay, taglineIndex)
                if (!setup.allDone) SetupCard(setup, onOpenSettings)
            }

            Status(
                state = state,
                onLockFor = { choosingLockFor = true },
                onStartCooldown = { confirmCooldown = true },
                onCancelCooldown = viewModel::cancelCooldown,
                modifier = Modifier.padding(vertical = 48.dp),
            )

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LinkRow(
                    stringResource(R.string.modes_title) to onModes,
                    stringResource(R.string.schedules_title) to onSchedules,
                    stringResource(R.string.manage_pucks) to onPucks,
                )
                LinkRow(
                    stringResource(R.string.stats_title) to onStats,
                    stringResource(R.string.settings_title) to onSettings,
                )
            }
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

@Composable
private fun Greeting(partOfDay: PartOfDay, taglineIndex: Int) {
    val taglines = stringArrayResource(R.array.greeting_taglines)
    Column {
        Text(
            stringResource(
                when (partOfDay) {
                    PartOfDay.MORNING -> R.string.greeting_morning
                    PartOfDay.AFTERNOON -> R.string.greeting_afternoon
                    PartOfDay.EVENING -> R.string.greeting_evening
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(taglines.getOrElse(taglineIndex) { taglines.first() }, style = MaterialTheme.typography.headlineSmall)
    }
}

/** The lock in large, quiet type, centred. */
@Composable
private fun Status(
    state: HomeUiState,
    onLockFor: () -> Unit,
    onStartCooldown: () -> Unit,
    onCancelCooldown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.isLocked) {
            Text(
                stringResource(R.string.home_ring_locked_for),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurfaceVariant,
            )
            Text(
                if (state.lockedForMillis < 60_000) stringResource(R.string.home_ring_just_now)
                else formatDuration(state.lockedForMillis),
                style = MaterialTheme.typography.displayLarge,
                color = colors.primary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                stringResource(R.string.status_unlocked),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))

        val modeLine = state.activeModeName?.let { name ->
            stringResource(
                if (state.fromSchedule) R.string.home_active_mode_scheduled else R.string.home_active_mode,
                name,
            )
        }
        val untilLine = state.endsAt?.let { stringResource(R.string.home_until, formatWhen(it)) }
        listOfNotNull(modeLine, untilLine).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(
            text = when {
                state.isLocked && state.unlockPuckNames.isNotEmpty() ->
                    stringResource(R.string.home_hint_unlock_with, joinNames(state.unlockPuckNames))
                state.isLocked -> stringResource(R.string.home_hint_no_puck)
                state.puckCount == 0 -> stringResource(R.string.home_hint_pair)
                else -> stringResource(R.string.home_hint_lock)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state.isLocked) {
            Text(
                text = state.passEndsAt?.let { stringResource(R.string.home_pass_open, formatWhen(it)) }
                    ?: pluralStringResource(R.plurals.home_passes_left, state.passesLeft, state.passesLeft, PASSES_PER_LOCK),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        when {
            !state.isLocked -> Button(onClick = onLockFor, modifier = Modifier.padding(top = 20.dp)) {
                Text(stringResource(R.string.lock_for_button))
            }
            state.cooldownEndsAt != null -> {
                Text(
                    stringResource(R.string.cooldown_running, formatWhen(state.cooldownEndsAt)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
                TextButton(onClick = onCancelCooldown) { Text(stringResource(R.string.cooldown_cancel)) }
            }
            state.canStartCooldown -> OutlinedButton(
                onClick = onStartCooldown,
                modifier = Modifier.padding(top = 20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surfaceContainerLowest,
                    contentColor = colors.primary,
                ),
                border = BorderStroke(1.dp, colors.primary),
            ) {
                Text(stringResource(R.string.cooldown_button))
            }
        }
    }
}

/** Plain text links separated by dots: "Modes · Schedules · Pucks". */
@Composable
private fun LinkRow(vararg links: Pair<String, () -> Unit>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        links.forEachIndexed { i, (label, onClick) ->
            if (i > 0) Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClick) { Text(label) }
        }
    }
}

/** "Home", "Home or Car", "Home, Car or Desk". */
@Composable
internal fun joinNames(names: List<String>): String {
    if (names.size == 1) return names.single()
    return stringResource(R.string.names_or, names.dropLast(1).joinToString(", "), names.last())
}
