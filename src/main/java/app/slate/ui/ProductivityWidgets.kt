package app.slate.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.slate.SlateApp
import app.slate.TimerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
private fun Title(text: String, accent: Color = Slate.Accent, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text.uppercase(), color = accent, size = WType.title, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Txt(text, color = Slate.Muted, size = 13.sp, modifier = Modifier.press(onClick = onClick).padding(4.dp))
}

@Composable
private fun Field(value: String, hint: String, modifier: Modifier = Modifier, size: Int = 15, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    Box(modifier) {
        if (value.isEmpty()) Txt(hint, color = Slate.Faint, size = size.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = onDone != null,
            textStyle = TextStyle(color = Slate.Ink, fontSize = size.sp),
            cursorBrush = SolidColor(Slate.Ink),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Re-read a widget's JSON whenever any widget data changes. */
@Composable
private fun data(slate: SlateApp, id: String): JSONObject {
    val v by slate.widgetData.version.collectAsState()
    return remember(v, id) { slate.widgetData.get(id) }
}

// ── Calendar ─────────────────────────────────────────────────────────────

private data class Event(val id: Long, val title: String, val begin: Long, val allDay: Boolean, val color: Int)

@Composable
fun CalendarWidget() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val now = remember { System.currentTimeMillis() }
    val events by produceState(emptyList<Event>(), allowed) {
        value = if (allowed) withContext(Dispatchers.IO) { loadEvents(context) } else emptyList()
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(SimpleDateFormat("d", Locale.getDefault()).format(Date(now)), size = WType.hero(), weight = FontWeight.Light)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Txt(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now)), color = Slate.Danger, size = 13.sp, weight = FontWeight.SemiBold)
                Txt(SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(now)), color = Slate.Muted, size = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !allowed -> Link("Show my events →") { ask.launch(Manifest.permission.READ_CALENDAR) }
            events.isEmpty() -> Txt("Nothing scheduled this week.", color = Slate.Muted, size = 14.sp)
            else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var lastDay = ""
                events.forEach { e ->
                    val day = dayLabel(e.begin)
                    if (day != lastDay) { Txt(day, color = Slate.Faint, size = 12.sp, weight = FontWeight.SemiBold); lastDay = day }
                    Row(
                        Modifier.fillMaxWidth().press {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)))
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Txt(e.title, size = 15.sp, maxLines = 1)
                            Txt(if (e.allDay) "All day" else android.text.format.DateFormat.getTimeFormat(context).format(Date(e.begin)), color = Slate.Muted, size = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(t: Long): String {
    val d = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(t), ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (ChronoUnit.DAYS.between(today, d)) {
        0L -> "TODAY"; 1L -> "TOMORROW"
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
    }
}

private fun loadEvents(context: Context): List<Event> = runCatching {
    val start = System.currentTimeMillis() - 60 * 60_000
    val end = start + 7L * 24 * 60 * 60_000
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, start); ContentUris.appendId(it, end)
    }.build()
    val out = mutableListOf<Event>()
    context.contentResolver.query(
        uri,
        arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR),
        null, null, "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 12) {
            out += Event(c.getLong(0), c.getString(1) ?: "(No title)", c.getLong(2), c.getInt(3) == 1, c.getInt(4))
        }
    }
    out
}.getOrDefault(emptyList())

// ── To-do ────────────────────────────────────────────────────────────────

