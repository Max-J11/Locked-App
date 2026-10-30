package com.max.locked.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** What the setup checklist shows. Each item is something Locked needs from the system. */
data class SetupStatus(
    val blockerOn: Boolean = true,
    val notificationAccess: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val nfcOn: Boolean = true,
) {
    val allDone: Boolean
        get() = blockerOn && notificationAccess && notificationsAllowed && batteryUnrestricted && nfcOn
}

/** Reads system settings Locked depends on, and builds intents to the pages that change them. */
object SystemStatus {

    fun setupStatus(context: Context) = SetupStatus(
        blockerOn = isBlockerEnabled(context),
        notificationAccess = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent(context)),
        notificationsAllowed = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        // On One UI, "Unrestricted" battery use is the same as being exempt from optimization.
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        nfcOn = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true,
    )

    fun isBlockerEnabled(context: Context): Boolean =
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == BlockerAccessibilityService::class.java.name
            }

    fun listenerComponent(context: Context) = ComponentName(context, NotificationBlockerService::class.java)

    /**
     * The Accessibility list (Locked is under Installed apps). Locked's own switch page needs a
     * system-only permission (OPEN_ACCESSIBILITY_DETAILS_SETTINGS), so it can't be opened directly.
     */
    fun blockerSettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Locked's own notification-access page, or the full list. */
    fun notificationAccessIntent(context: Context): Intent {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        return if (detail.resolveActivity(context.packageManager) != null) {
            detail
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    /** Locked's notification settings, where its notifications can be allowed. */
    fun appNotificationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Locked's App info page: Battery, and "Allow restricted settings" in the ⋮ menu. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun nfcSettingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS)
}
