package app.slate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.App

/** Pick 2–4 apps; each opens in its own window, tiled across the screen. */
@Composable
fun SplitSheet(slate: SlateApp, preselect: App?, onOpen: (List<App>) -> Unit, onClose: () -> Unit) {
    val apps by slate.apps.apps.collectAsState()
    val recents by slate.usage.recents.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val chosen = remember { mutableStateListOf<App>().apply { preselect?.let { add(it) } } }
    var query by remember { mutableStateOf("") }
    BackHandler { onClose() }
    val recentApps = recents.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(8)
    val list = remember(apps, query, prefs.hidden) {
        apps.filter { it.key !in prefs.hidden && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
    }
    fun toggle(a: App) { if (a in chosen) chosen.remove(a) else if (chosen.size < 4) chosen.add(a) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth(0.88f).fillMaxHeight(0.84f)
                .glass(RoundedCornerShape(30.dp), Slate.Sheet).border(1.dp, Slate.CardEdge, RoundedCornerShape(30.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(26.dp),
        ) {
            // Left: pick apps
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Txt("Split View", size = 26.sp, weight = FontWeight.SemiBold)
                Txt("Choose up to 4 apps. Each opens in its own window, side by side.", color = Slate.Muted, size = 14.sp)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp), Slate.Card).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) Txt("Search apps", color = Slate.Muted, size = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Slate.Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Slate.Ink), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.weight(1f)) {
                    if (query.isBlank() && recentApps.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Txt("RECENT", color = Slate.Faint, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(recentApps, key = { "r" + it.key }) { a -> Pick(a, chosen.indexOf(a)) { toggle(a) } }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Txt("ALL APPS", color = Slate.Faint, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        }
                    }
                    items(list, key = { it.key }) { a -> Pick(a, chosen.indexOf(a)) { toggle(a) } }
                }
            }
            Spacer(Modifier.width(26.dp))
            // Right: live layout preview
            Column(Modifier.width(300.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("Layout", color = Slate.Muted, size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                Preview(chosen)
                Spacer(Modifier.weight(1f))
                if (chosen.size >= 2) {
                    Chip("Open ${chosen.size} apps", strong = true) { onOpen(chosen.toList()) }
                } else {
                    Txt("Pick at least 2 apps", color = Slate.Faint, size = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Chip("Cancel", onClick = onClose)
            }
        }
    }
}

@Composable
private fun Pick(app: App, index: Int, onClick: () -> Unit) {
    Box(Modifier.padding(6.dp).press(onClick = onClick), contentAlignment = Alignment.TopEnd) {
        LabeledIcon(app.label, 52.dp, true) { AppIcon(app, 52.dp) }
        if (index >= 0) {
            Box(
                Modifier.offset(x = (-14).dp).size(22.dp).clip(CircleShape).background(Slate.Accent),
                contentAlignment = Alignment.Center,
            ) { Txt("${index + 1}", color = Color.Black, size = 12.sp, weight = FontWeight.Bold) }
        }
    }
}

/** A miniature of the screen showing where each app will go. */
@Composable
private fun Preview(apps: List<App>) {
    val w = 280.dp; val h = 175.dp; val g = 6.dp
    Box(Modifier.size(w, h).glass(RoundedCornerShape(14.dp), Slate.Card).padding(g)) {
        val inner = w - g * 2; val innerH = h - g * 2
        val half = (inner - g) / 2; val halfH = (innerH - g) / 2
        val tiles: List<Triple<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp, Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>>> = when (apps.size) {
            0, 1 -> listOf(Triple(0.dp, 0.dp, inner to innerH))
            2 -> listOf(Triple(0.dp, 0.dp, half to innerH), Triple(half + g, 0.dp, half to innerH))
            3 -> listOf(Triple(0.dp, 0.dp, half to innerH), Triple(half + g, 0.dp, half to halfH), Triple(half + g, halfH + g, half to halfH))
            else -> listOf(Triple(0.dp, 0.dp, half to halfH), Triple(half + g, 0.dp, half to halfH), Triple(0.dp, halfH + g, half to halfH), Triple(half + g, halfH + g, half to halfH))
        }
        tiles.forEachIndexed { i, (x, y, size) ->
            Box(
                Modifier.offset(x, y).size(size.first, size.second).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { apps.getOrNull(i)?.let { AppIcon(it, 34.dp) } }
        }
    }
}
