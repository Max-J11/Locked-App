package com.max.locked

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import com.max.locked.service.SetupStatus
import com.max.locked.service.SystemStatus
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.max.locked.ui.apppicker.AppPickerScreen
import com.max.locked.ui.apppicker.AppPickerViewModel
import com.max.locked.ui.modes.ModesScreen
import com.max.locked.ui.schedules.ScheduleEditScreen
import com.max.locked.ui.schedules.ScheduleEditViewModel
import com.max.locked.ui.schedules.SchedulesScreen
import com.max.locked.ui.home.HomeScreen
import com.max.locked.ui.pairing.PairingScreen
import com.max.locked.ui.pucks.PucksScreen
import com.max.locked.ui.settings.SettingsScreen
import com.max.locked.ui.sites.SitesScreen
import com.max.locked.ui.sites.SitesViewModel
import com.max.locked.ui.theme.LockedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Starts as "all done" so the checklist doesn't flash before the first real read. */
    private val setupStatus = mutableStateOf(SetupStatus())

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
                                setup = setupStatus.value,
                                onOpenSettings = ::openSettings,
                                onModes = { nav.navigate(ROUTE_MODES) },
                                onSchedules = { nav.navigate(ROUTE_SCHEDULES) },
                                onPucks = { nav.navigate(ROUTE_PUCKS) },
                                onSettings = { nav.navigate(ROUTE_SETTINGS) },
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
                        AppPickerScreen(
                            onBack = { nav.popBackStack() },
                            onOpenSites = {
                                nav.navigate("$ROUTE_SITES/${it.arguments?.getLong(AppPickerViewModel.ARG_MODE_ID)}")
                            },
                        )
                    }
                    composable(
                        route = "$ROUTE_SITES/{${SitesViewModel.ARG_MODE_ID}}",
                        arguments = listOf(navArgument(SitesViewModel.ARG_MODE_ID) { type = NavType.LongType }),
                    ) {
                        SitesScreen(onBack = { nav.popBackStack() })
                    }
                    composable(ROUTE_SCHEDULES) {
                        SchedulesScreen(
                            onBack = { nav.popBackStack() },
                            onOpen = { id -> nav.navigate("$ROUTE_SCHEDULE/$id") },
                            onAdd = { nav.navigate("$ROUTE_SCHEDULE/0") },
                        )
                    }
                    composable(
                        route = "$ROUTE_SCHEDULE/{${ScheduleEditViewModel.ARG_SCHEDULE_ID}}",
                        arguments = listOf(
                            navArgument(ScheduleEditViewModel.ARG_SCHEDULE_ID) { type = NavType.LongType },
                        ),
                    ) {
                        ScheduleEditScreen(onBack = { nav.popBackStack() })
                    }
                    composable(ROUTE_PUCKS) {
                        PucksScreen(
                            onBack = { nav.popBackStack() },
                            onPair = { nav.navigate(ROUTE_PAIR) },
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(onBack = { nav.popBackStack() })
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
        // Settings are changed outside the app, so re-read them each time Locked comes back.
        setupStatus.value = SystemStatus.setupStatus(this)
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Missing page, or one Settings refuses to open for us (SecurityException).
            Log.d(TAG, "couldn't open ${intent.action}: $e")
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private companion object {
        const val TAG = "Locked"
        const val ROUTE_HOME = "home"
        const val ROUTE_MODES = "modes"
        const val ROUTE_MODE = "mode"
        const val ROUTE_SITES = "sites"
        const val ROUTE_SCHEDULES = "schedules"
        const val ROUTE_SCHEDULE = "schedule"
        const val ROUTE_PUCKS = "pucks"
        const val ROUTE_PAIR = "pair"
        const val ROUTE_SETTINGS = "settings"
    }
}
