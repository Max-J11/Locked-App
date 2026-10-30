package com.max.locked.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Runs [HealthChecker.check] every 15 minutes (Android's shortest repeat), including after a
 * reboot, so a blocker that's off gets noticed even when nothing else of Locked is running.
 */
class HealthCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun healthChecker(): HealthChecker
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .healthChecker()
            .check("periodic")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "health_check"

        /** Safe to call on every start: an already scheduled check is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
