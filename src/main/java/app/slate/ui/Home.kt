package app.slate.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import app.slate.SlateApp
import app.slate.data.App
import app.slate.data.Categories
import app.slate.data.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

/** What the home screen asks the activity to do. */
interface Actions {
    fun launch(app: App, bounds: Rect?)
    fun details(app: App)
    fun uninstall(app: App)
    fun webSearch(q: String)
    fun openSettings()
    fun openSearch()
    fun openWidgets()
    fun toast(s: String)
}

private fun LayoutCoordinates.rootRect() = Rect(positionInRoot(), size.toSize())

private data class GridGeo(val rect: Rect, val cols: Int, val rows: Int) {
    val cellW get() = rect.width / cols
    val cellH get() = rect.height / rows
}

private data class DockSlot(val key: String, val favorite: Boolean, val rect: Rect)

private sealed interface Src {
    data class Home(val id: String) : Src
    data class Dock(val key: String) : Src
}

private data class Menu(val src: Src, val anchor: Rect)

/**
 * All the moving parts of the home screen. Gestures are handled once, at the root, and hit-tested
 * against geometry the pages report — so a drag can travel across pages and into the dock.
 */
private class HomeController(
    val slate: SlateApp,
    val context: Context,
    val pager: PagerState,
    val scope: CoroutineScope,
) {
    lateinit var actions: Actions
    var cap: (Int) -> Int = { 30 }
    var edgePx = 60f
    var slopPx = 30f
    var widthPx = 0f

    val grids = HashMap<Int, GridGeo>()
    var dockRect by mutableStateOf(Rect.Zero)
    val dockSlots = HashMap<String, DockSlot>()

    var editMode by mutableStateOf(false)
    var pressed by mutableStateOf<String?>(null)
    var haptic: () -> Unit = {}
    var menu by mutableStateOf<Menu?>(null)
    var openFolder by mutableStateOf<String?>(null)
    var libraryBox by mutableStateOf<Pair<String, List<App>>?>(null)
    var libraryMenu by mutableStateOf<App?>(null)
    val sheetOpen get() = openFolder != null || libraryBox != null || libraryMenu != null

    // drag
    var candidate by mutableStateOf<Src?>(null)
    var dragging by mutableStateOf(false)
    var dragPos by mutableStateOf(Offset.Zero)
    var pressStart = Offset.Zero
    var longPressed = false
    var mergeCandidate by mutableStateOf<String?>(null)
    var folderTarget by mutableStateOf<String?>(null)
    var overDock by mutableStateOf(false)
    var edge by mutableStateOf(0)
    private var lastHover: Pair<Int, Int>? = null

    val pages get() = slate.home.pages.value
    fun app(key: String): App? = slate.apps.apps.value.firstOrNull { it.key == key }
    private val onLibrary get() = pager.currentPage >= pages.size

    // ── hit testing ──
    private fun gridHit(pos: Offset): Triple<Int, Int, GridGeo>? {
        val p = pager.currentPage
        if (p >= pages.size) return null
        val g = grids[p] ?: return null
        if (!g.rect.contains(pos)) return null
        val col = ((pos.x - g.rect.left) / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val row = ((pos.y - g.rect.top) / g.cellH).toInt().coerceIn(0, g.rows - 1)
        return Triple(p, row * g.cols + col, g)
    }

    private fun cellRect(g: GridGeo, idx: Int): Rect {
        val l = g.rect.left + (idx % g.cols) * g.cellW
        val t = g.rect.top + (idx / g.cols) * g.cellH
        return Rect(l, t, l + g.cellW, t + g.cellH)
    }

    private fun dockHit(pos: Offset): DockSlot? = dockSlots.values.firstOrNull { it.rect.contains(pos) }

    /** Which icon is under a finger (for the press-down effect). */
    fun pressTarget(pos: Offset): String? {
        if (openFolder != null || menu != null || onLibrary) return null
        dockHit(pos)?.let { return "dock:" + it.key + it.favorite }
        val hit = gridHit(pos) ?: return null
        return pages[hit.first].items.getOrNull(hit.second)?.id
    }

    // ── gestures ──
    fun onTap(pos: Offset) {
        if (longPressed) return
        if (menu != null) { menu = null; return }
        if (sheetOpen || pager.isScrollInProgress || onLibrary) return
        dockHit(pos)?.let { s -> app(s.key)?.let { actions.launch(it, s.rect) }; return }
        val hit = gridHit(pos)
        val item = hit?.let { (p, i, _) -> pages[p].items.getOrNull(i) }
        when (item) {
            is Item.AppItem -> if (!editMode) app(item.app)?.let { actions.launch(it, cellRect(hit.third, hit.second)) }
            is Item.Folder -> openFolder = item.id
            null -> if (editMode) editMode = false
        }
    }

    fun onLongPress(pos: Offset) {
        longPressed = true
        if (sheetOpen || onLibrary || pager.isScrollInProgress) return
        haptic()
        pressStart = pos
        dockHit(pos)?.let { s ->
            candidate = Src.Dock(s.key)
            menu = Menu(Src.Dock(s.key), s.rect)
            return
        }
        val hit = gridHit(pos)
        val item = hit?.let { (p, i, _) -> pages[p].items.getOrNull(i) }
        if (item != null && hit != null) {
            candidate = Src.Home(item.id)
            if (!editMode) menu = Menu(Src.Home(item.id), cellRect(hit.third, hit.second))
        } else {
            editMode = true
        }
    }

    fun onDragMove(pos: Offset) {
        val c = candidate ?: return
        if (!dragging) {
            if (hypot(pos.x - pressStart.x, pos.y - pressStart.y) < slopPx) return
            dragging = true
            menu = null
            editMode = true
        }
        dragPos = pos
        overDock = dockRect.contains(pos)
        edge = when {
            pos.x < edgePx -> -1
            pos.x > widthPx - edgePx -> 1
            else -> 0
        }
        if (overDock) { mergeCandidate = null; return }
        val hit = gridHit(pos) ?: run { mergeCandidate = null; return }
        val (p, idx, g) = hit
        val items = pages[p].items
        val target = items.getOrNull(idx)
        val dragId = (c as? Src.Home)?.id
        // Near the centre of another icon → it may become a folder.
        val r = cellRect(g, idx)
        val nearCentre = hypot(pos.x - r.center.x, pos.y - r.center.y) < minOf(g.cellW, g.cellH) * 0.28f
        val draggedIsApp = c is Src.Dock || (dragId != null && slate.home.find(dragId) is Item.AppItem)
        mergeCandidate = if (target != null && target.id != dragId && nearCentre && draggedIsApp) target.id else null
        if (mergeCandidate != null || c !is Src.Home) return
        // Otherwise, make room: move the dragged icon to the hovered slot.
        val to = idx.coerceAtMost(if (slate.home.locate(c.id)?.first == p) items.size - 1 else items.size)
        if (lastHover == p to to) return
        lastHover = p to to
        val cur = slate.home.locate(c.id)
        if (cur != p to to) slate.home.move(c.id, p, to, cap)
    }

    fun flipPage(dir: Int) {
        val c = candidate ?: return
        val last = pages.size - 1
        val target = pager.currentPage + dir
        if (target < 0) return
        if (target > last) {
            // Past the last page: start a new one.
            if (c is Src.Home && (slate.home.locate(c.id)?.let { pages[it.first].items.size > 1 } != false)) {
                slate.home.move(c.id, last + 1, 0, cap)
            } else return
        } else if (c is Src.Home) {
            slate.home.move(c.id, target, pages[target].items.size, cap)
        }
        lastHover = null
        scope.launch { pager.animateScrollToPage(target.coerceAtMost(pages.size - 1)) }
    }

    fun onDrop() {
        val c = candidate
        val wasDragging = dragging
        candidate = null
        dragging = false
        edge = 0
        lastHover = null
        val merge = folderTarget
        mergeCandidate = null
        folderTarget = null
        val toDock = overDock
        overDock = false
        if (c == null || !wasDragging) return
        when (c) {
            is Src.Home -> {
                val item = slate.home.find(c.id)
                when {
                    merge != null -> {
                        val name = (item as? Item.AppItem)?.let { a -> folderName(a.app, merge) } ?: "Folder"
                        slate.home.merge(c.id, merge, name)
                    }
                    toDock && item is Item.AppItem -> {
                        if (slate.home.addToDock(item.app, dockIndex(dragPos))) slate.home.remove(item.id)
                        else actions.toast("The dock is full")
                    }
                }
            }
            is Src.Dock -> {
                when {
                    merge != null -> if (slate.home.mergeApp(c.key, merge, folderName(c.key, merge))) slate.home.removeFromDock(c.key)
                    toDock -> {
                        val l = slate.home.dock.value.toMutableList()
                        l.remove(c.key); l.add(dockIndex(dragPos).coerceIn(0, l.size), c.key)
                        slate.home.setDock(l)
                    }
                    else -> gridHit(dragPos)?.let { (p, idx, _) ->
                        slate.home.insert(Item.AppItem(app.slate.data.newId(), c.key), p, idx, cap)
                        slate.home.removeFromDock(c.key)
                    }
                }
            }
        }
        slate.home.compact()
    }

    private fun dockIndex(pos: Offset): Int =
        dockSlots.values.filter { it.favorite }.sortedBy { it.rect.left }.count { it.rect.center.x < pos.x }

    private fun folderName(dragKey: String, targetId: String): String {
        val target = slate.home.find(targetId)
        if (target is Item.Folder) return target.name
        val a = (target as? Item.AppItem)?.let { app(it.app) } ?: return "Folder"
        val b = app(dragKey)
        val ca = Categories.of(context, a)
        return if (b != null && Categories.of(context, b) == ca && ca != Categories.OTHER) ca else "Folder"
    }
}

@Composable
fun Home(slate: SlateApp, actions: Actions, homeSignal: Int, blurred: Boolean = false) {
    val apps by slate.apps.apps.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    val pages by slate.home.pages.collectAsState()
    val dockKeys by slate.home.dock.collectAsState()
    val recents by slate.usage.recents.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { pages.size + 1 }
    val ctl = remember { HomeController(slate, context, pager, scope) }
    val hapticFeedback = LocalHapticFeedback.current
    ctl.actions = actions
    ctl.haptic = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }

    LaunchedEffect(homeSignal) {
        ctl.menu = null; ctl.openFolder = null; ctl.editMode = false
        if (homeSignal > 0 && pager.currentPage != 0) pager.animateScrollToPage(0)
    }
    LaunchedEffect(ctl.edge, ctl.dragging) {
        if (ctl.dragging && ctl.edge != 0) { delay(600); ctl.flipPage(ctl.edge) }
    }
    LaunchedEffect(ctl.mergeCandidate) {
        ctl.folderTarget = null
        if (ctl.mergeCandidate != null) { delay(350); ctl.folderTarget = ctl.mergeCandidate }
    }
    BackHandler(enabled = ctl.editMode || ctl.menu != null || ctl.sheetOpen) {
        when {
            ctl.menu != null -> ctl.menu = null
            ctl.libraryMenu != null -> ctl.libraryMenu = null
            ctl.libraryBox != null -> ctl.libraryBox = null
            ctl.openFolder != null -> ctl.openFolder = null
            else -> ctl.editMode = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { ctl.onLongPress(it) },
                    onDrag = { change, _ -> if (ctl.candidate != null) { change.consume(); ctl.onDragMove(change.position) } },
                    onDragEnd = { ctl.onDrop() },
                    onDragCancel = { ctl.onDrop() },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pos ->
                        ctl.longPressed = false
                        ctl.pressed = ctl.pressTarget(pos)
                        tryAwaitRelease()
                        ctl.pressed = null
                    },
                    onTap = { ctl.onTap(it) },
                )
            }
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = {
                        if (dy > 80.dp.toPx() && !ctl.dragging && ctl.candidate == null && ctl.openFolder == null &&
                            pager.currentPage < ctl.pages.size
                        ) actions.openSearch()
                    },
                ) { _, d -> dy += d }
            },
    ) {
        val landscape = maxWidth > maxHeight
        val baseCols = if (landscape) prefs.density.landCols else prefs.density.landRows
        val baseRows = if (landscape) prefs.density.landRows else prefs.density.landCols
        val widgetsOn = prefs.widgetsOnHome
        fun gridOf(page: Int): Pair<Int, Int> =
            if (page == 0 && widgetsOn) {
                val span = prefs.density.widgetSpan
                if (landscape) (baseCols - span) to baseRows else baseCols to (baseRows - span)
            }
            else baseCols to baseRows
        val cap: (Int) -> Int = { p -> gridOf(p).let { it.first * it.second } }
        ctl.cap = cap
        ctl.widthPx = constraints.maxWidth.toFloat()
        ctl.edgePx = with(density) { 44.dp.toPx() }
        ctl.slopPx = with(density) { 14.dp.toPx() }
        val cap0 = cap(0)
        val capN = cap(1)
        LaunchedEffect(cap0, capN) { slate.home.repackIfCapacityChanged(cap0, capN) }

        // Wallpaper dimming
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.dim / 100f)))

        val behind = blurred || ctl.sheetOpen || ctl.menu != null
        val blur by animateDpAsState(if (behind) 26.dp else 0.dp, tween(220), label = "blur")
        val sink by animateFloatAsState(if (behind) 0.96f else 1f, tween(220), label = "sink")
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .then(if (sink < 1f) Modifier.graphicsLayer { scaleX = sink; scaleY = sink } else Modifier)
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
        ) {
            EditBar(ctl.editMode, actions, onDone = { ctl.editMode = false })
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = ctl.candidate == null,
                beyondViewportPageCount = 1,
                key = { if (it < pages.size) pages[it].id else "library" },
            ) { p ->
                if (p < pages.size) {
                    HomePage(
                        p, pages[p].items, byKey, gridOf(p), landscape, baseCols, baseRows,
                        p == 0 && widgetsOn, prefs.density.widgetSpan, (prefs.density.iconDp * prefs.iconScale).toInt(), prefs.labels, ctl, slate, actions,
                    )
                } else {
                    Library(slate, actions, landscape, onOpenBox = { t, l -> ctl.libraryBox = t to l }, onAppMenu = { ctl.libraryMenu = it })
                }
            }
            PageDots(pages.size + 1, pager.currentPage)
            Dock(
                favorites = dockKeys.mapNotNull { byKey[it] },
                recents = if (prefs.dockRecents) recents.filter { it !in dockKeys }.mapNotNull { byKey[it] }.take(3) else emptyList(),
                ctl = ctl,
                draggingKey = (ctl.candidate as? Src.Dock)?.key?.takeIf { ctl.dragging },
                iconSize = (54 * prefs.iconScale).dp,
            )
        }

        // The icon under your finger while dragging.
        if (ctl.dragging) {
            val c = ctl.candidate
            val dragged: Item? = when (c) {
                is Src.Home -> slate.home.find(c.id)
                is Src.Dock -> Item.AppItem("drag", c.key)
                null -> null
            }
            val size = (64 * prefs.iconScale).dp
            val half = with(density) { (size / 2).toPx() }
            if (dragged != null) {
                Box(
                    Modifier
                        .offset { IntOffset((ctl.dragPos.x - half).toInt(), (ctl.dragPos.y - half).toInt()) }
                        .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.95f },
                ) { ItemGlyph(dragged, byKey, size) }
            }
        }

        // Long-press menu
        ctl.menu?.let { m -> ContextMenu(m, ctl, byKey, slate, actions, maxWidth, maxHeight) }

        // Open folder
        AnimatedVisibility(
            visible = ctl.openFolder != null,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.95f),
        ) {
            val id = remember { mutableStateOf(ctl.openFolder) }.apply { if (ctl.openFolder != null) value = ctl.openFolder }.value
            val f = id?.let { slate.home.find(it) } as? Item.Folder
            if (f != null) FolderSheet(f, byKey, slate, actions, ctl)
        }

        // App Library box, opened full-size above everything
        AnimatedVisibility(
            visible = ctl.libraryBox != null,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.95f),
        ) {
            val b = remember { mutableStateOf(ctl.libraryBox) }.apply { if (ctl.libraryBox != null) value = ctl.libraryBox }.value
            if (b != null) {
                AppsSheet(
                    title = b.first, apps = b.second,
                    onDismiss = { ctl.libraryBox = null },
                    onOpen = { actions.launch(it, null) },
                    onHold = { ctl.libraryMenu = it },
                )
            }
        }
        ctl.libraryMenu?.let { a -> LibraryAppMenu(a, slate, actions, cap) { ctl.libraryMenu = null } }
    }
}

