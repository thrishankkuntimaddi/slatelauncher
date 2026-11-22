package app.slate

import android.app.ActivityOptions
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import app.slate.data.App
import app.slate.data.Categories
import app.slate.data.Item
import app.slate.data.Page
import app.slate.data.WidgetSpec
import app.slate.data.newId
import app.slate.ui.Actions
import app.slate.ui.Chip
import app.slate.ui.Home
import app.slate.ui.LocalIconCache
import app.slate.ui.LocalIconStyle
import app.slate.ui.SearchOverlay
import app.slate.ui.SettingsSheet
import app.slate.ui.Slate
import app.slate.ui.Txt
import app.slate.ui.glass

private enum class Overlay { None, Search, Settings, Widgets }

class MainActivity : ComponentActivity(), Actions {
    private val slate get() = application as SlateApp
    private var overlay by mutableStateOf(Overlay.None)
    private var homeSignal by mutableIntStateOf(0)
    private var isDefault by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by slate.settings.prefs.collectAsState()
            CompositionLocalProvider(LocalIconCache provides slate.icons, LocalIconStyle provides prefs.iconStyle) { Root() }
        }
        val sp = getSharedPreferences("slate_misc", MODE_PRIVATE)
        if (!sp.getBoolean("asked_default", false) && !checkDefault()) {
            sp.edit { putBoolean("asked_default", true) }
            requestDefault()
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault = checkDefault()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        overlay = Overlay.None
        homeSignal++
    }

    @Composable
    private fun Root() {
        val apps by slate.apps.apps.collectAsState()

        // First run: build an organised home from what's installed. Afterwards, drop uninstalled apps.
        LaunchedEffect(apps) {
            if (apps.isEmpty()) return@LaunchedEffect
            if (!slate.home.seeded) { seed(apps); slate.home.seeded = true }
            else slate.home.prune(apps.map { it.key }.toSet())
        }

        Box(Modifier.fillMaxSize()) {
            Home(slate, this@MainActivity, homeSignal)

            AnimatedVisibility(overlay == Overlay.Search, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                SearchOverlay(slate, this@MainActivity) { overlay = Overlay.None }
            }
            AnimatedVisibility(
                overlay == Overlay.Settings,
                enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 12 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 12 },
            ) {
                SettingsSheet(
                    slate, isDefault,
                    onMakeDefault = ::requestDefault,
                    onWallpaper = { safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) },
                    onSystemSettings = { safeStart(Intent(Settings.ACTION_SETTINGS)) },
                    onResetLayout = { seed(slate.apps.apps.value); overlay = Overlay.None; toast("Home screen rebuilt") },
                    onClose = { overlay = Overlay.None },
                )
            }
            AnimatedVisibility(overlay == Overlay.Widgets, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                WidgetPicker { overlay = Overlay.None }
            }
        }
    }

    @Composable
    private fun WidgetPicker(onClose: () -> Unit) {
        val widgets by slate.home.widgets.collectAsState()
        BackHandler { onClose() }
        val types = listOf("clock" to "Clock & date", "battery" to "Battery")
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .clickable(remember { MutableInteractionSource() }, null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth(0.6f).glass(RoundedCornerShape(28.dp), Slate.Sheet).padding(26.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                Txt("Widgets", size = 26.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                types.forEach { (t, name) ->
                    val on = widgets.any { it.type == t }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(name, size = 17.sp, modifier = Modifier.weight(1f))
                        Chip(if (on) "Remove" else "Add", strong = !on) {
                            slate.home.setWidgets(if (on) widgets.filter { it.type != t } else widgets + WidgetSpec(newId(), t))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Txt("More widgets are on the way.", color = Slate.Muted, size = 14.sp)
            }
        }
    }

    /** Dock: the everyday tools. Page 1: popular apps. Page 2+: one folder per category for the rest. */
    private fun seed(apps: List<App>) {
        val byPkg = apps.associateBy { it.packageName }
        fun first(vararg pkgs: String) = pkgs.firstNotNullOfOrNull { byPkg[it]?.key }
        val dock = listOfNotNull(
            first("com.android.chrome", "com.mi.globalbrowser", "com.android.browser"),
            first("com.google.android.gm"),
            first("com.whatsapp"),
            first("com.google.android.youtube"),
            first("com.google.android.apps.docs", "com.google.android.apps.nbu.files", "com.mi.android.globalFileexplorer"),
            first("com.google.android.keep", "com.miui.notes"),
            first("com.android.settings"),
        ).distinct()
        val favourites = listOf(
            "com.google.android.calendar", "com.google.android.apps.photos", "com.miui.gallery", "com.google.android.apps.maps",
            "com.spotify.music", "com.netflix.mediaclient", "com.openai.chatgpt", "com.anthropic.claude",
            "com.google.android.apps.bard", "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.discord", "com.reddit.frontpage", "com.twitter.android",
            "com.android.camera", "com.android.deskclock", "com.miui.calculator", "com.google.android.apps.youtube.music",
        ).mapNotNull { byPkg[it]?.key }.filter { it !in dock }.take(20)

        val rest = apps.filter { it.key !in dock && it.key !in favourites }
        val folders = Categories.sort(this, rest).map { (name, list) ->
            if (list.size >= 2) Item.Folder(newId(), name, list.map { it.key }) else Item.AppItem(newId(), list.first().key)
        }
        val pages = mutableListOf(Page(newId(), favourites.map { Item.AppItem(newId(), it) }))
        folders.chunked(30).forEach { pages += Page(newId(), it) }
        slate.home.setPages(pages)
        slate.home.setDock(dock)
    }

    // ── Actions ──────────────────────────────────────────────────────────

    override fun launch(app: App, bounds: Rect?) {
        val r = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        val opts = r?.let {
            ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        if (slate.apps.launch(app, r, opts)) slate.usage.record(app.key) else toast("Couldn't open ${app.label}")
    }

    override fun details(app: App) { slate.apps.details(app) }

    override fun uninstall(app: App) {
        safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)).putExtra(Intent.EXTRA_USER, app.user))
    }

    override fun webSearch(q: String) {
        if (!safeStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q), quiet = true)) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        }
    }

    override fun openSettings() { overlay = Overlay.Settings }
    override fun openSearch() { overlay = Overlay.Search }
    override fun openWidgets() { overlay = Overlay.Widgets }
    override fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun checkDefault(): Boolean =
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName

    private fun requestDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                runCatching { startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7) }.onSuccess { return }
            }
        }
        safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun safeStart(i: Intent, quiet: Boolean = false): Boolean =
        runCatching { startActivity(i) }.onFailure { if (!quiet) toast("Not available") }.isSuccess
}
