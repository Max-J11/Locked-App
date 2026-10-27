package com.max.locked.ui.schedules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.data.ScheduleEntity
import com.max.locked.data.toMask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class ScheduleEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    /** The schedule as stored (or defaults for a new one), used to fill the form once. */
    val initial: ScheduleEntity? = null,
    val modes: List<ModeSummary> = emptyList(),
    /** Its window is running now, so it can't be changed or deleted. */
    val running: Boolean = false,
)

@HiltViewModel
class ScheduleEditViewModel @Inject constructor(
    private val repository: BrickRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val scheduleId: Long = savedStateHandle.get<Long>(ARG_SCHEDULE_ID) ?: 0L
    private val initial = MutableStateFlow<ScheduleEntity?>(null)

    val uiState: StateFlow<ScheduleEditUiState> =
        combine(initial, repository.modes, repository.policy) { schedule, modes, policy ->
            val now = System.currentTimeMillis()
            ScheduleEditUiState(
                loaded = schedule != null,
                isNew = scheduleId == 0L,
                initial = schedule,
                modes = modes,
                running = scheduleId != 0L && policy.isActive(now) && policy.schedules.any { it.scheduleId == scheduleId },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleEditUiState())

    init {
        viewModelScope.launch {
            initial.value = if (scheduleId == 0L) DEFAULT_SCHEDULE else repository.getSchedule(scheduleId) ?: DEFAULT_SCHEDULE
        }
    }

    fun save(days: Set<DayOfWeek>, startMinute: Int, endMinute: Int, modeId: Long, onDone: () -> Unit) {
        val base = initial.value ?: DEFAULT_SCHEDULE
        viewModelScope.launch {
            val saved = repository.saveSchedule(
                base.copy(
                    id = scheduleId,
                    days = days.toMask(),
                    startMinute = startMinute,
                    endMinute = endMinute,
                    modeId = modeId,
                ),
            )
            if (saved) onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { if (repository.deleteSchedule(scheduleId)) onDone() }
    }

    companion object {
        const val ARG_SCHEDULE_ID = "scheduleId"

        /** 10 PM to 7 AM, Default mode, no days picked yet. */
        private val DEFAULT_SCHEDULE = ScheduleEntity(
            modeId = BrickRepository.DEFAULT_MODE_ID,
            days = 0, // no days picked until chosen
            startMinute = 22 * 60,
            endMinute = 7 * 60,
        )
    }
}
