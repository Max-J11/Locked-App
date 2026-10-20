package com.max.locked.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.max.locked.data.BrickRepository
import com.max.locked.domain.shouldDismissNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dismisses notifications from blocked apps while locked (when the mode has "Also hide
 * notifications" on), and sweeps existing ones whenever the policy changes.
 *
 * Dismiss only: nothing is saved, queued or re-posted. Reads the in-memory Policy only.
 * Logs package names, never notification content.
 */
@AndroidEntryPoint
class NotificationBlockerService : NotificationListenerService() {

    @Inject lateinit var repository: BrickRepository

    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "notification listener connected")
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            // StateFlow emits the current value first, so this also sweeps on connect
            // (e.g. after a reboot during a lock), then again whenever the policy changes.
            s.launch { repository.policy.collect { sweep() } }
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "notification listener disconnected")
        scope?.cancel()
        scope = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && dismissIfBlocked(sbn)) {
            Log.d(TAG, "dismissed notification from ${sbn.packageName}")
        }
    }

    private fun sweep() {
        val policy = repository.policy.value
        if (!policy.isActive(System.currentTimeMillis()) || !policy.blockNotifications) return

        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            Log.d(TAG, "sweep skipped: listener not ready ($e)")
            return
        } ?: return

        val dismissed = active.filter(::dismissIfBlocked).map { it.packageName }
        Log.d(TAG, "sweep: dismissed ${dismissed.size} notification(s) ${dismissed.distinct()}")
    }

    private fun dismissIfBlocked(sbn: StatusBarNotification): Boolean {
        val dismiss = shouldDismissNotification(
            policy = repository.policy.value,
            packageName = sbn.packageName,
            isOngoing = sbn.isOngoing || !sbn.isClearable,
            now = System.currentTimeMillis(),
        )
        if (dismiss) cancelNotification(sbn.key)
        return dismiss
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Locked"
    }
}
