package com.max.locked.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.DEFAULT_PASSES_PER_LOCK
import com.max.locked.domain.canSetPassCap
import com.max.locked.domain.canTurnOffStrictMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Simple app settings kept in DataStore. Changes that would loosen a lock are refused while locked. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brickRepository: BrickRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    /** In memory, so the blocker can read it on every screen change without touching storage. */
    val strictMode: StateFlow<Boolean> = context.settingsStore.data
        .map { it[SettingsKeys.STRICT_MODE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Passes per lock. */
    val passCap: StateFlow<Int> = context.settingsStore.data
        .map { it.passCap() }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_PASSES_PER_LOCK)

    /** Turning on is always allowed; turning off is refused while locked. Returns false if refused. */
    suspend fun setStrictMode(on: Boolean): Boolean {
        if (!on) {
            val (p, now) = brickRepository.storedPolicy()
            if (!canTurnOffStrictMode(p, now)) {
                Log.d(TAG, "refused to turn off strict mode while locked")
                return false
            }
        }
        context.settingsStore.edit { it[SettingsKeys.STRICT_MODE] = on }
        Log.d(TAG, "strict mode ${if (on) "on" else "off"}")
        return true
    }

    /** Any number 0 to 5 while unlocked; only lower while locked. Returns false if refused. */
    suspend fun setPassCap(count: Int): Boolean {
        val (p, now) = brickRepository.storedPolicy()
        val current = context.settingsStore.data.first().passCap()
        if (!canSetPassCap(p, now, current, count)) {
            Log.d(TAG, "refused passes per lock $current -> $count")
            return false
        }
        context.settingsStore.edit { it[SettingsKeys.PASS_CAP] = count }
        Log.d(TAG, "passes per lock $current -> $count")
        return true
    }

    private companion object {
        const val TAG = "Locked"
    }
}
