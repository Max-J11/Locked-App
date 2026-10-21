package com.max.locked.ui.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModesUiState(
    val modes: List<ModeSummary> = emptyList(),
    /** The mode a lock is using right now, if any. */
    val activeModeId: Long? = null,
)

@HiltViewModel
class ModesViewModel @Inject constructor(
    private val repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<ModesUiState> =
        combine(repository.modes, repository.policy) { modes, policy ->
            ModesUiState(
                modes = modes,
                activeModeId = policy.modeId.takeIf { policy.isActive(System.currentTimeMillis()) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModesUiState())

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createMode(name)) }
    }
}
