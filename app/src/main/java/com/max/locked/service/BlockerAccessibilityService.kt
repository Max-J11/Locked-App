package com.max.locked.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Watches which app is in the foreground and sends the user home if it's blocked.
 *
 * Milestone 2: the blocked package is hard-coded. Milestone 3 replaces it with the
 * in-memory Policy StateFlow from BrickRepository.
 *
 * Known One UI quirk: if a blocked app is opened in pop-up view, HOME minimizes it to a
 * floating icon (systemui freeform_container_pointer_control_view) instead of closing it.
 * The icon has no dismiss action; tapping it reopens the app, which gets blocked again.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var overlay: BlockOverlay

    private var lastForegroundPackage: String? = null
    private var lastBlockAtMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = BlockOverlay(this)
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> event.packageName?.toString()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> activeAppPackage()
            else -> null
        } ?: return

        // Our own overlay and screens should never trigger a block.
        if (pkg == packageName) return

        if (pkg != lastForegroundPackage) {
            Log.d(TAG, "foreground: $pkg (${AccessibilityEvent.eventTypeToString(event.eventType)})")
            lastForegroundPackage = pkg
        }

        if (pkg != BLOCKED_PACKAGE) return

        // Blocked apps can send a late state event after we've already sent the user home.
        // Pressing HOME again on One UI jumps to the main home page, so confirm the app
        // really is in front. If we can't tell (null), block anyway.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val active = activeAppPackage()
            if (active != null && active != pkg) {
                Log.d(TAG, "ignored stale event from $pkg (active: $active)")
                return
            }
        }

        block(pkg)
    }

    /** Package of the active app window, or the active window as a fallback. */
    private fun activeAppPackage(): String? {
        val appWindow = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused)
        }
        return appWindow?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
    }

    private fun block(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        // One app launch fires a burst of events. Only act once per burst.
        if (now - lastBlockAtMs < DEBOUNCE_MS) return
        lastBlockAtMs = now

        Log.d(TAG, "blocked $pkg")
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlay.show()
    }

    override fun onInterrupt() {
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "service destroyed")
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Locked"
        private const val DEBOUNCE_MS = 750L

        // Temporary: replaced by the Policy in Milestone 3.
        const val BLOCKED_PACKAGE = "com.google.android.youtube"
    }
}
