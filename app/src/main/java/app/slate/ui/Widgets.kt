package app.slate.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import app.slate.data.WidgetStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.WidgetSpec
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grid metrics shared with the icons so widgets snap to the same rows and gutters. */
data class WidgetMetrics(val unit: Dp, val block: Dp, val gap: Dp, val hGap: Dp) {
    /** A widget [rows] tall spans from the top of one icon to the bottom of the label [rows] rows down. */
    fun height(rows: Int): Dp = unit * (rows - 1) + block
}

/** The size of the widget being drawn, so its content can adapt (wide timer row, hero text size). */
val LocalWidgetSize = compositionLocalOf { DpSize(320.dp, 200.dp) }

/** One type scale for every widget: big numbers grow with the card, everything else stays put. */
object WType {
    val title = 12.sp
    val body = 15.sp
    val small = 13.sp
    val caption = 12.sp

    /** The big number (time, temperature, days left), sized to the card's height. */
    @Composable
    fun hero(): TextUnit {
        val h = LocalWidgetSize.current.height
        return when {
            h < 150.dp -> 34.sp
            h < 220.dp -> 42.sp
            h < 320.dp -> 50.sp
            else -> 58.sp
        }
    }
}

/**
 * Drives a widget stack like a smart stack: it snaps from one widget to the next, and after
 * [seconds] of nobody touching it, moves on by itself (wrapping to the top at the end).
 * It holds still while you touch it, type in it, or edit the home screen.
 */
private class StackDriver {
    var lastTouch by mutableLongStateOf(0L)
    var focused by mutableStateOf<String?>(null)
}

@Composable
private fun rememberStackDriver(
    state: LazyListState,
    count: Int,
    seconds: Int,
    editing: Boolean,
    imeOpen: Boolean,
): StackDriver {
    val driver = remember { StackDriver() }
    LaunchedEffect(seconds, editing, count, driver.focused, imeOpen) {
        if (seconds <= 0 || editing || count < 2 || driver.focused != null || imeOpen) return@LaunchedEffect
        while (true) {
            delay(seconds * 1000L)
            val idle = SystemClock.uptimeMillis() - driver.lastTouch
            if (state.isScrollInProgress || idle < seconds * 1000L) continue
            val next = state.firstVisibleItemIndex + 1
            if (!state.canScrollForward || next >= count) {
                state.animateScrollToItem(0)
            } else {
                val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == next }
                if (info != null) state.animateScrollBy(info.offset.toFloat(), tween(700, easing = FastOutSlowInEasing))
                else state.animateScrollToItem(next)
            }
        }
    }
    return driver
}

/** Notes every touch on the stack without taking it, so the auto-scroll waits for you. */
private fun Modifier.touchClock(driver: StackDriver) = pointerInput(driver) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            driver.lastTouch = SystemClock.uptimeMillis()
        }
    }
}

/** How far the keyboard reaches up into this box, so we can move the content above it. */
@Composable
private fun Modifier.keyboardOverlap(onOverlap: (Dp) -> Unit): Modifier {
    val density = LocalDensity.current
    val view = LocalView.current
    val ime = WindowInsets.ime.getBottom(density)
    var bottom by remember { mutableIntStateOf(0) }
    val overlap = (ime - (view.height - bottom)).coerceAtLeast(0)
    onOverlap(with(density) { overlap.toDp() })
    return onGloballyPositioned { bottom = (it.positionInWindow().y + it.size.height).toInt() }
}

/** Page 1, landscape: a column of widgets beside the icons, scrolling one widget at a time. */
@Composable
fun WidgetStack(slate: SlateApp, actions: Actions, editing: Boolean, m: WidgetMetrics, modifier: Modifier) {
    val widgets by slate.home.widgets.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val v by slate.widgetData.version.collectAsState()
    val state = rememberLazyListState()
    var overlap by remember { mutableStateOf(0.dp) }
    val driver = rememberStackDriver(state, widgets.size, prefs.widgetRotate, editing, overlap > 0.dp)
    val gap = m.gap.coerceIn(12.dp, 22.dp)

    // A new widget goes in at the top: show it.
    var lastFirst by remember { mutableStateOf(widgets.firstOrNull()?.id) }
    LaunchedEffect(widgets.firstOrNull()?.id) {
        val first = widgets.firstOrNull()?.id
        if (first != null && first != lastFirst && widgets.size > 1) state.animateScrollToItem(0)
        lastFirst = first
    }
    // Typing: keep the widget you're typing in in view, above the keyboard.
    LaunchedEffect(overlap > 0.dp, driver.focused) {
        val i = widgets.indexOfFirst { it.id == driver.focused }
        if (overlap > 0.dp && i >= 0) state.animateScrollToItem(i)
    }

    Box(modifier.keyboardOverlap { overlap = it }) {
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = overlap).touchClock(driver),
            state = state,
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(bottom = 12.dp),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(widgets, key = { it.id }) { w ->
                val rows = remember(w, v, m) { rowsOf(slate, w, m) }
                WidgetCard(
                    w, slate, actions, editing, rows,
                    Modifier.animateItem(
                        fadeInSpec = tween(260), fadeOutSpec = tween(180),
                        placementSpec = spring(0.85f, Spring.StiffnessMediumLow),
                    ).fillMaxWidth().height(m.height(rows))
                        .onFocusChanged { f -> if (f.hasFocus) driver.focused = w.id else if (driver.focused == w.id) driver.focused = null },
                )
            }
            if (widgets.isEmpty()) item { EmptyWidgets(actions) }
        }
    }
}