@Composable
private fun EditBar(edit: Boolean, actions: Actions, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(if (edit) 56.dp else 8.dp).padding(horizontal = 28.dp)) {
        if (edit) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("+  Widgets") { actions.openWidgets() }
                Chip("Settings") { actions.openSettings() }
            }
            Chip("Done", Modifier.align(Alignment.CenterEnd), strong = true, onClick = onDone)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, strong: Boolean = false, onClick: () -> Unit) {
    Txt(
        text, size = 15.sp, weight = FontWeight.SemiBold,
        color = if (strong) Color.Black else Slate.Ink,
        modifier = modifier
            .glass(RoundedCornerShape(50), if (strong) Slate.Ink else Slate.GlassLight)
            .press(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
private fun HomePage(
    p: Int,
    items: List<Item>,
    byKey: Map<String, App>,
    grid: Pair<Int, Int>,
    landscape: Boolean,
    baseCols: Int,
    baseRows: Int,
    withWidgets: Boolean,
    widgetSpan: Int,
    iconDp: Int,
    labels: Boolean,
    ctl: HomeController,
    slate: SlateApp,
    actions: Actions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        // One set of metrics for the whole page, so widgets line up with the icon rows and columns.
        val cellW = maxWidth / baseCols
        val cellH = maxHeight / baseRows
        val icon = iconFor(cellW, cellH, iconDp, labels)
        val block = icon + if (labels) LABEL_BLOCK else 0.dp
        val hInset = (cellW - icon) / 2
        val vInset = ((cellH - block) / 2).coerceAtLeast(0.dp)
        val m = WidgetMetrics(unit = cellH, block = block, gap = cellH - block, hGap = cellW - icon)
        if (withWidgets) {
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    WidgetStack(
                        slate, actions, ctl.editMode, m,
                        Modifier.width(cellW * widgetSpan).fillMaxHeight().padding(start = hInset, end = hInset, top = vInset),
                    )
                    Grid(p, items, byKey, grid, icon, labels, ctl, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    WidgetRow(
                        slate, actions, ctl.editMode, m,
                        Modifier.height(cellH * widgetSpan).fillMaxWidth().padding(horizontal = hInset, vertical = vInset),
                    )
                    Grid(p, items, byKey, grid, icon, labels, ctl, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Grid(p, items, byKey, grid, icon, labels, ctl, Modifier.fillMaxSize())
        }
    }
}

/** Space under an icon taken by its name. */
val LABEL_BLOCK = 24.dp

fun iconFor(cellW: Dp, cellH: Dp, capDp: Int, labels: Boolean): Dp =
    min(min(cellW * 0.62f, (cellH - if (labels) LABEL_BLOCK else 0.dp) * 0.82f), capDp.dp)

@Composable
private fun Grid(
    p: Int,
    items: List<Item>,
    byKey: Map<String, App>,
    grid: Pair<Int, Int>,
    icon: Dp,
    labels: Boolean,
    ctl: HomeController,
    modifier: Modifier,
) {
    val (cols, rows) = grid
    BoxWithConstraints(modifier.onGloballyPositioned { ctl.grids[p] = GridGeo(it.rootRect(), cols, rows) }) {
        val cw = maxWidth / cols
        val ch = maxHeight / rows
        val draggingId = (ctl.candidate as? Src.Home)?.id?.takeIf { ctl.dragging }
        items.forEachIndexed { i, item ->
            key(item.id) {
                val target = with(LocalDensity.current) {
                    IntOffset((cw * (i % cols)).roundToPx(), (ch * (i / cols)).roundToPx())
                }
                val off by animateIntOffsetAsState(target, spring(0.8f, Spring.StiffnessMediumLow), label = "slot")
                Box(
                    Modifier.offset { off }.size(cw, ch).graphicsLayer { alpha = if (item.id == draggingId) 0f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    HomeItem(item, byKey, icon, labels, ctl.editMode, ctl.folderTarget == item.id, ctl.pressed == item.id) {
                        ctl.slate.home.remove(item.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeItem(
    item: Item,
    byKey: Map<String, App>,
    icon: Dp,
    labels: Boolean,
    jiggle: Boolean,
    highlight: Boolean,
    pressed: Boolean,
    onRemove: () -> Unit,
) {
    val label = when (item) {
        is Item.AppItem -> byKey[item.app]?.label ?: ""
        is Item.Folder -> item.name
    }
    val phase = remember { Random.nextFloat() }
    val wobble = if (jiggle) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            -1.6f, 1.6f, infiniteRepeatable(tween(130 + (phase * 40).toInt(), easing = LinearEasing), RepeatMode.Reverse),
            label = "w",
        ).value
    } else 0f
    val grow by animateFloatAsState(
        when { highlight -> 1.12f; pressed -> 0.9f; else -> 1f },
        spring(0.55f, Spring.StiffnessMedium), label = "grow",
    )
    Box {
        LabeledIcon(
            label, icon, labels,
            Modifier.graphicsLayer { rotationZ = wobble; scaleX = grow; scaleY = grow },
        ) { ItemGlyph(item, byKey, icon) }
        if (jiggle) {
            Box(
                Modifier
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xE6505058))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(10.dp).height(2.dp).background(Slate.Ink)) }
        }
    }
}

@Composable
private fun ItemGlyph(item: Item, byKey: Map<String, App>, size: Dp) {
    when (item) {
        is Item.AppItem -> byKey[item.app]?.let { AppIcon(it, size) } ?: Spacer(Modifier.size(size))
        is Item.Folder -> FolderIcon(item.apps.mapNotNull { byKey[it] }, size)
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            if (i == count - 1) {
                // The App Library: a tiny 2x2 glyph instead of a dot.
                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            repeat(2) {
                                Box(Modifier.size(3.5.dp).clip(CircleShape).background(if (on) Slate.Ink else Slate.Faint))
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Slate.Ink else Slate.Faint))
            }
        }
    }
}

@Composable
private fun Dock(favorites: List<App>, recents: List<App>, ctl: HomeController, draggingKey: String?, iconSize: Dp) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        val glow by animateFloatAsState(if (ctl.overDock) 1f else 0f, label = "dockglow")
        Row(
            Modifier
                .onGloballyPositioned { ctl.dockRect = it.rootRect() }
                .glass(RoundedCornerShape(30.dp), Color(0x66000000))
                .border(1.dp, Color.White.copy(alpha = 0.10f + 0.25f * glow), RoundedCornerShape(30.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (favorites.isEmpty() && recents.isEmpty()) {
                Txt("Drag apps here", color = Slate.Muted, size = 14.sp, modifier = Modifier.padding(horizontal = 40.dp, vertical = 18.dp))
            }
            favorites.forEach { a -> DockIcon(a, true, ctl, a.key == draggingKey, iconSize) }
            if (recents.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.25f)))
                recents.forEach { a -> DockIcon(a, false, ctl, false, iconSize) }
            }
        }
    }
}

@Composable
private fun DockIcon(app: App, favorite: Boolean, ctl: HomeController, hidden: Boolean, size: Dp) {
    val slot = (if (favorite) "f:" else "r:") + app.key
    DisposableEffect(slot) { onDispose { ctl.dockSlots.remove(slot) } }
    AppIcon(
        app, size,
        Modifier
            .onGloballyPositioned { ctl.dockSlots[slot] = DockSlot(app.key, favorite, it.rootRect()) }
            .graphicsLayer {
                alpha = if (hidden) 0.25f else 1f
                val sc = if (ctl.pressed == "dock:" + app.key + favorite) 0.9f else 1f
                scaleX = sc; scaleY = sc
            },
    )
}

// ── Long-press menu ──────────────────────────────────────────────────────

@Composable
private fun ContextMenu(
    m: Menu,
    ctl: HomeController,
    byKey: Map<String, App>,
    slate: SlateApp,
    actions: Actions,
    screenW: Dp,
    screenH: Dp,
) {
    val density = LocalDensity.current
    val close = { ctl.menu = null }
    data class Row(val text: String, val danger: Boolean = false, val run: () -> Unit)
    val rows = mutableListOf<Row>()
    when (val s = m.src) {
        is Src.Home -> when (val item = slate.home.find(s.id)) {
            is Item.AppItem -> {
                val a = byKey[item.app]
                if (!slate.home.inDock(item.app)) rows += Row("Add to Dock") {
                    if (slate.home.addToDock(item.app)) slate.home.remove(item.id) else actions.toast("The dock is full")
                }
                rows += Row("Remove from Home") { slate.home.remove(item.id) }
                rows += Row("Edit Home Screen") { ctl.editMode = true }
                if (a != null) {
                    rows += Row("App Info") { actions.details(a) }
                    rows += Row("Uninstall", danger = true) { actions.uninstall(a) }
                }
            }
            is Item.Folder -> {
                rows += Row("Open & Rename") { ctl.openFolder = item.id }
                rows += Row("Unpack Folder") { slate.home.unpackFolder(item.id, ctl.cap) }
                rows += Row("Edit Home Screen") { ctl.editMode = true }
            }
            null -> {}
        }
        is Src.Dock -> {
            val a = byKey[s.key]
            if (slate.home.inDock(s.key)) rows += Row("Remove from Dock") { slate.home.removeFromDock(s.key) }
            else rows += Row("Keep in Dock") { if (!slate.home.addToDock(s.key)) actions.toast("The dock is full") }
            rows += Row("Edit Home Screen") { ctl.editMode = true }
            if (a != null) {
                rows += Row("App Info") { actions.details(a) }
                rows += Row("Uninstall", danger = true) { actions.uninstall(a) }
            }
        }
    }
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { close() }) {
        val anchor = m.anchor
        val menuW = 240.dp
        val menuH = (rows.size * 48 + 16).dp
        val x = with(density) { (anchor.center.x.toDp() - menuW / 2).coerceIn(12.dp, screenW - menuW - 12.dp) }
        val below = with(density) { anchor.bottom.toDp() + 6.dp }
        val y = if (below + menuH < screenH - 16.dp) below else with(density) { anchor.top.toDp() - menuH - 6.dp }
        Column(
            Modifier
                .offset(x, y)
                .width(menuW)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp), Slate.Sheet)
                .padding(vertical = 8.dp),
        ) {
            rows.forEach { r ->
                Txt(
                    r.text, size = 16.sp, color = if (r.danger) Slate.Danger else Slate.Ink,
                    modifier = Modifier.fillMaxWidth().press { close(); r.run() }.padding(horizontal = 18.dp, vertical = 13.dp),
                )
            }
        }
    }
}

