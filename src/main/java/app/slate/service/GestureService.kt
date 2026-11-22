package app.slate.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.min

/**
 * Full-screen gestures for HyperOS, where gestures only work with Xiaomi's own launcher.
 * Thin invisible strips at the screen edges turn swipes into Home / Recents / Back.
 * It performs only those three system actions and never reads screen content.
 */
class GestureService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val views = mutableListOf<View>()
    private var arrow: BackArrow? = null
    private val d get() = resources.displayMetrics.density

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        attach()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        attach()
    }

    private fun overlay(w: Int, h: Int, gravity: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun attach() {
        detach()
        val screenH = resources.displayMetrics.heightPixels
        val edgeW = (18 * d).toInt()
        val sideH = (screenH * 0.68f).toInt()

        // Bottom edge: swipe up → Home, swipe up and pause → Recents.
        add(View(this).apply { setOnTouchListener(BottomSwipe()) },
            overlay(WindowManager.LayoutParams.MATCH_PARENT, (18 * d).toInt(), Gravity.BOTTOM))
        // Left and right edges: swipe inward → Back.
        add(View(this).apply { setOnTouchListener(SideSwipe(fromLeft = true)) },
            overlay(edgeW, sideH, Gravity.START or Gravity.CENTER_VERTICAL))
        add(View(this).apply { setOnTouchListener(SideSwipe(fromLeft = false)) },
            overlay(edgeW, sideH, Gravity.END or Gravity.CENTER_VERTICAL))
        // The back arrow that follows your finger (not touchable).
        val a = BackArrow(this)
        add(a, overlay((72 * d).toInt(), (72 * d).toInt(), Gravity.TOP or Gravity.START, touchable = false))
        a.visibility = View.GONE
        arrow = a
    }

    private fun add(v: View, lp: WindowManager.LayoutParams) {
        if (runCatching { wm.addView(v, lp) }.isSuccess) views += v
    }

    private fun detach() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        arrow = null
    }

    /** Short swipes and quick flicks both count; a pause at the top means Recents. */
    private inner class BottomSwipe : View.OnTouchListener {
        var startY = 0f; var lastMoveAt = 0L; var lastY = 0f
        var vt: VelocityTracker? = null
        var fired = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY; lastY = e.rawY; lastMoveAt = SystemClock.uptimeMillis(); fired = false
                    vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    if (abs(e.rawY - lastY) > 0.5f * d) { lastMoveAt = SystemClock.uptimeMillis(); lastY = e.rawY }
                    // A quick upward swipe goes Home right away, without waiting for lift-off.
                    if (!fired) {
                        vt?.computeCurrentVelocity(1000)
                        val vy = -(vt?.yVelocity ?: 0f) / d
                        if ((startY - e.rawY) / d > 36 && vy > 800) { fired = true; performGlobalAction(GLOBAL_ACTION_HOME) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (fired) { vt?.recycle(); vt = null; return true }
                    vt?.addMovement(e); vt?.computeCurrentVelocity(1000)
                    val vy = -(vt?.yVelocity ?: 0f) / d          // dp/s, upward positive
                    val dy = (startY - e.rawY) / d
                    // Recents only when the finger has really stopped: nearly no speed at lift-off
                    // AND no movement for a moment. A slow swipe still means Home.
                    val still = abs(vy) < 120 && SystemClock.uptimeMillis() - lastMoveAt > 250
                    when {
                        dy > 60 && still -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                        dy > 28 || (dy > 12 && vy > 700) -> performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    vt?.recycle(); vt = null
                }
                MotionEvent.ACTION_CANCEL -> { vt?.recycle(); vt = null }
            }
            return true
        }
    }

    private inner class SideSwipe(val fromLeft: Boolean) : View.OnTouchListener {
        var startX = 0f
        var vt: VelocityTracker? = null
        var fired = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val a = arrow
            val dx = if (fromLeft) e.rawX - startX else startX - e.rawX
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; fired = false; a?.show(fromLeft, e.rawY, 0f)
                    vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    if (fired) return true
                    a?.show(fromLeft, e.rawY, (dx / (60 * d)).coerceIn(0f, 1f))
                    // Far enough and still moving inward → Back now, not on lift-off.
                    vt?.computeCurrentVelocity(1000)
                    val vx = (vt?.xVelocity ?: 0f) / d * (if (fromLeft) 1 else -1)
                    if (dx / d > 44 && vx > 250) { fired = true; a?.hide(); performGlobalAction(GLOBAL_ACTION_BACK) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    a?.hide()
                    vt?.addMovement(e); vt?.computeCurrentVelocity(1000)
                    val vx = (vt?.xVelocity ?: 0f) / d * (if (fromLeft) 1 else -1)
                    val dxDp = dx / d
                    if (!fired && e.actionMasked == MotionEvent.ACTION_UP && (dxDp > 36 || (dxDp > 14 && vx > 600))) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    vt?.recycle(); vt = null
                }
            }
            return true
        }
    }

    /** A soft circle with a chevron, sliding in from the edge as you drag. */
    private inner class BackArrow(context: Context) : View(context) {
        private var progress = 0f
        private var fromLeft = true
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 28, 28, 32) }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.6f * d; strokeCap = Paint.Cap.ROUND
        }

        fun show(left: Boolean, y: Float, p: Float) {
            fromLeft = left; progress = p
            visibility = VISIBLE
            val lp = layoutParams as? WindowManager.LayoutParams ?: return
            val size = lp.width
            val screenW = resources.displayMetrics.widthPixels
            lp.y = (y - size / 2).toInt()
            val travel = 34 * d * p
            lp.x = if (left) (travel - size / 2).toInt() else (screenW - size / 2 - travel).toInt()
            runCatching { wm.updateViewLayout(this, lp) }
            alpha = min(1f, p * 1.6f)
            invalidate()
        }

        fun hide() { visibility = GONE }

        override fun onDraw(c: Canvas) {
            val r = width / 2f * (0.55f + 0.25f * progress)
            val cx = width / 2f; val cy = height / 2f
            c.drawCircle(cx, cy, r, disc)
            val s = r * 0.32f
            val dir = if (fromLeft) 1 else -1
            c.drawLine(cx + dir * s * 0.5f, cy - s, cx - dir * s * 0.5f, cy, line)
            c.drawLine(cx - dir * s * 0.5f, cy, cx + dir * s * 0.5f, cy + s, line)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { detach(); super.onDestroy() }

    companion object {
        /** Enabled in settings AND actually bound by the system right now. */
        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            val me = ComponentName(context, GestureService::class.java)
            return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo?.serviceInfo?.let { si -> ComponentName(si.packageName, si.name) } == me }
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GestureService::class.java)
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
