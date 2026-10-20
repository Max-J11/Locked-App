package com.max.locked.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    notificationAccess: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onChooseApps: () -> Unit,
    onPucks: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (state.isLocked) R.string.status_locked else R.string.status_unlocked),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when {
                    state.puckCount == 0 -> R.string.home_hint_pair
                    state.isLocked -> R.string.home_hint_unlock
                    else -> R.string.home_hint_lock
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = pluralStringResource(R.plurals.apps_selected, state.appCount, state.appCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onChooseApps) { Text(stringResource(R.string.choose_apps)) }

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
}
