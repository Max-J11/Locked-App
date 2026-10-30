package com.max.locked

import android.app.Application
import com.max.locked.data.HistoryRepository
import com.max.locked.schedule.HealthChecker
import com.max.locked.widget.StatusWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LockedApp : Application() {

    @Inject lateinit var healthChecker: HealthChecker
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var widget: StatusWidgetUpdater

    override fun onCreate() {
        super.onCreate()
        healthChecker.start()
        history.start()
        widget.start()
    }
}
