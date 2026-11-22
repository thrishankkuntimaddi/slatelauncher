package app.slate.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
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

    /** The dock's Recents button lives in the launcher process; it asks us to open Recents. */
    private val requests = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                ACTION_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                ACTION_HOME_SHOWN -> removeClearAllCatcher()
            }
        }
    }
    private var listening = false

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        attach()
        if (!listening) {
            ContextCompat.registerReceiver(this, requests, IntentFilter(ACTION_RECENTS).apply { addAction(ACTION_HOME_SHOWN) }, ContextCompat.RECEIVER_NOT_EXPORTED)
            listening = true
        }
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

    /**
     * Swipe up → Home. Swipe up and hold still → Recents, opened while the finger is still down
     * (like iPadOS). "Still" tolerates normal finger jitter: the finger must stay within a few dp
     * for a moment, not be perfectly motionless.
     */
    private inner class BottomSwipe : View.OnTouchListener {
        var startY = 0f
        var anchorX = 0f; var anchorY = 0f; var anchorAt = 0L
        var vt: VelocityTracker? = null
        var fired = false
        val handler = Handler(Looper.getMainLooper())
        val hold = Runnable { checkHold() }

        fun checkHold() {
            if (fired) return
            val dy = (startY - anchorY) / d
            if (dy > HOLD_MIN_DP && SystemClock.uptimeMillis() - anchorAt >= HOLD_MS) {
                fired = true
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY; anchorX = e.rawX; anchorY = e.rawY; anchorAt = SystemClock.uptimeMillis(); fired = false
                    vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
                    handler.removeCallbacks(hold)
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    if (fired) return true
                    // Moved beyond the jitter allowance → restart the "holding still" clock.
                    if (abs(e.rawY - anchorY) > JITTER_DP * d || abs(e.rawX - anchorX) > JITTER_DP * d) {
                        anchorX = e.rawX; anchorY = e.rawY; anchorAt = SystemClock.uptimeMillis()
                        handler.removeCallbacks(hold)
                        if ((startY - e.rawY) / d > HOLD_MIN_DP) handler.postDelayed(hold, HOLD_MS)
                    }
                    // A quick upward flick goes Home right away, without waiting for lift-off.
                    vt?.computeCurrentVelocity(1000)
                    val vy = -(vt?.yVelocity ?: 0f) / d
                    if ((startY - e.rawY) / d > 36 && vy > 900) {
                        fired = true; handler.removeCallbacks(hold); performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(hold)
                    if (!fired) {
                        vt?.addMovement(e); vt?.computeCurrentVelocity(1000)
                        val vy = -(vt?.yVelocity ?: 0f) / d          // dp/s, upward positive
                        val dy = (startY - e.rawY) / d
                        val heldStill = SystemClock.uptimeMillis() - anchorAt >= HOLD_MS * 0.7f && abs(vy) < 200
                        when {
                            dy > HOLD_MIN_DP && heldStill -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                            dy > 28 || (dy > 12 && vy > 700) -> performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                    vt?.recycle(); vt = null
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(hold); vt?.recycle(); vt = null }
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

    // ── Which apps are still open: read off the system Recents screen ──────
    //
    // Android doesn't tell launchers when you clear an app from Recents. The Recents screen
    // itself shows it, so while Recents is open we read its card titles (nothing else) and save
    // them; the dock only shows recent apps that are still there.

    private val tasksHandler = Handler(Looper.getMainLooper())
    private val snapshot = Runnable { readRecents() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val id = runCatching { event.source?.viewIdResourceName }.getOrNull().orEmpty()
            val txt = event.text.joinToString(" ").lowercase()
            if ("clear_all" in id || "clear all" in txt) {
                tasksHandler.removeCallbacks(snapshot)
                OpenTasks.write(this, emptyList())
                return
            }
        }
        tasksHandler.removeCallbacks(snapshot)
        tasksHandler.postDelayed(snapshot, 250)
    }

    private fun readRecents() {
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || root.findAccessibilityNodeInfosByViewId("$pkg:id/recents_view").isNullOrEmpty()) {
            removeClearAllCatcher()                 // not the Recents screen (any more)
            return
        }
        val titles = root.findAccessibilityNodeInfosByViewId("$pkg:id/title")
            .mapNotNull { it.text?.toString()?.trim()?.takeIf { t -> t.isNotEmpty() } }
            .distinct()
        OpenTasks.write(this, titles)
        findClearAll(root)?.let { placeClearAllCatcher(it) } ?: removeClearAllCatcher()
    }

    private fun findClearAll(n: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        val id = n.viewIdResourceName.orEmpty()
        val text = n.text?.toString().orEmpty()
        if ("clear_all" in id || "clearall" in id.lowercase() || text.equals("Clear all", ignoreCase = true)) {
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            return c ?: n
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { ch -> findClearAll(ch, depth + 1)?.let { return it } }
        return null
    }

    /**
     * HyperOS sends no event when "Clear all" is tapped and Recents closes at once, so we'd never
     * learn the apps are gone. While Recents is up, an invisible target sits exactly on that button:
     * a tap on it empties the dock's recent apps, then presses the real button. Any other touch
     * makes us check whether Recents is still there, and the target goes away with it.
     */
    private var catcher: View? = null
    private var catcherRect = Rect()

    private fun placeClearAllCatcher(node: AccessibilityNodeInfo) {
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) { removeClearAllCatcher(); return }
        val lp = overlay(r.width(), r.height(), Gravity.TOP or Gravity.START).apply {
            x = r.left; y = r.top
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        catcherRect = r
        val v = catcher
        if (v != null) { runCatching { wm.updateViewLayout(v, lp) }; return }
        val view = View(this).apply {
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_OUTSIDE -> {
                        tasksHandler.removeCallbacks(snapshot); tasksHandler.postDelayed(snapshot, 450)
                    }
                    MotionEvent.ACTION_UP -> {
                        OpenTasks.write(this@GestureService, emptyList())
                        // Step aside and tap the real button (HyperOS ignores accessibility clicks on it).
                        val cx = catcherRect.exactCenterX(); val cy = catcherRect.exactCenterY()
                        removeClearAllCatcher()
                        tasksHandler.postDelayed({ tap(cx, cy) }, 60)
                    }
                }
                true
            }
        }
        if (runCatching { wm.addView(view, lp) }.isSuccess) catcher = view
    }

    private fun tap(x: Float, y: Float) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)).build(),
            null, null,
        )
    }

    private fun removeClearAllCatcher() {
        catcher?.let { runCatching { wm.removeView(it) } }
        catcher = null
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() {
        detach()
        tasksHandler.removeCallbacks(snapshot)
        removeClearAllCatcher()
        if (listening) runCatching { unregisterReceiver(requests) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RECENTS = "app.slate.action.RECENTS"
        const val ACTION_HOME_SHOWN = "app.slate.action.HOME_SHOWN"
        private const val HOLD_MS = 220L
        private const val HOLD_MIN_DP = 50f
        private const val JITTER_DP = 7f

        /** Ask the running service to open Recents. Returns false if the service is off. */
        fun openRecents(context: Context): Boolean {
            if (!isRunning(context)) return false
            context.sendBroadcast(Intent(ACTION_RECENTS).setPackage(context.packageName))
            return true
        }

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
