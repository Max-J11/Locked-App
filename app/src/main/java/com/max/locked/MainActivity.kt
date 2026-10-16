package com.max.locked

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.max.locked.service.BlockerAccessibilityService
import com.max.locked.ui.theme.LockedTheme

class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StatusScreen(
                        serviceEnabled = serviceEnabled.value,
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isBlockerEnabled()
    }

    private fun isBlockerEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == BlockerAccessibilityService::class.java.name
            }
    }
}

@Composable
private fun StatusScreen(
    serviceEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (serviceEnabled) R.string.service_on else R.string.service_off),
            style = MaterialTheme.typography.headlineSmall,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.open_accessibility_settings))
        }
    }
}
