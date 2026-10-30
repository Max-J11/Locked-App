package com.max.locked.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.di.ApplicationScope
import com.max.locked.service.SystemStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a calm reminder while something is locked but the blocker (accessibility service) is off,
 * and removes it once the blocker is back on or nothing is locked.
 *
 * Checks whenever Locked is running and the blocker is switched on or off, or a lock starts or
 * ends; and every 15 minutes from [HealthCheckWorker], which also runs after a reboot. After a
 * force stop nothing of Locked's runs until it's opened again, so that case is caught on the
 * next open.
 */
@Singleton
class HealthChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()
    private var started = false

    /** Called once from [com.max.locked.LockedApp.onCreate]. */
    fun start() {
        if (started) return
        started = true
        createChannel()
        context.getSystemService(AccessibilityManager::class.java)
            .addAccessibilityServicesStateChangeListener(context.mainExecutor) {
                scope.launch { check("accessibility changed") }
            }
        scope.launch {
            repository.policy
                .map { it.isActive(System.currentTimeMillis()) }
                .distinctUntilChanged()
                .collect { check("lock changed") }
        }
        HealthCheckWorker.schedule(context)
    }

    suspend fun check(reason: String) = mutex.withLock {
        val locked = repository.isLockedNow()
        val blockerOn = SystemStatus.isBlockerEnabled(context)
        Log.d(TAG, "health check ($reason): locked=$locked blocker=$blockerOn")
        if (locked && !blockerOn) showReminder() else notifications.cancel(NOTIFICATION_ID)
    }

    private fun showReminder() {
        if (!notifications.areNotificationsEnabled()) {
            Log.d(TAG, "blocker is off but Locked can't post notifications")
            return
        }
        // Already showing: don't alert again.
        if (notifications.activeNotifications.any { it.id == NOTIFICATION_ID }) return

        val open = PendingIntent.getActivity(
            context,
            0,
            SystemStatus.blockerSettingsIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.health_title))
            .setContentText(context.getString(R.string.health_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "blocker reminder shown")
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.health_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.health_channel_description) }
        notifications.createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "Locked"
        const val CHANNEL_ID = "blocker_health"
        const val NOTIFICATION_ID = 1
    }
}
