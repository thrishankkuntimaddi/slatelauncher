package app.slate.ui

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// ── Sticky notes ─────────────────────────────────────────────────────────

private val STICKY = listOf(Color(0xFFFFE27A), Color(0xFFFFB3C7), Color(0xFFA6E3FF), Color(0xFFB8F2A2), Color(0xFFD9C2FF))

/** A wall of coloured sticky notes. Tap to write, hold to remove, + for another. */
@Composable
fun StickyWidget(slate: SlateApp, id: String) {
    val v by slate.widgetData.version.collectAsState()
    val notes = remember(v) { slate.widgetData.get(id).optJSONArray("notes") ?: JSONArray() }
    fun save(a: JSONArray) = slate.widgetData.update(id) { it.put("notes", a) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("STICKY NOTES", color = Color(0xFFFFE27A), size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Txt("+ New", color = Slate.Muted, size = 13.sp, modifier = Modifier.press {
                notes.put(JSONObject().put("t", "").put("c", notes.length() % STICKY.size)); save(notes)
            }.padding(4.dp))
        }
        if (notes.length() == 0) {
            Txt("Tap + New for a sticky note.", color = Slate.Faint, size = 14.sp)
        }
        LazyVerticalGrid(GridCells.Adaptive(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed((0 until notes.length()).map { notes.getJSONObject(it) }, key = { i, _ -> "$id-$i" }) { i, n ->
                StickyNote(n.optString("t"), STICKY[n.optInt("c") % STICKY.size],
                    onChange = { t -> n.put("t", t); slate.widgetData.update(id) { it.put("notes", notes) } },
                    onRemove = {
                        val keep = JSONArray(); for (j in 0 until notes.length()) if (j != i) keep.put(notes.getJSONObject(j)); save(keep)
                    },
                    onRecolor = { n.put("c", (n.optInt("c") + 1) % STICKY.size); save(notes) },
                )
            }
        }
    }
}

@Composable
private fun StickyNote(text: String, color: Color, onChange: (String) -> Unit, onRemove: () -> Unit, onRecolor: () -> Unit) {
    var t by remember(text) { mutableStateOf(text) }
    LaunchedEffect(t) { if (t != text) { delay(350); onChange(t) } }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(color).padding(10.dp),
    ) {
        BasicTextField(
            value = t, onValueChange = { t = it },
            textStyle = TextStyle(color = Color(0xFF2B2B2B), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Color(0xFF2B2B2B)), modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0x33000000)).press(onClick = onRecolor))
            Txt("✕", color = Color(0x99000000), size = 12.sp, modifier = Modifier.press(onClick = onRemove))
        }
    }
}

// ── Screen time ──────────────────────────────────────────────────────────

/** Today's screen time and the apps it went to. */
@Composable
fun ScreenTimeWidget(slate: SlateApp) {
    val context = LocalContext.current
    val apps by slate.apps.apps.collectAsState()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val allowed = slate.usage.hasSystemAccess(context)
    val data by produceState(emptyList<Pair<String, Long>>(), tick, allowed) {
        value = if (!allowed) emptyList() else withContext(Dispatchers.IO) { todayUsage(context) }
    }
    val total = data.sumOf { it.second }
    Column(Modifier.fillMaxSize()) {
        Txt("SCREEN TIME · TODAY", color = Color(0xFF64D2FF), size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        if (!allowed) {
            Txt("Allow Usage access in Settings → System to see this.", color = Slate.Muted, size = 14.sp)
            return@Column
        }
        Txt(fmt(total), size = 36.sp, weight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        val max = data.firstOrNull()?.second ?: 1L
        data.take(4).forEach { (pkg, ms) ->
            val app = apps.firstOrNull { it.packageName == pkg }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIcon(app, 22.dp) else Spacer(Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Txt(app?.label ?: pkg, size = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Txt(fmt(ms), color = Slate.Muted, size = 12.sp)
                    }
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Slate.GlassLight)) {
                        Box(Modifier.fillMaxWidth(ms.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(0xFF64D2FF)))
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun todayUsage(context: Context): List<Pair<String, Long>> = runCatching {
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
    val skip = setOf(context.packageName, "com.miui.home", "com.android.systemui", "app.nolauncher")
    val launchable = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    ).map { it.activityInfo.packageName }.toSet()
    usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        .filter { (k, v) -> k !in skip && k in launchable && v.totalTimeInForeground > 60_000 }
        .map { (k, v) -> k to v.totalTimeInForeground }
        .sortedByDescending { it.second }
}.getOrDefault(emptyList())

// ── Up next ──────────────────────────────────────────────────────────────

/** Your next event and how long until it starts. */
@Composable
fun UpNextWidget() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val next by produceState<Triple<Long, String, Long>?>(null, allowed, now / 300_000) {
        value = if (allowed) withContext(Dispatchers.IO) { nextEvent(context) } else null
    }
    Column(Modifier.fillMaxSize()) {
        Txt("UP NEXT", color = Color(0xFFFF9F0A), size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        val n = next
        when {
            !allowed -> Txt("Show my next event →", color = Slate.Muted, size = 14.sp, modifier = Modifier.press { ask.launch(Manifest.permission.READ_CALENDAR) })
            n == null -> { Txt("You're free.", size = 22.sp, weight = FontWeight.Light); Txt("Nothing else today.", color = Slate.Muted, size = 13.sp) }
            else -> {
                val mins = ((n.first - now) / 60_000).coerceAtLeast(0)
                Txt(n.second, size = 19.sp, weight = FontWeight.Medium, maxLines = 2, modifier = Modifier.press {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, n.third))) }
                })
                Spacer(Modifier.height(4.dp))
                Txt(
                    if (mins == 0L) "Now" else if (mins < 60) "in $mins min" else "in ${mins / 60}h ${mins % 60}m",
                    color = Color(0xFFFF9F0A), size = 26.sp, weight = FontWeight.Light,
                )
                Txt(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(n.first)), color = Slate.Muted, size = 13.sp)
            }
        }
    }
}

private fun nextEvent(context: Context): Triple<Long, String, Long>? = runCatching {
    val now = System.currentTimeMillis()
    val endOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, endOfDay) }.build()
    context.contentResolver.query(
        uri, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.ALL_DAY),
        "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= ?", arrayOf(now.toString()),
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { c -> if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1) ?: "(No title)", c.getLong(2)) else null }
}.getOrNull()

// ── Photo frame ──────────────────────────────────────────────────────────

/** A favourite photo on your home screen. Tap to choose another. */
@Composable
fun PhotoWidget(slate: SlateApp, id: String) {
    val context = LocalContext.current
    val v by slate.widgetData.version.collectAsState()
    val uri = remember(v) { slate.widgetData.get(id).optString("uri") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            slate.widgetData.update(id) { it.put("uri", u.toString()) }
        }
    }
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = if (uri.isEmpty()) null else withContext(Dispatchers.IO) { load(context, Uri.parse(uri)) }
    }
    Box(
        Modifier.fillMaxSize().press { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(WIDGET_RADIUS - 8.dp)))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("PHOTO", color = Color(0xFFFF6482), size = 12.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Txt("Tap to choose a photo", color = Slate.Muted, size = 14.sp)
            }
        }
    }
}

private fun load(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / sample > 1400 || opts.outHeight / sample > 1400) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }?.asImageBitmap()
}.getOrNull()
