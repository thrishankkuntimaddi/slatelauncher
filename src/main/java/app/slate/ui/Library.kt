package app.slate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.App
import app.slate.data.Categories

/**
 * App Library — the last page. Every app sorted into boxes: Suggestions (what you use most),
 * Recently Added, then categories. Each box shows three apps you can open straight away and a
 * cluster you tap to see the rest. Search lists everything A–Z.
 */
@Composable
fun Library(
    slate: SlateApp,
    actions: Actions,
    landscape: Boolean,
    onOpenBox: (String, List<App>) -> Unit,
    onAppMenu: (App) -> Unit,
) {
    val context = LocalContext.current
    val apps by slate.apps.apps.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val counts by slate.usage.counts.collectAsState()
    val visible = remember(apps, prefs.hidden) { apps.filter { it.key !in prefs.hidden } }
    val boxes by produceState(emptyList<Pair<String, List<App>>>(), visible, counts) {
        value = withContext(Dispatchers.Default) {
            val suggestions = visible.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 }.take(8)
            val recent = visible.sortedByDescending { it.installed }.take(8)
            buildList {
                if (suggestions.size >= 3) add("Suggestions" to suggestions)
                add("Recently Added" to recent)
                addAll(Categories.sort(context, visible))
            }
        }
    }
    var query by remember { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = if (landscape) 5 else 3
        val gap = if (landscape) 34.dp else 30.dp
        val box = min((maxWidth - 56.dp - gap * (cols - 1)) / cols, if (landscape) 196.dp else 210.dp)
        val side = (maxWidth - box * cols - gap * (cols - 1)) / 2

        Column(Modifier.fillMaxSize()) {
            SearchBar(query, { query = it }, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 18.dp))
            if (query.isBlank()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(start = side, end = side, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(boxes, key = { it.first }) { (title, list) ->
                        CategoryBox(title, list, box, onApp = { actions.launch(it, null) }, onHold = onAppMenu) { onOpenBox(title, list) }
                    }
                }
            } else {
                val results = remember(visible, query) {
                    val q = query.trim().lowercase()
                    visible.filter { it.label.lowercase().contains(q) }
                        .sortedWith(compareBy({ !it.label.lowercase().startsWith(q) }, { it.label.lowercase() }))
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(horizontal = side, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(results, key = { it.key }) { a -> AppRow(a, onHold = { onAppMenu(a) }) { actions.launch(a, null) } }
                }
                if (results.isEmpty()) {
                    Txt("No apps match “$query”", color = Slate.Muted, size = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .widthIn(max = 520.dp).fillMaxWidth(0.6f).height(44.dp)
            .glass(RoundedCornerShape(14.dp), Slate.Card)
            .border(1.dp, Slate.CardEdge, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MagnifierGlyph(Slate.Muted, 16.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt("App Library", color = Slate.Muted, size = 16.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Slate.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Slate.Ink), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Txt("Cancel", color = Slate.Accent, size = 15.sp, modifier = Modifier.press { onChange("") })
    }
}

@Composable
fun MagnifierGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = 1.8.dp.toPx()
        drawCircle(color, w * 0.33f, Offset(w * 0.42f, w * 0.42f), style = Stroke(st))
        drawLine(color, Offset(w * 0.67f, w * 0.67f), Offset(w * 0.94f, w * 0.94f), st, StrokeCap.Round)
    }
}

/** 2×2: three apps to open directly, plus a cluster of the rest. */
@Composable
private fun CategoryBox(title: String, apps: List<App>, box: Dp, onApp: (App) -> Unit, onHold: (App) -> Unit, onOpen: () -> Unit) {
    val pad = box * 0.11f
    val gap = box * 0.08f
    val slot = (box - pad * 2 - gap) / 2
    val big = slot * 0.92f
    val mini = (slot - gap * 0.6f) / 2 * 0.9f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(box)
                .glass(RoundedCornerShape(box * 0.15f), Slate.Card)
                .border(1.dp, Slate.CardEdge, RoundedCornerShape(box * 0.15f))
                .padding(pad),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(apps.getOrNull(0), slot, big, onApp, onHold)
                    Slot(apps.getOrNull(1), slot, big, onApp, onHold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(apps.getOrNull(2), slot, big, onApp, onHold)
                    Box(Modifier.size(slot).press(onClick = onOpen), contentAlignment = Alignment.Center) {
                        when {
                            apps.size == 4 -> AppIcon(apps[3], big)
                            apps.size > 4 -> Column(verticalArrangement = Arrangement.spacedBy(gap * 0.6f)) {
                                apps.drop(3).take(4).chunked(2).forEach { r ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(gap * 0.6f)) { r.forEach { AppIcon(it, mini) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        Txt(
            title, size = 13.sp, weight = FontWeight.SemiBold, shadow = true, maxLines = 1, align = TextAlign.Center,
            modifier = Modifier.width(box).padding(top = 9.dp).press(onClick = onOpen),
        )
    }
}

@Composable
private fun Slot(app: App?, slot: Dp, icon: Dp, onApp: (App) -> Unit, onHold: (App) -> Unit) {
    Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
        if (app != null) AppIcon(app, icon, Modifier.press(onLongClick = { onHold(app) }) { onApp(app) })
    }
}

@Composable
fun AppRow(app: App, onHold: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().press(onLongClick = onHold, onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 42.dp)
        Spacer(Modifier.size(14.dp))
        Txt(app.label, size = 17.sp, maxLines = 1, shadow = true)
    }
}
