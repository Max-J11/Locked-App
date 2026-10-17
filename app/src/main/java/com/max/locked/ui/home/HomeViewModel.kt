package com.max.locked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLocked: Boolean = false,
    val appCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.policy, repository.selectedApps) { policy, apps ->
            HomeUiState(isLocked = policy.isActive(System.currentTimeMillis()), appCount = apps.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Debug stand-in for the puck. Replaced by TapActivity in Milestone 4. */
    fun toggleLock() {
        viewModelScope.launch {
            if (repository.policy.value.isActive(System.currentTimeMillis())) {
                repository.unlock(source = "debug_button")
            } else {
                repository.lock(source = "debug_button")
            }
        }
    }
}
