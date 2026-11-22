package app.slate.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class Item {
    abstract val id: String
    data class AppItem(override val id: String, val app: String) : Item()
    data class Folder(override val id: String, val name: String, val apps: List<String>) : Item()
}

data class Page(val id: String, val items: List<Item>)

data class WidgetSpec(val id: String, val type: String, val appWidgetId: Int = -1)

fun newId(): String = UUID.randomUUID().toString().take(10)

/**
 * The home screen: pages of apps and folders, the dock, and the widget stack.
 * Page capacity depends on screen size and density, so callers pass [cap] (page index → slots).
 */
class HomeStore(context: Context) {
    private val sp = context.getSharedPreferences("slate_home", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveJob: Job? = null

    private val _pages = MutableStateFlow(readPages())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _dock = MutableStateFlow(sp.getString("dock", "").orEmpty().split('\n').filter { it.isNotBlank() })
    val dock: StateFlow<List<String>> = _dock.asStateFlow()

    private val _widgets = MutableStateFlow(readWidgets())
    val widgets: StateFlow<List<WidgetSpec>> = _widgets.asStateFlow()

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit { putBoolean("seeded", v) }

    // ── Queries ────────────────────────────────────────────────────────────

    fun locate(id: String): Pair<Int, Int>? {
        _pages.value.forEachIndexed { p, page ->
            val i = page.items.indexOfFirst { it.id == id }
            if (i >= 0) return p to i
        }
        return null
    }

    fun find(id: String): Item? = _pages.value.firstNotNullOfOrNull { p -> p.items.firstOrNull { it.id == id } }

    fun onHome(appKey: String): Boolean = _pages.value.any { p ->
        p.items.any { (it is Item.AppItem && it.app == appKey) || (it is Item.Folder && appKey in it.apps) }
    }

    // ── Page edits ─────────────────────────────────────────────────────────

    /** Move an item to [toPage]/[toIndex]; anything pushed past a page's capacity flows onward. */
    fun move(id: String, toPage: Int, toIndex: Int, cap: (Int) -> Int) {
        val pages = mutablePages()
        val (fp, fi) = locate(id) ?: return
        val item = pages[fp].second.removeAt(fi)
        while (pages.size <= toPage) pages.add(newId() to mutableListOf())
        pages[toPage].second.add(toIndex.coerceIn(0, pages[toPage].second.size), item)
        overflow(pages, cap)
        commit(pages, dropEmpty = false)
    }

    /** Insert a new item (from dock or library) at a spot. */
    fun insert(item: Item, toPage: Int, toIndex: Int, cap: (Int) -> Int) {
        val pages = mutablePages()
        while (pages.size <= toPage) pages.add(newId() to mutableListOf())
        pages[toPage].second.add(toIndex.coerceIn(0, pages[toPage].second.size), item)
        overflow(pages, cap)
        commit(pages, dropEmpty = false)
    }

    /** Add apps at the end of the home screen. */
    fun addApps(keys: List<String>, cap: (Int) -> Int) {
        val pages = mutablePages()
        if (pages.isEmpty()) pages.add(newId() to mutableListOf())
        keys.forEach { k ->
            var p = pages.indexOfLast { true }
            if (pages[p].second.size >= cap(p)) { pages.add(newId() to mutableListOf()); p++ }
            pages[p].second.add(Item.AppItem(newId(), k))
        }
        commit(pages, dropEmpty = true)
    }

    fun remove(id: String) {
        val pages = mutablePages()
        pages.forEach { (_, items) -> items.removeAll { it.id == id } }
        commit(pages, dropEmpty = true)
    }

    /** Drop [dragId] onto [targetId]: two apps make a folder; an app onto a folder joins it. */
    fun merge(dragId: String, targetId: String, folderName: String): Boolean {
        if (dragId == targetId) return false
        val drag = find(dragId) as? Item.AppItem ?: return false
        val pages = mutablePages()
        var done = false
        pages.forEach { (_, items) ->
            val i = items.indexOfFirst { it.id == targetId }
            if (i >= 0) {
                when (val t = items[i]) {
                    is Item.AppItem -> { items[i] = Item.Folder(newId(), folderName, listOf(t.app, drag.app)); done = true }
                    is Item.Folder -> if (drag.app !in t.apps) { items[i] = t.copy(apps = t.apps + drag.app); done = true }
                }
            }
        }
        if (!done) return false
        pages.forEach { (_, items) -> items.removeAll { it.id == dragId } }
        commit(pages, dropEmpty = true)
        return true
    }

    /** Put an app (e.g. from the dock) into a folder or make a folder with another app. */
    fun mergeApp(appKey: String, targetId: String, folderName: String): Boolean {
        val pages = mutablePages()
        var done = false
        pages.forEach { (_, items) ->
            val i = items.indexOfFirst { it.id == targetId }
            if (i >= 0) {
                when (val t = items[i]) {
                    is Item.AppItem -> if (t.app != appKey) { items[i] = Item.Folder(newId(), folderName, listOf(t.app, appKey)); done = true }
                    is Item.Folder -> if (appKey !in t.apps) { items[i] = t.copy(apps = t.apps + appKey); done = true }
                }
            }
        }
        if (done) commit(pages, dropEmpty = true)
        return done
    }

    fun renameFolder(id: String, name: String) = updateItem(id) { (it as? Item.Folder)?.copy(name = name) ?: it }

    fun removeFromFolder(folderId: String, appKey: String, cap: (Int) -> Int) {
        val pages = mutablePages()
        pages.forEach { (_, items) ->
            val i = items.indexOfFirst { it.id == folderId }
            if (i >= 0) {
                val f = items[i] as Item.Folder
                val rest = f.apps - appKey
                when (rest.size) {
                    0 -> items.removeAt(i)
                    1 -> items[i] = Item.AppItem(newId(), rest.first())
                    else -> items[i] = f.copy(apps = rest)
                }
                items.add((i + 1).coerceAtMost(items.size), Item.AppItem(newId(), appKey))
            }
        }
        overflow(pages, cap)
        commit(pages, dropEmpty = true)
    }

    fun reorderFolder(folderId: String, apps: List<String>) = updateItem(folderId) { (it as? Item.Folder)?.copy(apps = apps) ?: it }

    fun unpackFolder(id: String, cap: (Int) -> Int) {
        val pages = mutablePages()
        pages.forEach { (_, items) ->
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) {
                val f = items.removeAt(i) as Item.Folder
                items.addAll(i, f.apps.map { Item.AppItem(newId(), it) })
            }
        }
        overflow(pages, cap)
        commit(pages, dropEmpty = true)
    }

