package com.max.locked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.data.PairedKeyEntity
import com.max.locked.domain.canStartCooldown
import com.max.locked.domain.lockingPuckId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLocked: Boolean = false,
    /** Name of the mode in use while locked. */
    val activeModeName: String? = null,
    /** A timed lock ends at this time. */
    val endsAt: Long? = null,
    /** "Unlock without puck" finishes at this time. */
    val cooldownEndsAt: Long? = null,
    /** "Unlock without puck" is offered: false once the lock would end sooner on its own. */
    val canStartCooldown: Boolean = false,
    /** Pucks that can end the current lock: the locking puck, or for a timed lock, pucks set to its mode. */
    val unlockPuckNames: List<String> = emptyList(),
    val modes: List<ModeSummary> = emptyList(),
    val pucks: List<PairedKeyEntity> = emptyList(),
) {
    val puckCount: Int get() = pucks.size
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: BrickRepository,
) : ViewModel() {

    /** Ticks while Home is visible, so time-based UI (like the cooldown button) updates on its own. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MILLIS)
        }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(repository.policy, repository.modes, repository.pairedKeys, clock) { policy, modes, keys, now ->
            val locked = policy.isActive(now)
            val lockingId = lockingPuckId(policy.source)
            val unlockers = when {
                !locked -> emptyList()
                lockingId != null -> keys.filter { it.id == lockingId }
                else -> keys.filter { it.modeId == policy.modeId }
            }
            HomeUiState(
                isLocked = locked,
                activeModeName = if (locked) modes.firstOrNull { it.id == policy.modeId }?.name else null,
                endsAt = if (locked) policy.endsAt else null,
                // Only worth showing if it beats the timer.
                cooldownEndsAt = policy.cooldownEndsAt
                    ?.takeIf { locked && (policy.endsAt == null || it < policy.endsAt) },
                canStartCooldown = canStartCooldown(locked, policy.endsAt, policy.cooldownEndsAt, now),
                unlockPuckNames = unlockers.map { it.name },
                modes = modes,
                pucks = keys,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun lockUntil(endsAt: Long, modeId: Long) {
        viewModelScope.launch { repository.lockUntil(endsAt, modeId) }
    }

    fun startCooldown() {
        viewModelScope.launch { repository.startCooldown() }
    }

    fun cancelCooldown() {
        viewModelScope.launch { repository.cancelCooldown() }
    }
}

private const val CLOCK_TICK_MILLIS = 15_000L