@Composable
fun TodoWidget(slate: SlateApp, id: String) {
    val d = data(slate, id)
    val items = d.optJSONArray("items") ?: JSONArray()
    var draft by remember { mutableStateOf("") }
    fun save(arr: JSONArray) = slate.widgetData.update(id) { it.put("items", arr) }
    val open = (0 until items.length()).count { !items.getJSONObject(it).optBoolean("done") }
    Column(Modifier.fillMaxSize()) {
        Title("To-do · $open left", trailing = {
            if ((0 until items.length()).any { items.getJSONObject(it).optBoolean("done") }) Link("Clear done") {
                save(JSONArray((0 until items.length()).map { items.getJSONObject(it) }.filter { !it.optBoolean("done") }))
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val done = it.optBoolean("done")
                Row(
                    Modifier.fillMaxWidth().press(onLongClick = {
                        save(JSONArray((0 until items.length()).filter { j -> j != i }.map { j -> items.getJSONObject(j) }))
                    }) {
                        it.put("done", !done); save(items)
                    }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .then(if (done) Modifier.background(Slate.Accent) else Modifier.border(1.5.dp, Slate.Muted, CircleShape)),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextStrike(it.optString("t"), done)
                }
            }
        }
        Field(draft, "+ Add a task", Modifier.fillMaxWidth().padding(top = 8.dp), onDone = {
            if (draft.isNotBlank()) { items.put(JSONObject().put("t", draft.trim()).put("done", false)); save(items); draft = "" }
        }) { draft = it }
    }
}

@Composable
private fun BasicTextStrike(text: String, done: Boolean) {
    androidx.compose.foundation.text.BasicText(
        text, maxLines = 2,
        style = TextStyle(
            color = if (done) Slate.Faint else Slate.Ink, fontSize = 15.sp,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
    )
}

// ── Notes ────────────────────────────────────────────────────────────────

@Composable
fun NotesWidget(slate: SlateApp, id: String) {
    var text by remember(id) { mutableStateOf(slate.widgetData.get(id).optString("text")) }
    LaunchedEffect(text) { delay(400); slate.widgetData.update(id) { it.put("text", text) } }
    Column(Modifier.fillMaxSize()) {
        Title("Notes", Color(0xFFFFD60A))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (text.isEmpty()) Txt("Jot something down…", color = Slate.Faint, size = 15.sp)
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(color = Slate.Ink, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(Slate.Ink), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Weather (Open-Meteo, no account needed) ──────────────────────────────

@Composable
fun WeatherWidget(slate: SlateApp, id: String) {
    val d = data(slate, id)
    val city = d.optString("city")
    var editing by remember { mutableStateOf(city.isEmpty()) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(q) {
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        results = withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=4&name=" + URLEncoder.encode(q, "UTF-8")))
                    .optJSONArray("results") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }.getOrDefault(emptyList())
        }
    }
    val forecast by produceState<JSONObject?>(null, city, d.optDouble("lat")) {
        if (city.isEmpty()) return@produceState
        val cached = d.optJSONObject("cache")
        if (cached != null && System.currentTimeMillis() - d.optLong("at") < 30 * 60_000) { value = cached; return@produceState }
        value = cached
        withContext(Dispatchers.IO) {
            runCatching {
                JSONObject(get(
                    "https://api.open-meteo.com/v1/forecast?latitude=${d.optDouble("lat")}&longitude=${d.optDouble("lon")}" +
                        "&current=temperature_2m,apparent_temperature,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                        "&forecast_days=4&timezone=auto"
                ))
            }.getOrNull()
        }?.let { f -> value = f; slate.widgetData.update(id) { it.put("cache", f).put("at", System.currentTimeMillis()) } }
    }
    Column(Modifier.fillMaxSize()) {
        if (editing) {
            Title("Weather · choose a city", Color(0xFF64D2FF))
            Field(q, "Type a city…", Modifier.fillMaxWidth()) { q = it }
            Spacer(Modifier.height(8.dp))
            results.forEach { r ->
                val name = r.optString("name") + listOfNotNull(r.optString("admin1").ifEmpty { null }, r.optString("country").ifEmpty { null }).joinToString(", ", ", ")
                Txt(name, size = 14.sp, maxLines = 1, modifier = Modifier.fillMaxWidth().press {
                    slate.widgetData.update(id) {
                        it.put("city", r.optString("name")).put("lat", r.optDouble("latitude")).put("lon", r.optDouble("longitude"))
                            .remove("cache")
                    }
                    editing = false; q = ""
                }.padding(vertical = 7.dp))
            }
        } else {
            Title(city, Color(0xFF64D2FF), trailing = { Link("Change") { editing = true } })
            val cur = forecast?.optJSONObject("current")
            if (cur == null) Txt("Loading…", color = Slate.Muted)
            else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("${cur.optDouble("temperature_2m").toInt()}°", size = WType.hero(), weight = FontWeight.Light)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Txt(weatherText(cur.optInt("weather_code")), size = 16.sp)
                        Txt("Feels ${cur.optDouble("apparent_temperature").toInt()}°", color = Slate.Muted, size = 13.sp)
                    }
                }
                val daily = forecast?.optJSONObject("daily")
                if (daily != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val times = daily.optJSONArray("time"); val hi = daily.optJSONArray("temperature_2m_max"); val lo = daily.optJSONArray("temperature_2m_min")
                        if (times != null && hi != null && lo != null) for (i in 0 until minOf(4, times.length(), hi.length(), lo.length())) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val day = runCatching { LocalDate.parse(times.getString(i)).format(DateTimeFormatter.ofPattern("EEE")) }.getOrDefault("")
                                Txt(if (i == 0) "Today" else day, color = Slate.Muted, size = 12.sp)
                                Txt("${hi.optDouble(i).toInt()}° / ${lo.optDouble(i).toInt()}°", size = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
}

private fun weatherText(code: Int) = when (code) {
    0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
    45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"
    in 80..82 -> "Showers"; in 85..86 -> "Snow showers"; in 95..99 -> "Thunderstorm"; else -> "—"
}

// ── Focus timer ──────────────────────────────────────────────────────────

@Composable
fun TimerWidget(slate: SlateApp, id: String) {
    val context = LocalContext.current
    val d = data(slate, id)
    val focusMin = d.optInt("focus", 25)
    val breakMin = d.optInt("break", 5)
    val mode = d.optString("mode", "focus")
    val endAt = d.optLong("endAt", 0)
    val pausedLeft = d.optLong("left", -1)
    val total = (if (mode == "focus") focusMin else breakMin) * 60_000L
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) { while (endAt > 0) { now = System.currentTimeMillis(); delay(500) } }
    val running = endAt > now
    val left = when { running -> endAt - now; pausedLeft >= 0 -> pausedLeft; else -> total }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(running, endAt) {
        if (endAt in 1..now) slate.widgetData.update(id) { it.put("endAt", 0).put("left", -1).put("mode", if (mode == "focus") "break" else "focus") }
    }
    fun start() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        val end = System.currentTimeMillis() + left
        slate.widgetData.update(id) { it.put("endAt", end).put("left", -1) }
        TimerReceiver.schedule(context, end, if (mode == "focus") "Focus session done — take a break." else "Break's over — back to it.")
    }
    fun pause() {
        slate.widgetData.update(id) { it.put("endAt", 0).put("left", endAt - System.currentTimeMillis()) }
        TimerReceiver.cancel(context)
    }
    fun reset() {
        slate.widgetData.update(id) { it.put("endAt", 0).put("left", -1) }
        TimerReceiver.cancel(context)
    }

    val accent = if (mode == "focus") Color(0xFFFF9F0A) else Color(0xFF30D158)
    val size = LocalWidgetSize.current
    val wide = size.width > 300.dp
    val time = "%d:%02d".format(left / 60_000, (left / 1000) % 60)
    val progress = (1f - left.toFloat() / total).coerceIn(0f, 1f)
    // All three controls are always there, so nothing hides off the bottom of a short card.
    val controls: @Composable () -> Unit = {
        Chip(if (running) "Pause" else if (pausedLeft >= 0) "Resume" else "Start", strong = true, compact = true) { if (running) pause() else start() }
        Chip("Reset", compact = true) { reset() }
        Chip(if (mode == "focus") "Break" else "Focus", compact = true) {
            TimerReceiver.cancel(context)
            slate.widgetData.update(id) { it.put("mode", if (mode == "focus") "break" else "focus").put("endAt", 0).put("left", -1) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Title(if (mode == "focus") "Focus" else "Break", accent, trailing = {
            Link("$focusMin / $breakMin min") {
                val next = when (focusMin) { 25 -> 50; 50 -> 15; else -> 25 }
                slate.widgetData.update(id) { it.put("focus", next).put("break", if (next == 50) 10 else 5).put("left", -1) }
            }
        })
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Txt(time, size = WType.hero(), weight = FontWeight.Light, maxLines = 1, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
            }
        } else {
            Txt(time, size = WType.hero(), weight = FontWeight.Light, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Slate.GlassLight)) {
            Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        }
    }
}

// ── World clocks ─────────────────────────────────────────────────────────

val ZONES = listOf(
    "Asia/Kolkata" to "India", "Europe/London" to "London", "America/New_York" to "New York",
    "America/Los_Angeles" to "San Francisco", "Europe/Berlin" to "Berlin", "Asia/Dubai" to "Dubai",
    "Asia/Singapore" to "Singapore", "Asia/Tokyo" to "Tokyo", "Australia/Sydney" to "Sydney", "UTC" to "UTC",
)

@Composable
fun WorldClockWidget(slate: SlateApp, id: String) {
    val d = data(slate, id)
    val zones = d.optJSONArray("zones")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        ?: listOf("Europe/London", "America/New_York", "Asia/Tokyo")
    var picking by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Column(Modifier.fillMaxSize()) {
        Title("World clock", Color(0xFFBF5AF2), trailing = { Link(if (picking) "Done" else "Edit") { picking = !picking } })
        if (picking) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ZONES.forEach { (z, name) ->
                    val on = z in zones
                    Row(Modifier.fillMaxWidth().press {
                        val next = if (on) zones - z else (zones + z).take(4)
                        slate.widgetData.update(id) { it.put("zones", JSONArray(next)) }
                    }.padding(vertical = 6.dp)) {
                        Txt(name, size = 14.sp, modifier = Modifier.weight(1f))
                        if (on) Txt("✓", color = Slate.Accent, size = 14.sp)
                    }
                }
            }
        } else {
            val localDay = LocalDate.now()
            zones.forEach { z ->
                val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.of(z))
                val diff = ChronoUnit.DAYS.between(localDay, t.toLocalDate())
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(ZONES.firstOrNull { it.first == z }?.second ?: z, size = 15.sp)
                        Txt(when (diff) { 0L -> "Today"; 1L -> "Tomorrow"; -1L -> "Yesterday"; else -> "" }, color = Slate.Muted, size = 12.sp)
                    }
                    Txt(t.format(DateTimeFormatter.ofPattern("h:mm a")), size = 22.sp, weight = FontWeight.Light)
                }
            }
        }
    }
}

