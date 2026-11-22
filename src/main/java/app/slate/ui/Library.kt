package app.slate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.App
import app.slate.data.Categories
import app.slate.data.Item
import app.slate.data.newId

/**
 * App Library — the last page. Every app, automatically sorted into boxes:
 * Suggestions (what you use most), Recently Added, then categories. Search lists everything A–Z.
 */
@Composable
fun Library(slate: SlateApp, actions: Actions, landscape: Boolean, cap: (Int) -> Int) {
    val context = LocalContext.current
    val apps by slate.apps.apps.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val counts by slate.usage.counts.collectAsState()
    val visible = remember(apps, prefs.hidden) { apps.filter { it.key !in prefs.hidden } }
    val boxes = remember(visible, counts) {
        val suggestions = visible.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 }.take(8)
        val recent = visible.sortedByDescending { it.installed }.take(8)
        buildList {
            if (suggestions.size >= 3) add("Suggestions" to suggestions)
            add("Recently Added" to recent)
            addAll(Categories.sort(context, visible))
        }
    }
    var query by remember { mutableStateOf("") }
    var openBox by remember { mutableStateOf<Pair<String, List<App>>?>(null) }
    var menuFor by remember { mutableStateOf<App?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp), Slate.GlassLight)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (query.isEmpty()) Txt("App Library", color = Slate.Muted, size = 16.sp, modifier = Modifier.align(Alignment.Center))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Slate.Ink, fontSize = 16.sp, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Slate.Ink), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (query.isBlank()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (landscape) 200.dp else 190.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(boxes, key = { it.first }) { (title, list) ->
                        CategoryBox(title, list, onApp = { actions.launch(it, null) }, onHold = { menuFor = it }) {
                            openBox = title to list
                        }
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
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(results, key = { it.key }) { a ->
                        AppRow(a, onHold = { menuFor = a }) { actions.launch(a, null) }
                    }
                }
            }
        }

        AnimatedVisibility(openBox != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
            val b = remember { mutableStateOf(openBox) }.apply { if (openBox != null) value = openBox }.value
            if (b != null) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                        .clickable(remember { MutableInteractionSource() }, null) { openBox = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(0.8f)
                            .clickable(remember { MutableInteractionSource() }, null) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Txt(b.first, size = 28.sp, weight = FontWeight.SemiBold, shadow = true, modifier = Modifier.padding(bottom = 16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(118.dp),
                            modifier = Modifier.glass(RoundedCornerShape(34.dp), Color(0x8C1E1E24)).padding(20.dp)
                                .height(((b.second.size + 4) / 5 * 116 + 8).coerceAtMost(520).dp),
                        ) {
                            items(b.second, key = { it.key }) { a ->
                                LabeledIcon(
                                    a.label, 62.dp, true,
                                    Modifier.padding(vertical = 10.dp).press(onLongClick = { menuFor = a }) { actions.launch(a, null) },
                                ) { AppIcon(a, 62.dp) }
                            }
                        }
                    }
                }
            }
        }

        menuFor?.let { a ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, null) { menuFor = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.width(280.dp).glass(RoundedCornerShape(20.dp), Slate.Sheet).padding(vertical = 10.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Txt(a.label, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    }
                    val onHome = slate.home.onHome(a.key)
                    val rows = buildList<Pair<String, () -> Unit>> {
                        if (!onHome) add("Add to Home Screen" to {
                            val pages = slate.home.pages.value
                            slate.home.insert(Item.AppItem(newId(), a.key), pages.size - 1, Int.MAX_VALUE, cap)
                            actions.toast("Added to your home screen")
                        })
                        if (!slate.home.inDock(a.key)) add("Add to Dock" to {
                            if (!slate.home.addToDock(a.key)) actions.toast("The dock is full")
                        })
                        add("App Info" to { actions.details(a) })
                        add("Uninstall" to { actions.uninstall(a) })
                    }
                    rows.forEach { (t, run) ->
                        Txt(
                            t, size = 16.sp, color = if (t == "Uninstall") Slate.Danger else Slate.Ink,
                            modifier = Modifier.fillMaxWidth().press { menuFor = null; run() }.padding(horizontal = 18.dp, vertical = 13.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBox(title: String, apps: List<App>, onApp: (App) -> Unit, onHold: (App) -> Unit, onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(30.dp), Slate.GlassLight).padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slot(apps.getOrNull(0), onApp, onHold, Modifier.weight(1f))
                    Slot(apps.getOrNull(1), onApp, onHold, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slot(apps.getOrNull(2), onApp, onHold, Modifier.weight(1f))
                    // The fourth corner: a cluster of the rest; tap to see everything.
                    Box(Modifier.weight(1f).fillMaxSize().press(onClick = onOpen), contentAlignment = Alignment.Center) {
                        val rest = apps.drop(3).take(4)
                        if (apps.size == 4) {
                            AppIcon(apps[3], 64.dp)
                        } else if (rest.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                rest.chunked(2).forEach { r ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { r.forEach { AppIcon(it, 29.dp) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        Txt(
            title, size = 14.sp, weight = FontWeight.Medium, shadow = true, maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).press(onClick = onOpen),
        )
    }
}

@Composable
private fun Slot(app: App?, onApp: (App) -> Unit, onHold: (App) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (app != null) AppIcon(app, 64.dp, Modifier.press(onLongClick = { onHold(app) }) { onApp(app) })
    }
}

@Composable
fun AppRow(app: App, onHold: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().press(onLongClick = onHold, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 40.dp)
        Spacer(Modifier.size(14.dp))
        Txt(app.label, size = 17.sp, maxLines = 1, shadow = true)
    }
}
