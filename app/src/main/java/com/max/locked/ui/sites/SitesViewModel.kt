package com.max.locked.ui.sites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.domain.isModeInUse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SitesUiState(
    val modeName: String = "",
    val sites: List<String> = emptyList(),
    /** The mode is in use: sites can be added but not removed. */
    val modeActive: Boolean = false,
    /** The last thing typed wasn't a website address. */
    val invalidInput: Boolean = false,
)

/** Edits one mode's blocked websites. */
@HiltViewModel
class SitesViewModel @Inject constructor(
    private val repository: BrickRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val modeId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_MODE_ID))

    private val invalid = MutableStateFlow(false)

    val uiState: StateFlow<SitesUiState> =
        combine(
            repository.mode(modeId),
            repository.sites(modeId),
            repository.policy,
            invalid,
        ) { mode, sites, policy, bad ->
            SitesUiState(
                modeName = mode?.name.orEmpty(),
                sites = sites,
                modeActive = isModeInUse(policy, modeId, System.currentTimeMillis()),
                invalidInput = bad,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitesUiState())

    /** Adds [input]; calls [onAdded] (to clear the field) if it was a website address. */
    fun add(input: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            val added = repository.addSite(modeId, input)
            invalid.value = added == null
            if (added != null) onAdded()
        }
    }

    fun clearInvalid() {
        invalid.value = false
    }

    fun remove(domain: String) {
        viewModelScope.launch { repository.removeSite(modeId, domain) }
    }

    companion object {
        const val ARG_MODE_ID = "modeId"
    }
}
