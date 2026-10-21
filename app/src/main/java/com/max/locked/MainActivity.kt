package com.max.locked

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.max.locked.service.BlockerAccessibilityService
import com.max.locked.service.NotificationBlockerService
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.max.locked.ui.apppicker.AppPickerScreen
import com.max.locked.ui.apppicker.AppPickerViewModel
import com.max.locked.ui.modes.ModesScreen
import com.max.locked.ui.home.HomeScreen
import com.max.locked.ui.pairing.PairingScreen
import com.max.locked.ui.pucks.PucksScreen
import com.max.locked.ui.theme.LockedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)
    private val notificationAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockedTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) {
                        Scaffold(Modifier.fillMaxSize()) { padding ->
                            HomeScreen(
                                serviceEnabled = serviceEnabled.value,
                                notificationAccess = notificationAccess.value,
                                onOpenAccessibilitySettings = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                onOpenNotificationSettings = ::openNotificationAccessSettings,
                                onModes = { nav.navigate(ROUTE_MODES) },
                                onPucks = { nav.navigate(ROUTE_PUCKS) },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                    composable(ROUTE_MODES) {
                        ModesScreen(
                            onBack = { nav.popBackStack() },
                            onOpenMode = { id -> nav.navigate("$ROUTE_MODE/$id") },
                        )
                    }
                    composable(
                        route = "$ROUTE_MODE/{${AppPickerViewModel.ARG_MODE_ID}}",
                        arguments = listOf(navArgument(AppPickerViewModel.ARG_MODE_ID) { type = NavType.LongType }),
                    ) {
                        AppPickerScreen(onBack = { nav.popBackStack() })
                    }
                    composable(ROUTE_PUCKS) {
                        PucksScreen(
                            onBack = { nav.popBackStack() },
                            onPair = { nav.navigate(ROUTE_PAIR) },
                        )
                    }
                    composable(ROUTE_PAIR) {
                        PairingScreen(onDone = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isBlockerEnabled()
        notificationAccess.value = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent)
    }

    private val listenerComponent: ComponentName
        get() = ComponentName(this, NotificationBlockerService::class.java)

    /** Opens Locked's own notification-access page, falling back to the full list. */
    private fun openNotificationAccessSettings() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        try {
            startActivity(detail)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun isBlockerEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == BlockerAccessibilityService::class.java.name
            }
    }

    private companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_MODES = "modes"
        const val ROUTE_MODE = "mode"
        const val ROUTE_PUCKS = "pucks"
        const val ROUTE_PAIR = "pair"
    }
}
