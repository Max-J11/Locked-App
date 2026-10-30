package com.max.locked.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.canTurnOffStrictMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Simple app settings kept in DataStore. Changes that would loosen a lock go through [BrickRepository]'s state. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brickRepository: BrickRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    /** In memory, so the blocker can read it on every screen change without touching storage. */
    val strictMode: StateFlow<Boolean> = context.settingsStore.data
        .map { it[STRICT_MODE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Turning on is always allowed; turning off is refused while locked. Returns false if refused. */
    suspend fun setStrictMode(on: Boolean): Boolean {
        if (!on) {
            val (p, now) = brickRepository.storedPolicy()
            if (!canTurnOffStrictMode(p, now)) {
                Log.d(TAG, "refused to turn off strict mode while locked")
                return false
            }
        }
        context.settingsStore.edit { it[STRICT_MODE] = on }
        Log.d(TAG, "strict mode ${if (on) "on" else "off"}")
        return true
    }

    private companion object {
        const val TAG = "Locked"
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
    }
}