    /** Re-chunk everything after the capacity changes (density, widgets toggled). */
    fun reflow(cap: (Int) -> Int) {
        val pages = mutablePages()
        overflow(pages, cap)
        commit(pages, dropEmpty = true)
    }

    /**
     * When the grid size changes (density, widgets on/off), re-pack every page so none is left
     * half-empty. Rotation doesn't trigger this — capacity is the same in both orientations.
     */
    fun repackIfCapacityChanged(cap0: Int, cap: Int) {
        val sig = "$cap0/$cap"
        if (sp.getString("caps", null) == sig) return
        sp.edit { putString("caps", sig) }
        val all = _pages.value.flatMap { it.items }
        val ids = _pages.value.map { it.id }
        val out = mutableListOf<Pair<String, List<Item>>>()
        var i = 0
        var p = 0
        do {
            val c = (if (p == 0) cap0 else cap).coerceAtLeast(1)
            out += (ids.getOrNull(p) ?: newId()) to all.subList(i, minOf(all.size, i + c))
            i += c; p++
        } while (i < all.size)
        commit(out, dropEmpty = true)
    }

    /** Remove apps that are gone. */
    fun prune(installed: Set<String>) {
        var changed = false
        val pages = mutablePages()
        pages.forEach { (_, items) ->
            val it = items.listIterator()
            while (it.hasNext()) {
                when (val x = it.next()) {
                    is Item.AppItem -> if (x.app !in installed) { it.remove(); changed = true }
                    is Item.Folder -> {
                        val keep = x.apps.filter { a -> a in installed }
                        if (keep.size != x.apps.size) {
                            changed = true
                            when (keep.size) {
                                0 -> it.remove()
                                1 -> it.set(Item.AppItem(newId(), keep.first()))
                                else -> it.set(x.copy(apps = keep))
                            }
                        }
                    }
                }
            }
        }
        if (changed) commit(pages, dropEmpty = true)
        if (_dock.value.any { it !in installed }) setDock(_dock.value.filter { it in installed })
    }