/** Page 1, portrait: two widgets side by side; swipe sideways for more (snaps to whole cards). */
@Composable
fun WidgetRow(slate: SlateApp, actions: Actions, editing: Boolean, m: WidgetMetrics, modifier: Modifier) {
    val widgets by slate.home.widgets.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val v by slate.widgetData.version.collectAsState()
    val state = rememberLazyListState()
    var overlap by remember { mutableStateOf(0.dp) }
    val driver = rememberStackDriver(state, widgets.size, prefs.widgetRotate, editing, overlap > 0.dp)
    val gap = m.hGap.coerceIn(14.dp, 24.dp)
    LaunchedEffect(widgets.firstOrNull()?.id) { if (widgets.size > 1) state.animateScrollToItem(0) }
    BoxWithConstraints(modifier.keyboardOverlap { overlap = it }) {
        val half = (maxWidth - gap) / 2
        LazyRow(
            Modifier.fillMaxSize().touchClock(driver), state = state,
            horizontalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(widgets, key = { it.id }) { w ->
                // Large widgets take the whole width in portrait.
                val wide = remember(w, v) { sizeOverride(slate, w) >= 4 }
                WidgetCard(
                    w, slate, actions, editing, 3,
                    Modifier.animateItem(fadeInSpec = tween(260), fadeOutSpec = tween(180))
                        .width(if (wide) maxWidth else half).fillMaxHeight()
                        .onFocusChanged { f -> if (f.hasFocus) driver.focused = w.id else if (driver.focused == w.id) driver.focused = null },
                )
            }
            if (widgets.isEmpty()) item { EmptyWidgets(actions) }
        }
    }
}

/** Size picked in edit mode (rows tall), or 0 for the widget's natural size. */
private fun sizeOverride(slate: SlateApp, w: WidgetSpec): Int = slate.widgetData.get(w.id).optInt("_rows", 0)

/** How many icon rows each widget spans. */
private fun rowsOf(slate: SlateApp, w: WidgetSpec, m: WidgetMetrics): Int {
    sizeOverride(slate, w).takeIf { it > 0 }?.let { return it }
    return when (w.type) {
        "battery" -> 1
        "clock", "weather", "timer", "world", "countdown", "upnext" -> 2
        "calendar", "todo", "habits", "sticky", "screentime", "photo", "notes" -> 3
        "app" -> slate.appWidgets.info(w.appWidgetId)?.let { info ->
            val dp = info.minHeight / slate.resources.displayMetrics.density
            kotlin.math.ceil(dp / m.unit.value).toInt().coerceIn(1, 4)
        } ?: 2
        else -> 2
    }
}

private fun sizesFor(type: String) = if (type == "battery") listOf(1 to "S", 2 to "M") else listOf(2 to "S", 3 to "M", 4 to "L")

/** Built-in widgets: type → (name, what it's for). */
val WIDGET_TYPES = listOf(
    "sticky" to ("Sticky notes" to "Colourful notes you can tap and write on"),
    "upnext" to ("Up next" to "Your next event and how long until it starts"),
    "screentime" to ("Screen time" to "Today's total and where it went"),
    "photo" to ("Photo frame" to "A favourite photo on your home screen"),
    "clock" to ("Clock" to "Time and date at a glance"),
    "calendar" to ("Calendar" to "Your events for the week"),
    "todo" to ("To-do" to "Tasks you can tick off"),
    "notes" to ("Notes" to "A scratchpad that saves itself"),
    "weather" to ("Weather" to "Now and the next few days"),
    "timer" to ("Focus timer" to "25/5 focus sessions with alerts"),
    "habits" to ("Habits" to "Daily check-ins with streaks"),
    "countdown" to ("Countdown" to "Days until something that matters"),
    "world" to ("World clock" to "Times in other cities"),
    "battery" to ("Battery" to "Charge level"),
)

