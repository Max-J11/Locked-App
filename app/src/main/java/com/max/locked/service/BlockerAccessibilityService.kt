package com.max.locked.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.HistoryRepository
import com.max.locked.data.Policy
import com.max.locked.data.SettingsRepository
import com.max.locked.domain.ProtectedScreen
import com.max.locked.domain.STRICT_MODE_PACKAGES
import com.max.locked.domain.BROWSER_URL_BARS
import com.max.locked.domain.ScreenNode
import com.max.locked.domain.hostOf
import com.max.locked.domain.protectedScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var history: HistoryRepository

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
            s.launch {
                combine(settings.strictMode, repository.policy) { strict, policy ->
                    val active = policy.isActive(System.currentTimeMillis())
                    (strict && active) || (active && policy.blockedSites.isNotEmpty())
                }.distinctUntilChanged().collect(::watchPageChanges)
            }
        }
    }

    /**
     * Page-change events let strict mode close a page the moment it fills in, and let site
     * blocking see a browser move to a new address. They're frequent, so they're only switched on
     * while strict mode is in effect or a lock includes websites.
     */
    private fun watchPageChanges(on: Boolean) {
        val info = serviceInfo ?: return
        info.eventTypes = if (on) BASE_EVENT_TYPES or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else BASE_EVENT_TYPES
        serviceInfo = info
        Log.d(TAG, "watching page changes: $on")
    }

    /** The policy changed (e.g. a schedule started): block the app or site already on screen if needed. */
    private fun recheckForeground() {
        val pkg = activeAppPackage() ?: return
        if (pkg == packageName) return
        if (repository.currentPolicy().isBlocked(pkg, System.currentTimeMillis())) {
            Log.d(TAG, "policy changed while $pkg was open")
            block(pkg)
        } else if (pkg in BROWSER_URL_BARS) {
            checkBrowser(pkg)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> event.packageName?.toString()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> activeAppPackage()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Only switched on for strict mode and site blocking; every other app returns here.
                val p = event.packageName?.toString() ?: return
                if (p in STRICT_MODE_PACKAGES) guardStrictMode(p)
                if (p in BROWSER_URL_BARS) scheduleBrowserCheck(p)
                return
            }
            else -> null
        } ?: return

        // Our own overlay and screens should never trigger a block.
        if (pkg == packageName) return

        if (pkg != lastForegroundPackage) {
            Log.d(TAG, "foreground: $pkg (${AccessibilityEvent.eventTypeToString(event.eventType)})")
            lastForegroundPackage = pkg
        }

        if (pkg in STRICT_MODE_PACKAGES) {
            guardStrictMode(pkg)
            return
        }

        if (!repository.currentPolicy().isBlocked(pkg, System.currentTimeMillis())) {
            if (pkg in BROWSER_URL_BARS) checkBrowser(pkg)
            return
        }

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
        history.recordBlock(pkg, isSite = false)
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

    /**
     * Uses a pass for [host], waits until the policy shows it, then reopens [address] in the same
     * browser. Falls back to the site's home page if the address can't be opened as it is.
     */
    private fun useSitePassAndReopen(pkg: String, host: String, address: String) {
        scope?.launch {
            if (!repository.useSitePass(host)) return@launch
            withTimeoutOrNull(PASS_READY_TIMEOUT_MS) {
                repository.policy.first { it.hasSitePass(host, System.currentTimeMillis()) }
            }
            val url = if ("://" in address) address else "https://$address"
            val opened = openInBrowser(pkg, url) || openInBrowser(pkg, "https://$host")
            Log.d(TAG, "site pass for $host, reopened=$opened")
        }
    }

    private fun openInBrowser(pkg: String, url: String): Boolean = try {
        startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        Log.d(TAG, "couldn't open $url in $pkg: $e")
        false
    }

    /** Re-checks the screen while Settings or the installer is in front. */
    private var strictWatch: Job? = null
    private var lastStrictScreen: ProtectedScreen? = null
    private var lastStrictCloseAtMs = 0L

    /**
     * Strict mode, while locked: closes Locked's App info page, its own Settings pages, the
     * "Turn off Locked?" dialog and the uninstall prompt. Events alone can arrive too late (a page
     * fills in after its window appears), so while Settings or the installer stays in front, the
     * screen is also re-checked every [STRICT_POLL_MS].
     */
    private fun guardStrictMode(pkg: String) {
        if (!strictModeActive()) return
        closeIfProtected(pkg)
        if (strictWatch?.isActive == true) return
        strictWatch = scope?.launch {
            while (true) {
                delay(STRICT_POLL_MS)
                if (!strictModeActive()) break
                val front = activeAppPackage() ?: continue
                if (front !in STRICT_MODE_PACKAGES) break
                closeIfProtected(front)
            }
        }
    }

    private fun strictModeActive(): Boolean =
        settings.strictMode.value && repository.currentPolicy().isActive(System.currentTimeMillis())

    private fun closeIfProtected(pkg: String): Boolean {
        val screen = protectedScreen(pkg, screenNodes(pkg), getString(R.string.app_name)) ?: return false
        val now = SystemClock.elapsedRealtime()
        // Skip only a repeat of the same screen while the last close is still taking effect;
        // a different one (the dialog after its page) is closed straight away.
        if (screen == lastStrictScreen && now - lastStrictCloseAtMs < STRICT_REPEAT_MS) return true
        lastStrictScreen = screen
        lastStrictCloseAtMs = now
        Log.d(TAG, "strict mode closed $screen")
        // Back, one screen at a time: the dialog, then Locked's page, then the Installed apps list
        // (each found by the next check). She stays in Settings, and reopening it doesn't land
        // back on a protected page.
        performGlobalAction(GLOBAL_ACTION_BACK)
        overlay.showMessage(getString(R.string.overlay_settings_message))
        return true
    }

    // --- Websites ---------------------------------------------------------------------------

    private var browserCheck: Job? = null
    private var lastSiteBlockAtMs = 0L

    /**
     * Browsers fire page-change events constantly while a page loads or scrolls. Check at most
     * once per [BROWSER_CHECK_MS], after the burst, rather than on every event.
     */
    private fun scheduleBrowserCheck(pkg: String) {
        if (browserCheck?.isActive == true) return
        browserCheck = scope?.launch {
            delay(BROWSER_CHECK_MS)
            checkBrowser(pkg)
        }
    }

    /**
     * What [pkg]'s address bar shows, e.g. "m.youtube.com/watch?v=1", or null while it's being
     * typed in or isn't on screen.
     */
    private fun browserAddress(pkg: String): String? {
        val barId = BROWSER_URL_BARS[pkg] ?: return null
        val root = windows.firstOrNull { (it.isActive || it.isFocused) && it.root?.packageName == pkg }?.root
            ?: return null
        val bar = root.findAccessibilityNodeInfosByViewId("$pkg:id/$barId").firstOrNull() ?: return null
        // While typing, the bar holds the search, not the page. Only a loaded page counts.
        if (bar.isFocused) return null
        return bar.text?.toString()
    }

    /** The site in [pkg]'s address bar, or null while it's being typed in or shows no address. */
    private fun browserHost(pkg: String): String? = browserAddress(pkg)?.let(::hostOf)

    /**
     * If the browser shows a blocked site: Back (to the previous page, or closing a new tab), and
     * the overlay. If the site is still showing a moment later, go home instead.
     */
    private fun checkBrowser(pkg: String) {
        val policy = repository.currentPolicy()
        val now = System.currentTimeMillis()
        if (!policy.isActive(now) || policy.blockedSites.isEmpty()) return
        if (activeAppPackage() != pkg) return
        // Read the whole address before Back, so a pass can reopen this exact page.
        val address = browserAddress(pkg) ?: return
        val host = hostOf(address) ?: return
        if (!policy.isSiteBlocked(host, now)) return

        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastSiteBlockAtMs < DEBOUNCE_MS) return
        lastSiteBlockAtMs = nowMs
        Log.d(TAG, "blocked site $host in $pkg")
        history.recordBlock(host, isSite = true)
        performGlobalAction(GLOBAL_ACTION_BACK)
        overlay.show(host, policy.passesLeft, onUsePass = { useSitePassAndReopen(pkg, host, address) })
        scope?.launch {
            delay(SITE_STILL_OPEN_MS)
            val still = activeAppPackage() == pkg && browserHost(pkg)?.let {
                repository.currentPolicy().isSiteBlocked(it, System.currentTimeMillis())
            } == true
            if (still) {
                Log.d(TAG, "site $host still open after Back, going home")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    /** View ids and text in the active window of [pkg]. Only called for Settings and the installer. */
    private fun screenNodes(pkg: String): List<ScreenNode> {
        val root = windows.firstOrNull { (it.isActive || it.isFocused) && it.root?.packageName == pkg }?.root
            ?: rootInActiveWindow
            ?: return emptyList()
        val nodes = mutableListOf<ScreenNode>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || nodes.size >= MAX_SCREEN_NODES || depth > MAX_SCREEN_DEPTH) return
            val id = node.viewIdResourceName?.substringAfter(":id/")
            val text = node.text?.toString()
            if (id != null || text != null) nodes += ScreenNode(id, text)
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return nodes
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
        private const val BROWSER_CHECK_MS = 200L
        private const val SITE_STILL_OPEN_MS = 1_500L
        private const val STRICT_POLL_MS = 150L
        private const val STRICT_REPEAT_MS = 400L
        /** Matches accessibility_config.xml. */
        private const val BASE_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        private const val MAX_SCREEN_NODES = 300
        private const val MAX_SCREEN_DEPTH = 40
    }
}
