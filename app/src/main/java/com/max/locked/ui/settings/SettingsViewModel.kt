package com.max.locked.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.SettingsRepository
import com.max.locked.domain.DEFAULT_PASSES_PER_LOCK
import com.max.locked.domain.canTurnOffStrictMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val strictMode: Boolean = false,
    /** Strict mode is on and something is locked, so it can't be switched off yet. */
    val strictModeLocked: Boolean = false,
    val passCap: Int = DEFAULT_PASSES_PER_LOCK,
    /** Something is locked, so passes per lock can only go down. */
    val passCapLocked: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.strictMode, settings.passCap, repository.policy) { strict, cap, policy ->
            val now = System.currentTimeMillis()
            SettingsUiState(
                strictMode = strict,
                strictModeLocked = strict && !canTurnOffStrictMode(policy, now),
                passCap = cap,
                passCapLocked = policy.isActive(now),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setStrictMode(on: Boolean) {
        viewModelScope.launch { settings.setStrictMode(on) }
    }

    fun setPassCap(count: Int) {
        viewModelScope.launch { settings.setPassCap(count) }
    }
}
