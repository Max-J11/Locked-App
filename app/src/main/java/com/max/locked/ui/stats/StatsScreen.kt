package com.max.locked.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max.locked.R
import com.max.locked.domain.hoursAndMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.loaded) return@Column
            if (!state.hasHistory) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }

            WeekCard(state)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile(stringResource(R.string.stats_this_month), formatDuration(state.monthMillis), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_all_time), formatDuration(state.allTimeMillis), Modifier.weight(1f))
            }

            StepInsCard(state)

            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.stats_clear)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.stats_clear_title)) },
            text = { Text(stringResource(R.string.stats_clear_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.stats_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WeekCard(state: StatsUiState) {
    // Today is shown until another day is tapped.
    var selected by rememberSaveable { mutableIntStateOf(state.days.lastIndex) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.stats_last_7_days), style = MaterialTheme.typography.labelLarge)
            Text(formatDuration(state.weekMillis), style = MaterialTheme.typography.displaySmall)
            Text(
                pluralStringResource(R.plurals.stats_locks, state.weekLocks, state.weekLocks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            state.days.getOrNull(selected)?.let { bar ->
                Text(
                    stringResource(R.string.stats_day_value, dayName(bar.day), formatDuration(bar.millis)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            WeekChart(state.days, selected, onSelect = { selected = it })
        }
    }
}

/** One bar per day, oldest first. Tap a bar to show its time above the chart. */
@Composable
private fun WeekChart(days: List<DayBar>, selected: Int, onSelect: (Int) -> Unit) {
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val max = days.maxOfOrNull { it.millis }?.coerceAtLeast(1) ?: 1
    val description = days.map { "${dayName(it.day)}: ${formatDuration(it.millis)}" }.joinToString()

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = description }
                .pointerInput(days.size) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / days.size)).toInt()
                        if (index in days.indices) onSelect(index)
                    }
                },
        ) {
            val slot = size.width / days.size
            val barWidth = slot * 0.5f
            val radius = 4.dp.toPx()
            days.forEachIndexed { i, bar ->
                if (bar.millis <= 0) return@forEachIndexed
                val h = (size.height * bar.millis / max).coerceAtLeast(radius * 2)
                val left = slot * i + (slot - barWidth) / 2
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = size.height - h,
                            right = left + barWidth,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                drawPath(path, if (i == selected) barColor else dimColor)
            }
            val line = 1.dp.toPx()
            drawRect(baseline, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, bar ->
                Text(
                    bar.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StepInsCard(state: StatsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_step_ins_title), style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.stats_step_ins, state.weekStepIns, state.weekStepIns),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.topStepIns.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(item.count.toString(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.weekPasses > 0) {
                Text(
                    pluralStringResource(R.plurals.stats_passes, state.weekPasses, state.weekPasses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun dayName(day: LocalDate): String =
    if (day == LocalDate.now()) {
        stringResource(R.string.stats_today)
    } else {
        day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

/** "4 h 10 m", or "25 m" under an hour. */
@Composable
fun formatDuration(millis: Long): String {
    val (h, m) = hoursAndMinutes(millis)
    return if (h > 0) stringResource(R.string.duration_hm, h, m) else stringResource(R.string.duration_m, m)
}
