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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.max.locked.ui.common.LockedScaffold
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
import com.max.locked.ui.common.formatDuration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LockedScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
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
    StatsCard(Modifier.fillMaxWidth()) {
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

/** The same rounded, light card used across the app. */
@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    StatsCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How often Locked stepped in this week: the total as one big number, then the top apps and
 * sites, each with a thin bar showing how it compares to the most-stopped one.
 */
@Composable
private fun StepInsCard(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    StatsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.stats_step_ins_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.weekStepIns.toString(), style = MaterialTheme.typography.displaySmall)
                Text(
                    pluralStringResource(R.plurals.stats_step_ins_label, state.weekStepIns),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }

            val max = state.topStepIns.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            state.topStepIns.forEach { item ->
                Column(Modifier.padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            item.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareBar(item.count.toFloat() / max)
                }
            }

            HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp), color = colors.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stats_passes_used),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(state.weekPasses.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A thin rounded bar filled to [fraction] of its width. */
@Composable
private fun ShareBar(fraction: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

@Composable
private fun dayName(day: LocalDate): String =
    if (day == LocalDate.now()) {
        stringResource(R.string.stats_today)
    } else {
        day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
