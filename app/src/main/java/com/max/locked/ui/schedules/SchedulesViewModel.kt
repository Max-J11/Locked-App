package com.max.locked.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.data.ScheduleEntity
import com.max.locked.data.toDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class ScheduleItem(
    val id: Long,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val modeName: String,
    val enabled: Boolean,
    /** Its window is running now, so it can't be changed or switched off. */
    val running: Boolean,
)

data class SchedulesUiState(
    val loading: Boolean = true,
    val schedules: List<ScheduleItem> = emptyList(),
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repository: BrickRepository,
) : ViewModel() {

    val uiState: StateFlow<SchedulesUiState> =
        combine(repository.schedules, repository.modes, repository.policy) { schedules, modes, policy ->
            val now = System.currentTimeMillis()
            val running = if (policy.isActive(now)) policy.schedules.map { it.scheduleId }.toSet() else emptySet()
            SchedulesUiState(
                loading = false,
                schedules = schedules.map { it.toItem(modes.firstOrNull { m -> m.id == it.modeId }?.name.orEmpty(), it.id in running) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesUiState())

    fun setEnabled(id: Long, on: Boolean) {
        viewModelScope.launch { repository.setScheduleEnabled(id, on) }
    }

    private fun ScheduleEntity.toItem(modeName: String, running: Boolean) = ScheduleItem(
        id = id,
        days = days.toDays(),
        startMinute = startMinute,
        endMinute = endMinute,
        modeName = modeName,
        enabled = enabled,
        running = running,
    )
}
