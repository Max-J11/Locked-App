package com.max.locked

import android.app.Application
import com.max.locked.data.HistoryRepository
import com.max.locked.schedule.HealthChecker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LockedApp : Application() {

    @Inject lateinit var healthChecker: HealthChecker
    @Inject lateinit var history: HistoryRepository

    override fun onCreate() {
        super.onCreate()
        healthChecker.start()
        history.start()
    }
}
