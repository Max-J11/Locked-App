package com.max.locked.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps that can never be blocked, in any mode. No lock may stop her calling or texting for help.
 *
 * Defaults (launcher, dialer, SMS, keyboards, clock, settings, maps) are looked up on the phone, since
 * they can change. Known Samsung/Google packages are listed too as a backstop.
 */
@Singleton
class NeverBlockList @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Recomputed on each call, so a changed default app is picked up on the next policy update. */
    fun packages(): Set<String> {
        val result = FIXED.toMutableSet()
        result += context.packageName

        val pm = context.packageManager
        resolve(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(result::add)
        resolve(pm, Intent(AlarmClock.ACTION_SHOW_ALARMS))?.let(result::add)
        resolve(pm, Intent(Settings.ACTION_SETTINGS))?.let(result::add)
        resolve(pm, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))?.let(result::add)

        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(result::add)
        Telephony.Sms.getDefaultSmsPackage(context)?.let(result::add)

        // Every enabled keyboard, so switching keyboards (or voice typing) still works.
        context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.forEach { result += it.packageName }

        return result
    }

    private fun resolve(pm: PackageManager, intent: Intent): String? = try {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            // "android" is the app chooser, which means no default is set.
            ?.takeIf { it != "android" }
    } catch (e: Exception) {
        Log.d("Locked", "never-block resolve failed for ${intent.action}: $e")
        null
    }

    private companion object {
        val FIXED = setOf(
            // System
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // Calls
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
            "com.google.android.dialer",
            // Contacts
            "com.samsung.android.app.contacts",
            "com.google.android.contacts",
            // Messages
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            // Clock
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            // Maps
            "com.google.android.apps.maps",
            // Emergency info / SOS
            "com.android.emergency",
            "com.samsung.android.emergency",
            "com.google.android.apps.safetyhub",
            // Launchers and keyboards (also resolved above)
            "com.sec.android.app.launcher",
            "com.samsung.android.honeyboard",
        )
    }
}