// ── Sheets ───────────────────────────────────────────────────────────────

/** A frosted panel of apps with a title: App Library boxes. */
@Composable
private fun AppsSheet(title: String, apps: List<App>, onDismiss: () -> Unit, onOpen: (App) -> Unit, onHold: (App) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(0.78f)
                .clickable(remember { MutableInteractionSource() }, null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(title, size = 30.sp, weight = FontWeight.SemiBold, shadow = true, modifier = Modifier.padding(bottom = 18.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier
                    .glass(RoundedCornerShape(36.dp), Color(0xB31C1C22))
                    .border(1.dp, Slate.CardEdge, RoundedCornerShape(36.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp)
                    .heightIn(max = 540.dp),
            ) {
                items(apps, key = { it.key }) { a ->
                    LabeledIcon(
                        a.label, 62.dp, true,
                        Modifier.padding(vertical = 12.dp).press(onLongClick = { onHold(a) }) { onOpen(a); onDismiss() },
                    ) { AppIcon(a, 62.dp) }
                }
            }
        }
    }
}

@Composable
private fun LibraryAppMenu(a: App, slate: SlateApp, actions: Actions, cap: (Int) -> Int, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(290.dp).shadow(28.dp, RoundedCornerShape(20.dp))
                .glass(RoundedCornerShape(20.dp), Slate.Sheet).padding(vertical = 8.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a, 44.dp)
                Spacer(Modifier.width(12.dp))
                Txt(a.label, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
            }
            val rows = buildList<Triple<String, Boolean, () -> Unit>> {
                if (!slate.home.onHome(a.key)) add(Triple("Add to Home Screen", false) {
                    val pages = slate.home.pages.value
                    slate.home.insert(Item.AppItem(app.slate.data.newId(), a.key), pages.size - 1, Int.MAX_VALUE, cap)
                    actions.toast("Added to your home screen")
                })
                if (!slate.home.inDock(a.key)) add(Triple("Add to Dock", false) {
                    if (!slate.home.addToDock(a.key)) actions.toast("The dock is full")
                })
                add(Triple("App Info", false) { actions.details(a) })
                add(Triple("Uninstall", true) { actions.uninstall(a) })
            }
            rows.forEach { (t, danger, run) ->
                Txt(
                    t, size = 16.sp, color = if (danger) Slate.Danger else Slate.Ink,
                    modifier = Modifier.fillMaxWidth().press { onDismiss(); run() }.padding(horizontal = 18.dp, vertical = 13.dp),
                )
            }
        }
    }
}

