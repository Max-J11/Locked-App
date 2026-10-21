package com.max.locked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLocked: Boolean = false,
    /** Name of the mode in use while locked. */
    val activeModeName: String? = null,
    val modeCount: Int = 0,
    val puckCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.policy, repository.modes, repository.pairedKeys) { policy, modes, keys ->
            val locked = policy.isActive(System.currentTimeMillis())
            HomeUiState(
                isLocked = locked,
                activeModeName = if (locked) modes.firstOrNull { it.id == policy.modeId }?.name else null,
                modeCount = modes.size,
                puckCount = keys.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
