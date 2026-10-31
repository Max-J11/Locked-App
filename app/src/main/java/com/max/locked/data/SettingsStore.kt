package com.max.locked.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.max.locked.domain.DEFAULT_PASSES_PER_LOCK
import com.max.locked.domain.MAX_PASSES_PER_LOCK

/**
 * The one DataStore for simple settings. Shared by [SettingsRepository] (which changes them) and
 * [BrickRepository] (which needs passes per lock for the policy), so neither depends on the other.
 */
internal val Context.settingsStore by preferencesDataStore(name = "settings")

internal object SettingsKeys {
    val STRICT_MODE = booleanPreferencesKey("strict_mode")
    val PASS_CAP = intPreferencesKey("pass_cap")
}

internal fun Preferences.passCap(): Int =
    (this[SettingsKeys.PASS_CAP] ?: DEFAULT_PASSES_PER_LOCK).coerceIn(0, MAX_PASSES_PER_LOCK)
