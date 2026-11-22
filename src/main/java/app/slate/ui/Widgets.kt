package app.slate.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/** Page 1, landscape: widgets stacked in a column beside the icons. */
@Composable
fun WidgetStack(slate: SlateApp, actions: Actions, editing: Boolean, modifier: Modifier) {
    val widgets by slate.home.widgets.collectAsState()
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        widgets.forEach { w -> WidgetCard(w, slate, actions, editing, Modifier.fillMaxWidth().height(heightOf(slate, w))) }
        if (widgets.isEmpty()) EmptyWidgets(actions)
    }
}

/** Page 1, portrait: widgets in a row above the icons. */
@Composable
fun WidgetRow(slate: SlateApp, actions: Actions, editing: Boolean, modifier: Modifier) {
    val widgets by slate.home.widgets.collectAsState()
    if (widgets.size in 1..3) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            widgets.forEach { w -> WidgetCard(w, slate, actions, editing, Modifier.weight(1f).fillMaxHeight()) }
        }
    } else {
        LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(widgets, key = { it.id }) { w -> WidgetCard(w, slate, actions, editing, Modifier.width(340.dp).fillMaxHeight()) }
            if (widgets.isEmpty()) item { EmptyWidgets(actions) }
        }
    }
}

private fun heightOf(slate: SlateApp, w: WidgetSpec): Dp = when (w.type) {
    "clock" -> 170.dp
    "battery" -> 118.dp
    "calendar" -> 290.dp
    "todo" -> 300.dp
    "notes" -> 230.dp
    "weather" -> 215.dp
    "timer" -> 220.dp
    "world" -> 235.dp
    "countdown" -> 200.dp
    "habits" -> 270.dp
    "app" -> slate.appWidgets.info(w.appWidgetId)?.let { (it.minHeight / slate.resources.displayMetrics.density).dp.coerceIn(130.dp, 420.dp) } ?: 180.dp
    else -> 200.dp
}

/** Built-in widgets: type → (name, what it's for). */
val WIDGET_TYPES = listOf(
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
fun WidgetCard(spec: WidgetSpec, slate: SlateApp, actions: Actions, editing: Boolean, modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier.fillMaxSize()
                .glass(RoundedCornerShape(26.dp), Color(0x2BFFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(26.dp))
                .padding(if (spec.type == "app") 8.dp else 20.dp),
        ) {
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
            }
        }
        if (editing) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Txt(SimpleDateFormat("EEEE", Locale.getDefault()).format(d).uppercase(), color = Slate.Accent, size = 13.sp, weight = FontWeight.SemiBold)
        Txt(
            android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(d),
            size = 52.sp, weight = FontWeight.Light,
        )
        Txt(SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(d), color = Slate.Muted, size = 15.sp)
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
            Txt("Battery", color = Slate.Muted, size = 13.sp, weight = FontWeight.SemiBold)
            if (charging) Txt("  · charging", color = Color(0xFF7BE495), size = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Txt(if (level >= 0) "$level%" else "—", size = 34.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Slate.GlassLight)) {
            Box(
                Modifier.fillMaxWidth((level.coerceAtLeast(0)) / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (level in 0..20 && !charging) Slate.Danger else Color(0xFF7BE495)),
            )
        }
    }
}
