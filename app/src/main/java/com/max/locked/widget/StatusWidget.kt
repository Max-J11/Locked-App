package com.max.locked.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The home-screen status widget. [StatusWidgetUpdater] does the drawing. */
@AndroidEntryPoint
class StatusWidget : AppWidgetProvider() {

    @Inject lateinit var updater: StatusWidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updater.refresh()
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updater.refresh()
    }

    companion object {
        const val ACTION_REFRESH = "com.max.locked.widget.REFRESH"
    }
}
