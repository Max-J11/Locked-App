package com.max.locked.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.max.locked.MainActivity
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.ModeSummary
import com.max.locked.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws the home-screen widget from the policy. Display only: nothing here decides whether
 * anything is locked.
 */
@Singleton
class StatusWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val widgetManager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, StatusWidget::class.java)

    /** Called once from [com.max.locked.LockedApp]: redraws whenever the lock or mode names change. */
    fun start() {
        scope.launch {
            repository.awaitLoaded()
            combine(repository.policy, repository.modes) { _, modes -> modes }.collect { render(it) }
        }
    }

    /** A widget was added, or a timer or schedule is due to end: redraw now. */
    fun refresh() {
        scope.launch {
            repository.awaitLoaded()
            render(repository.modes.first())
        }
    }

    private fun render(modes: List<ModeSummary>) {
        val ids = widgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = repository.currentPolicy(now)
        val views = RemoteViews(context.packageName, R.layout.widget_status)

        if (p.isActive(now)) {
            val since = p.lockedSince ?: now
            val base = SystemClock.elapsedRealtime() - (now - since)
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_locked)
            views.setViewVisibility(R.id.widget_title, View.GONE)
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            views.setChronometer(R.id.widget_timer, base, context.getString(R.string.widget_locked_for), true)

            val mode = modes.firstOrNull { it.id == p.modeId }?.name
            val end = listOfNotNull(p.endsAt, p.cooldownEndsAt).minOrNull()
            val detail = when {
                mode != null && end != null -> context.getString(R.string.widget_mode_until, mode, formatWhen(end))
                mode != null -> mode
                end != null -> context.getString(R.string.home_until, formatWhen(end))
                else -> ""
            }
            views.setTextViewText(R.id.widget_detail, detail)
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_unlocked)
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            views.setViewVisibility(R.id.widget_title, View.VISIBLE)
            views.setTextViewText(R.id.widget_detail, context.getString(R.string.home_hint_lock))
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        widgetManager.updateAppWidget(ids, views)
        scheduleNextRefresh(p.nextChangeAt(now))
        Log.d(TAG, "widget drawn (locked=${p.isActive(now)})")
    }

    /**
     * Wakes the widget when a timer, pass or schedule is due to change, in case Locked isn't
     * running then. Inexact (no special permission), and only redraws: locks never depend on it.
     */
    private fun scheduleNextRefresh(at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, StatusWidget::class.java).setAction(StatusWidget.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) alarms.cancel(intent) else alarms.set(AlarmManager.RTC, at, intent)
    }

    /** "5:00 PM", "Tomorrow, 9:00 AM" or "Sat, 9:00 AM". */
    private fun formatWhen(millis: Long): String {
        val time = DateFormat.getTimeFormat(context).format(Date(millis))
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (day) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.when_tomorrow, time)
            else -> DateUtils.formatDateTime(
                context, millis,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME,
            )
        }
    }

    private companion object {
        const val TAG = "Locked"
    }
}
