package com.max.locked.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.Policy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Watches which app is in the foreground and sends the user home if the current [Policy] blocks it.
 * Reads the in-memory policy only; never touches Room per event. Also re-checks the app on screen
 * whenever the policy changes, so a schedule starting while a blocked app is open still blocks it.
 *
 * Known One UI quirk: if a blocked app is opened in pop-up view, HOME minimizes it to a
 * floating icon (systemui freeform_container_pointer_control_view) instead of closing it.
 * The icon has no dismiss action; tapping it reopens the app, which gets blocked again.
 */
@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: BrickRepository

    private lateinit var overlay: BlockOverlay

    private var lastForegroundPackage: String? = null
    private var lastBlockAtMs = 0L

    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = BlockOverlay(this)
        Log.d(TAG, "service connected")
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { repository.policy.drop(1).collect { recheckForeground() } }
        }
    }

    /** The policy changed (e.g. a schedule started): block the app already on screen if needed. */
    private fun recheckForeground() {
        val pkg = activeAppPackage() ?: return
        if (pkg == packageName) return
        if (repository.currentPolicy().isBlocked(pkg, System.currentTimeMillis())) {
            Log.d(TAG, "policy changed while $pkg was open")
            block(pkg)
        }
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

        if (!repository.currentPolicy().isBlocked(pkg, System.currentTimeMillis())) return

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
        val passesLeft = repository.currentPolicy().passesLeft
        overlay.show(appLabel(pkg), passesLeft, onUsePass = { usePassAndReopen(pkg) })
    }

    /** Uses a pass, waits until the policy shows it (so the app isn't blocked again), then reopens the app. */
    private fun usePassAndReopen(pkg: String) {
        scope?.launch {
            if (!repository.usePass(pkg)) return@launch
            withTimeoutOrNull(PASS_READY_TIMEOUT_MS) {
                repository.policy.first { it.hasPass(pkg, System.currentTimeMillis()) }
            }
            val launch = packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch == null) {
                Log.d(TAG, "pass used but $pkg has no launch intent")
                return@launch
            }
            try {
                startActivity(launch)
            } catch (e: Exception) {
                Log.d(TAG, "couldn't reopen $pkg after pass: $e")
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        getString(R.string.overlay_fallback_app_name)
    }

    override fun onInterrupt() {
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "service destroyed")
        scope?.cancel()
        scope = null
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Locked"
        private const val DEBOUNCE_MS = 750L
        private const val PASS_READY_TIMEOUT_MS = 2_000L
    }
}