@Composable
private fun EmptyWidgets(actions: Actions) {
    Txt(
        "+ Add widgets", color = Slate.Muted, size = 15.sp,
        modifier = Modifier.glass(RoundedCornerShape(24.dp), Slate.Glass).press { actions.openWidgets() }.padding(24.dp),
    )
}

@Composable
fun WidgetCard(spec: WidgetSpec, slate: SlateApp, actions: Actions, editing: Boolean, rows: Int, modifier: Modifier) {
    val prefs by slate.settings.prefs.collectAsState()
    val (fill, edge) = when (prefs.widgetStyle) {
        WidgetStyle.Frosted -> Slate.Card to Slate.CardEdge
        WidgetStyle.Dark -> Color(0xE6151519) to Color(0x14FFFFFF)
        WidgetStyle.Clear -> Color(0x14FFFFFF) to Color(0x26FFFFFF)
    }
    val shape = RoundedCornerShape(WIDGET_RADIUS)
    BoxWithConstraints(modifier) {
        val size = DpSize(maxWidth, maxHeight)
        val inner = when {
            spec.type == "app" || spec.type == "photo" -> 8.dp
            maxHeight < 150.dp -> 14.dp
            else -> 18.dp
        }
        Box(
            Modifier.fillMaxSize()
                .glass(shape, fill)
                .border(1.dp, edge, shape)
                .padding(inner),
        ) {
            CompositionLocalProvider(LocalWidgetSize provides DpSize(size.width - inner * 2, size.height - inner * 2)) {
                when (spec.type) {
                    "clock" -> ClockWidget()
                    "battery" -> BatteryWidget()
                    "calendar" -> CalendarWidget()
                    "todo" -> TodoWidget(slate, spec.id)
                    "notes" -> NotesWidget(slate, spec.id)
                    "weather" -> WeatherWidget(slate, spec.id)
                    "timer" -> TimerWidget(slate, spec.id)
                    "world" -> WorldClockWidget(slate, spec.id)
                    "countdown" -> CountdownWidget(slate, spec.id)
                    "habits" -> HabitsWidget(slate, spec.id)
                    "app" -> HostedWidget(slate, spec.appWidgetId)
                    "sticky" -> StickyWidget(slate, spec.id)
                    "screentime" -> ScreenTimeWidget(slate)
                    "upnext" -> UpNextWidget()
                    "photo" -> PhotoWidget(slate, spec.id)
                }
            }
        }
        AnimatedVisibility(editing, Modifier.matchParentSize(), enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Column(
                Modifier.fillMaxSize().clip(shape).background(Color(0xE6141418)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                if (spec.type != "app") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sizesFor(spec.type).forEach { (r, label) ->
                            Chip(label, strong = r == rows) { slate.widgetData.update(spec.id) { it.put("_rows", r) } }
                        }
                    }
                }
                Chip("Remove widget") { removeWidget(slate, spec) }
            }
        }
    }
}

fun removeWidget(slate: SlateApp, spec: WidgetSpec) {
    slate.home.setWidgets(slate.home.widgets.value.filter { it.id != spec.id })
    if (spec.appWidgetId >= 0) slate.appWidgets.delete(spec.appWidgetId)
    slate.widgetData.remove(spec.id)
}

@Composable
private fun ClockWidget() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) } }
    val d = Date(now)
    val hero = WType.hero()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Txt(SimpleDateFormat("EEEE", Locale.getDefault()).format(d).uppercase(), color = Slate.Accent, size = WType.title, weight = FontWeight.SemiBold)
        Txt(
            android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(d),
            size = hero * 1.15f, weight = FontWeight.Light, maxLines = 1,
        )
        Txt(SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(d), color = Slate.Muted, size = WType.body)
    }
}

@Composable
private fun BatteryWidget() {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (l >= 0) l * 100 / s else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row {
            Txt("BATTERY", color = Color(0xFF7BE495), size = WType.title, weight = FontWeight.SemiBold)
            if (charging) Txt("  · CHARGING", color = Slate.Muted, size = WType.title)
        }
        Spacer(Modifier.height(6.dp))
        Txt(if (level >= 0) "$level%" else "—", size = WType.hero(), weight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Slate.GlassLight)) {
            Box(
                Modifier.fillMaxWidth((level.coerceAtLeast(0)) / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (level in 0..20 && !charging) Slate.Danger else Color(0xFF7BE495)),
            )
        }
    }
}

val WIDGET_RADIUS = 24.dp