// ── Countdown ────────────────────────────────────────────────────────────

@Composable
fun CountdownWidget(slate: SlateApp, id: String) {
    val context = LocalContext.current
    val d = data(slate, id)
    val title = d.optString("title")
    val date = d.optString("date").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
    var name by remember { mutableStateOf(title) }
    Column(Modifier.fillMaxSize()) {
        Title("Countdown", Color(0xFFFF375F), trailing = {
            Link(if (date == null) "Pick date" else "Change") {
                val c = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, day ->
                    slate.widgetData.update(id) { it.put("date", LocalDate.of(y, m + 1, day).toString()) }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        })
        Field(name, "What are you counting down to?", Modifier.fillMaxWidth(), size = 16, onDone = {
            slate.widgetData.update(id) { it.put("title", name.trim()) }
        }) { name = it; slate.widgetData.update(id) { o -> o.put("title", it.trim()) } }
        Spacer(Modifier.height(8.dp))
        if (date != null) {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(if (days >= 0) "$days" else "${-days}", size = WType.hero(), weight = FontWeight.Light)
                Spacer(Modifier.width(8.dp))
                Txt(if (days == 0L) "today!" else if (days > 0) (if (days == 1L) "day to go" else "days to go") else "days ago", color = Slate.Muted, size = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Txt(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = Slate.Faint, size = 12.sp)
        }
    }
}

// ── Habits ───────────────────────────────────────────────────────────────

@Composable
fun HabitsWidget(slate: SlateApp, id: String) {
    val d = data(slate, id)
    val habits = d.optJSONArray("habits") ?: JSONArray()
    var draft by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    fun save() = slate.widgetData.update(id) { it.put("habits", habits) }
    Column(Modifier.fillMaxSize()) {
        Title("Habits", Color(0xFF30D158), trailing = {
            Row { days.forEach { Txt(it.format(DateTimeFormatter.ofPattern("EEEEE")), color = Slate.Faint, size = 11.sp, modifier = Modifier.width(22.dp)) } }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until habits.length()) {
                val h = habits.getJSONObject(i)
                val done = h.optJSONArray("d")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                val streak = generateSequence(today) { it.minusDays(1) }.takeWhile { it.toString() in done }.count()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).press(onLongClick = {
                        val keep = JSONArray(); for (j in 0 until habits.length()) if (j != i) keep.put(habits.getJSONObject(j))
                        slate.widgetData.update(id) { it.put("habits", keep) }
                    }) {}) {
                        Txt(h.optString("n"), size = 15.sp, maxLines = 1)
                        if (streak > 0) Txt("🔥 $streak day streak", color = Slate.Muted, size = 11.sp)
                    }
                    days.forEach { day ->
                        val on = day.toString() in done
                        Box(
                            Modifier.width(22.dp).height(22.dp).padding(2.dp).clip(CircleShape)
                                .then(if (on) Modifier.background(Color(0xFF30D158)) else Modifier.border(1.5.dp, Slate.Faint, CircleShape))
                                .press {
                                    val next = if (on) done - day.toString() else done + day.toString()
                                    h.put("d", JSONArray(next.toList())); save()
                                },
                        )
                    }
                }
            }
        }
        Field(draft, "+ New habit", Modifier.fillMaxWidth().padding(top = 8.dp), onDone = {
            if (draft.isNotBlank()) { habits.put(JSONObject().put("n", draft.trim()).put("d", JSONArray())); save(); draft = "" }
        }) { draft = it }
    }
}

// ── Android widgets from installed apps ──────────────────────────────────

@Composable
fun HostedWidget(slate: SlateApp, appWidgetId: Int) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wDp = maxWidth.value.toInt(); val hDp = maxHeight.value.toInt()
        LaunchedEffect(wDp, hDp) { slate.appWidgets.resize(appWidgetId, wDp, hDp) }
        AndroidView(
            factory = { ctx -> slate.appWidgets.createView(ctx, appWidgetId) ?: FrameLayout(ctx) },
            modifier = Modifier.fillMaxSize(),
        )
        if (slate.appWidgets.info(appWidgetId) == null) {
            Txt("This widget's app was removed.", color = Slate.Muted, size = 14.sp)
        }
    }
}
