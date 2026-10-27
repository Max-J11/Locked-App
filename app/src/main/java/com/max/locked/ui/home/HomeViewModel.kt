package com.max.locked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.data.PairedKeyEntity
import com.max.locked.domain.canStartCooldown
import com.max.locked.domain.canUnlockWithPuck
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
    /** Names of the modes in use while locked. */
    val activeModeName: String? = null,
    /** At least part of the lock comes from a schedule. */
    val fromSchedule: Boolean = false,
    val scheduleCount: Int = 0,
    val passesLeft: Int = 0,
    /** A pass is open until this time. */
    val passEndsAt: Long? = null,
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
        combine(
            repository.policy,
            repository.modes,
            repository.pairedKeys,
            repository.schedules,
            clock,
        ) { policy, modes, keys, schedules, now ->
            val locked = policy.isActive(now)
            val manual = policy.manual
            // Pucks that can end at least part of what's locked.
            val unlockers = if (!locked) emptyList() else keys.filter { key ->
                (manual != null && canUnlockWithPuck(manual.source, key.id, key.modeId, manual.modeId)) ||
                    policy.schedules.any { it.modeId == key.modeId }
            }
            val activeIds = policy.activeModeIds
            val endsAt = policy.endsAt
            HomeUiState(
                isLocked = locked,
                activeModeName = if (locked) {
                    modes.filter { it.id in activeIds }.joinToString(", ") { it.name }.ifEmpty { null }
                } else {
                    null
                },
                fromSchedule = locked && policy.schedules.isNotEmpty(),
                scheduleCount = schedules.size,
                passesLeft = if (locked) policy.passesLeft else 0,
                passEndsAt = if (locked) policy.passEndsAt?.takeIf { it > now } else null,
                endsAt = if (locked) endsAt else null,
                // Only worth showing if it beats the timer.
                cooldownEndsAt = policy.cooldownEndsAt
                    ?.takeIf { locked && (endsAt == null || it < endsAt) },
                canStartCooldown = canStartCooldown(locked, endsAt, policy.cooldownEndsAt, now),
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
