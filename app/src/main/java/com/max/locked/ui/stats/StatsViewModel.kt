package com.max.locked.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.HistoryRepository
import com.max.locked.domain.LockedSpan
import com.max.locked.domain.lastDays
import com.max.locked.domain.lockedMillisBetween
import com.max.locked.domain.lockedMillisPerDay
import com.max.locked.domain.locksStartedBetween
import com.max.locked.domain.startOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DayBar(val day: LocalDate, val millis: Long)

data class StepIn(val label: String, val count: Int)

data class StatsUiState(
    val loaded: Boolean = false,
    val hasHistory: Boolean = false,
    /** The last 7 days, oldest first, ending today. */
    val days: List<DayBar> = emptyList(),
    val weekMillis: Long = 0,
    val weekLocks: Int = 0,
    val weekPasses: Int = 0,
    val monthMillis: Long = 0,
    val allTimeMillis: Long = 0,
    val weekStepIns: Int = 0,
    val topStepIns: List<StepIn> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: HistoryRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val weekStart = startOfDay(LocalDate.now(zone).minusDays(DAYS - 1L), zone)

    /** Re-counts every minute, so a lock that's running keeps adding up while the screen is open. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val uiState: StateFlow<StatsUiState> =
        combine(history.sessions, history.blockCountsSince(weekStart), clock) { sessions, counts, now ->
            val spans = sessions.map { LockedSpan(it.startedAt, it.endedAt) }
            val today = LocalDate.now(zone)
            val days = lastDays(today, DAYS)
            val weekFrom = startOfDay(days.first(), zone)
            StatsUiState(
                loaded = true,
                hasHistory = sessions.isNotEmpty() || counts.isNotEmpty(),
                days = days.zip(lockedMillisPerDay(spans, days, zone, now)) { d, ms -> DayBar(d, ms) },
                weekMillis = lockedMillisBetween(spans, weekFrom, now, now),
                weekLocks = locksStartedBetween(spans, weekFrom, now + 1),
                weekPasses = sessions.filter { it.startedAt >= weekFrom }.sumOf { it.passesUsed },
                monthMillis = lockedMillisBetween(spans, startOfDay(today.withDayOfMonth(1), zone), now, now),
                allTimeMillis = lockedMillisBetween(spans, 0, now, now),
                weekStepIns = counts.sumOf { it.count },
                topStepIns = counts.take(TOP_COUNT).map {
                    StepIn(if (it.isSite) it.target else appLabel(it.target), it.count)
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    private val labels = mutableMapOf<String, String>()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg // Uninstalled since.
        }
    }

    private companion object {
        const val DAYS = 7
        const val TOP_COUNT = 5
    }
}
