package com.max.locked.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.max.locked.R
import com.max.locked.domain.PASS_MILLIS

/**
 * A calm full-screen message shown after a blocked app is closed, with an optional
 * "Use a pass" button.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, so no "display over other apps" permission is needed.
 * Plain Views for now, because Compose inside a service needs extra lifecycle setup.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    private var view: View? = null

    /**
     * Shows the message for [appName]. If [passesLeft] > 0, also offers a pass; tapping it calls
     * [onUsePass] and hides the overlay. Stays up longer when a pass is offered, so there's time
     * to tap it.
     */
    fun show(appName: String, passesLeft: Int = 0, onUsePass: (() -> Unit)? = null) =
        showMessage(service.getString(R.string.overlay_message, appName), passesLeft, onUsePass)

    /** Same overlay with any [message], e.g. for strict mode closing a Settings page. */
    fun showMessage(message: String, passesLeft: Int = 0, onUsePass: (() -> Unit)? = null) {
        hide()
        val offerPass = passesLeft > 0 && onUsePass != null
        val v = buildView(message, if (offerPass) passesLeft else 0, onUsePass)
        try {
            windowManager.addView(v, layoutParams())
            view = v
            Log.d(TAG, "overlay shown (pass offered=$offerPass)")
        } catch (e: Exception) {
            Log.d(TAG, "overlay add failed: $e")
            return
        }
        handler.postDelayed(autoHide, if (offerPass) AUTO_HIDE_WITH_PASS_MS else AUTO_HIDE_MS)
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val v = view ?: return
        view = null
        try {
            windowManager.removeView(v)
            Log.d(TAG, "overlay hidden")
        } catch (e: Exception) {
            Log.d(TAG, "overlay remove failed: $e")
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun buildView(messageText: String, passesLeft: Int, onUsePass: (() -> Unit)?): View {
        val ctx = service
        fun dp(value: Int) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), ctx.resources.displayMetrics,
        ).toInt()

        val title = TextView(ctx).apply {
            setText(R.string.overlay_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }
        val message = TextView(ctx).apply {
            text = messageText
            setTextColor(Color.argb(220, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        }
        val ok = Button(ctx).apply {
            setText(R.string.overlay_ok)
            setOnClickListener { hide() }
        }

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), 0, dp(32), 0)
            addView(title)
            addView(message)
            addView(ok, LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT))
            if (passesLeft > 0 && onUsePass != null) {
                val minutes = (PASS_MILLIS / 60_000).toInt()
                val pass = Button(ctx).apply {
                    text = ctx.resources.getQuantityString(R.plurals.overlay_use_pass, passesLeft, minutes, passesLeft)
                    isAllCaps = false
                    setOnClickListener {
                        hide()
                        onUsePass()
                    }
                }
                addView(
                    pass,
                    LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                    },
                )
            }
        }

        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(240, 16, 26, 19))
            // Swallow taps so they don't fall through to the launcher.
            isClickable = true
            addView(
                column,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "Locked"
        const val AUTO_HIDE_MS = 3_000L
        const val AUTO_HIDE_WITH_PASS_MS = 6_000L
    }
}
