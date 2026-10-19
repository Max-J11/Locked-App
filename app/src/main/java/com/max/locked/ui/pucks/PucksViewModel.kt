package com.max.locked.ui.pucks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.PairedKeyEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PucksUiState(
    val pucks: List<PairedKeyEntity> = emptyList(),
    val isLocked: Boolean = false,
)

@HiltViewModel
class PucksViewModel @Inject constructor(
    private val repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<PucksUiState> =
        combine(repository.pairedKeys, repository.policy) { keys, policy ->
            PucksUiState(pucks = keys, isLocked = policy.isActive(System.currentTimeMillis()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PucksUiState())

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePairedKey(id) }
    }
}