    /** Drop empty pages (call when a drag ends). */
    fun compact() = commit(mutablePages(), dropEmpty = true)

    fun setPages(pages: List<Page>) { _pages.value = pages; save() }

    // ── Dock ───────────────────────────────────────────────────────────────

    fun setDock(keys: List<String>) {
        _dock.value = keys.distinct().take(MAX_DOCK)
        sp.edit { putString("dock", _dock.value.joinToString("\n")) }
    }

    fun inDock(key: String) = key in _dock.value

    /** Returns false if the dock is full. */
    fun addToDock(key: String, index: Int = Int.MAX_VALUE): Boolean {
        if (key in _dock.value) return true
        if (_dock.value.size >= MAX_DOCK) return false
        val l = _dock.value.toMutableList()
        l.add(index.coerceIn(0, l.size), key)
        setDock(l)
        return true
    }

    fun removeFromDock(key: String) = setDock(_dock.value - key)

    // ── Widgets ────────────────────────────────────────────────────────────

    fun setWidgets(list: List<WidgetSpec>) {
        _widgets.value = list
        sp.edit {
            putString("widgets", JSONArray(list.map {
                JSONObject().put("id", it.id).put("type", it.type).put("awid", it.appWidgetId)
            }).toString())
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun updateItem(id: String, f: (Item) -> Item) {
        val pages = mutablePages()
        pages.forEach { (_, items) -> items.replaceAll { if (it.id == id) f(it) else it } }
        commit(pages, dropEmpty = false)
    }

    private fun mutablePages(): MutableList<Pair<String, MutableList<Item>>> =
        _pages.value.map { it.id to it.items.toMutableList() }.toMutableList()

    private fun overflow(pages: MutableList<Pair<String, MutableList<Item>>>, cap: (Int) -> Int) {
        var i = 0
        while (i < pages.size) {
            val c = cap(i).coerceAtLeast(1)
            while (pages[i].second.size > c) {
                val last = pages[i].second.removeAt(pages[i].second.size - 1)
                if (i + 1 == pages.size) pages.add(newId() to mutableListOf())
                pages[i + 1].second.add(0, last)
            }
            i++
        }
    }

    private fun commit(pages: List<Pair<String, List<Item>>>, dropEmpty: Boolean) {
        var list = pages.map { Page(it.first, it.second.toList()) }
        if (dropEmpty) list = list.filterIndexed { i, p -> i == 0 || p.items.isNotEmpty() }
        if (list.isEmpty()) list = listOf(Page(newId(), emptyList()))
        _pages.value = list
        save()
    }

    /** Debounced: drags change the layout many times a second. */
    private fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            val json = JSONArray(_pages.value.map { p ->
                JSONObject().put("id", p.id).put("items", JSONArray(p.items.map { item ->
                    when (item) {
                        is Item.AppItem -> JSONObject().put("t", "a").put("id", item.id).put("app", item.app)
                        is Item.Folder -> JSONObject().put("t", "f").put("id", item.id).put("name", item.name)
                            .put("apps", JSONArray(item.apps))
                    }
                }))
            })
            sp.edit { putString("pages", json.toString()) }
        }
    }

    private fun readPages(): List<Page> = runCatching {
        val arr = JSONArray(sp.getString("pages", "[]")!!)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            Page(o.getString("id"), (0 until items.length()).map { j ->
                val it = items.getJSONObject(j)
                if (it.getString("t") == "f") {
                    val a = it.getJSONArray("apps")
                    Item.Folder(it.getString("id"), it.getString("name"), (0 until a.length()).map { k -> a.getString(k) })
                } else Item.AppItem(it.getString("id"), it.getString("app"))
            })
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(Page(newId(), emptyList())) }

    private fun readWidgets(): List<WidgetSpec> = runCatching {
        val arr = JSONArray(sp.getString("widgets", null) ?: return@runCatching null)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WidgetSpec(o.getString("id"), o.getString("type"), o.optInt("awid", -1))
        }
    }.getOrNull() ?: listOf("clock", "calendar", "todo", "timer", "notes").map { WidgetSpec(newId(), it) }

    companion object {
        const val MAX_DOCK = 10
    }
}