// ── Folder sheet ─────────────────────────────────────────────────────────

@Composable
private fun FolderSheet(f: Item.Folder, byKey: Map<String, App>, slate: SlateApp, actions: Actions, ctl: HomeController) {
    var name by remember(f.id) { mutableStateOf(f.name) }
    var holding by remember { mutableStateOf<App?>(null) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null) { ctl.openFolder = null },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.72f)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it; if (it.isNotBlank()) slate.home.renameFolder(f.id, it.trim()) },
                singleLine = true,
                textStyle = TextStyle(color = Slate.Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, shadow = Slate.LabelShadow),
                cursorBrush = SolidColor(Slate.Ink),
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            val apps = f.apps.mapNotNull { byKey[it] }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(36.dp), Color(0xB31C1C22))
                    .border(1.dp, Slate.CardEdge, RoundedCornerShape(36.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp)
                    .heightIn(max = 540.dp),
            ) {
                items(apps, key = { it.key }) { a ->
                    LabeledIcon(
                        a.label, 64.dp, true,
                        Modifier.padding(vertical = 10.dp).press(onLongClick = { holding = a }) { actions.launch(a, null) },
                    ) { AppIcon(a, 64.dp) }
                }
            }
            holding?.let { a ->
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Move “${a.label}” out") { slate.home.removeFromFolder(f.id, a.key, ctl.cap); holding = null }
                    Chip("App Info") { actions.details(a); holding = null }
                    Chip("Cancel") { holding = null }
                }
            }
        }
    }
}
