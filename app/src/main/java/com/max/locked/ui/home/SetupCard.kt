package com.max.locked.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.max.locked.R
import com.max.locked.service.SetupStatus
import com.max.locked.service.SystemStatus

/** Lists only what's still missing, each with a button to the Settings page that fixes it. */
@Composable
fun SetupCard(
    status: SetupStatus,
    onOpen: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items = buildList {
        if (!status.blockerOn) {
            add(
                SetupItem(
                    text = stringResource(R.string.setup_blocker),
                    hint = stringResource(R.string.setup_blocker_hint),
                    actions = listOf(
                        stringResource(R.string.setup_turn_on) to SystemStatus.blockerSettingsIntent(),
                        stringResource(R.string.setup_app_info) to SystemStatus.appInfoIntent(context),
                    ),
                ),
            )
        }
        if (!status.notificationsAllowed) {
            add(
                SetupItem(
                    text = stringResource(R.string.setup_notifications),
                    actions = listOf(stringResource(R.string.setup_allow) to SystemStatus.appNotificationsIntent(context)),
                ),
            )
        }
        if (!status.notificationAccess) {
            add(
                SetupItem(
                    text = stringResource(R.string.setup_notification_access),
                    actions = listOf(stringResource(R.string.setup_allow) to SystemStatus.notificationAccessIntent(context)),
                ),
            )
        }
        if (!status.batteryUnrestricted) {
            add(
                SetupItem(
                    text = stringResource(R.string.setup_battery),
                    actions = listOf(stringResource(R.string.setup_app_info) to SystemStatus.appInfoIntent(context)),
                ),
            )
        }
        if (!status.nfcOn) {
            add(
                SetupItem(
                    text = stringResource(R.string.setup_nfc),
                    actions = listOf(stringResource(R.string.setup_turn_on) to SystemStatus.nfcSettingsIntent()),
                ),
            )
        }
    }
    if (items.isEmpty()) return

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                item.hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.actions.forEach { (label, intent) ->
                        TextButton(onClick = { onOpen(intent) }) { Text(label) }
                    }
                }
            }
        }
    }
}

private class SetupItem(
    val text: String,
    val hint: String? = null,
    val actions: List<Pair<String, Intent>>,
)
