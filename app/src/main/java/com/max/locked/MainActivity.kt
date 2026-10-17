package com.max.locked

import android.accessibilityservice.AccessibilityServiceInfo
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
import com.max.locked.ui.apppicker.AppPickerScreen
import com.max.locked.ui.home.HomeScreen
import com.max.locked.ui.theme.LockedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)

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
                                onOpenAccessibilitySettings = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                onChooseApps = { nav.navigate(ROUTE_APPS) },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                    composable(ROUTE_APPS) {
                        AppPickerScreen(onBack = { nav.popBackStack() })
                    }
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

    private companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_APPS = "apps"
    }
}
