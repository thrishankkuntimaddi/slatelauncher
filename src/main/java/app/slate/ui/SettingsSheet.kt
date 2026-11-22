package app.slate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.Density
import app.slate.data.IconStyle

/** Settings, as a two-pane sheet that uses the tablet's width. */
@Composable
fun SettingsSheet(
    slate: SlateApp,
    isDefault: Boolean,
    onMakeDefault: () -> Unit,
    onWallpaper: () -> Unit,
    onSystemSettings: () -> Unit,
    onResetLayout: () -> Unit,
    onClose: () -> Unit,
) {
    val prefs by slate.settings.prefs.collectAsState()
    val apps by slate.apps.apps.collectAsState()
    var section by remember { mutableStateOf("Appearance") }
    BackHandler { onClose() }
    val sections = listOf("Appearance", "Home Screen", "Hidden Apps", "System")

    Box(Modifier.fillMaxSize().background(Color(0xF0121216)).statusBarsPadding()) {
        Row(Modifier.fillMaxSize()) {
            // Sidebar
            Column(Modifier.width(280.dp).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Settings", size = 32.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                sections.forEach { s ->
                    val on = s == section
                    Txt(
                        s, size = 17.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) Slate.Ink else Slate.Muted,
                        modifier = Modifier.fillMaxWidth()
                            .glass(RoundedCornerShape(12.dp), if (on) Slate.SheetRaised else Color.Transparent)
                            .press { section = s }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Chip("Done", strong = true, onClick = onClose)
            }
            Box(Modifier.width(1.dp).fillMaxSize().background(Color(0x22FFFFFF)))
            // Detail
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            ) {
                Column(Modifier.widthIn(max = 680.dp)) {
                    Txt(section, size = 26.sp, weight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))
                    when (section) {
                        "Appearance" -> {
                            Group("Icon size") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    listOf(0.75f to "Smaller", 0.85f to "Small", 1.0f to "Medium", 1.15f to "Large").forEach { (v, name) ->
                                        Chip(name, strong = kotlin.math.abs(prefs.iconScale - v) < 0.01f) {
                                            slate.icons.clear(); slate.settings.update { it.copy(iconScale = v) }
                                        }
                                    }
                                }
                            }
                            Group("Icons") {
                                Choice("Full colour", prefs.iconStyle == IconStyle.Colour) { slate.icons.clear(); slate.settings.update { it.copy(iconStyle = IconStyle.Colour) } }
                                Choice("Monochrome", prefs.iconStyle == IconStyle.Mono) { slate.icons.clear(); slate.settings.update { it.copy(iconStyle = IconStyle.Mono) } }
                                Toggle("Show app names", prefs.labels) { v -> slate.settings.update { it.copy(labels = v) } }
                            }
                            Group("Wallpaper") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    listOf(0, 15, 30, 45).forEach { d ->
                                        Chip(if (d == 0) "No dimming" else "Dim $d%", strong = prefs.dim == d) {
                                            slate.settings.update { it.copy(dim = d) }
                                        }
                                    }
                                }
                                Action("Change wallpaper…", onWallpaper)
                            }
                        }
                        "Home Screen" -> {
                            Group("Grid") {
                                Density.entries.forEach { d ->
                                    Choice(d.label, prefs.density == d) { slate.settings.update { it.copy(density = d) } }
                                }
                            }
                            Group("Layout") {
                                Toggle("Widgets on the first page", prefs.widgetsOnHome) { v -> slate.settings.update { it.copy(widgetsOnHome = v) } }
                                Toggle("Recent apps in the dock", prefs.dockRecents) { v -> slate.settings.update { it.copy(dockRecents = v) } }
                                Action("Rebuild home screen from my apps", onResetLayout)
                            }
                        }
                        "Hidden Apps" -> {
                            Txt("Hidden apps don't appear in the App Library or search.", color = Slate.Muted, size = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                            Group(null) {
                                apps.forEach { a ->
                                    Row(
                                        Modifier.fillMaxWidth().press {
                                            slate.settings.update { p -> p.copy(hidden = if (a.key in p.hidden) p.hidden - a.key else p.hidden + a.key) }
                                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppIcon(a, 34.dp)
                                        Spacer(Modifier.width(14.dp))
                                        Txt(a.label, size = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        Switch(a.key in prefs.hidden)
                                    }
                                }
                            }
                        }
                        "System" -> {
                            Group(null) {
                                if (!isDefault) Action("Make Slate your home screen", onMakeDefault)
                                else Txt("Slate is your home screen", color = Slate.Muted, size = 16.sp, modifier = Modifier.padding(16.dp))
                                Action("Tablet settings", onSystemSettings)
                            }
                            Txt("Slate 0.1 · part of NoLauncher", color = Slate.Faint, size = 13.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    if (title != null) Txt(title.uppercase(), color = Slate.Faint, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), Slate.SheetRaised).padding(vertical = 4.dp)) { content() }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, size = 16.sp, modifier = Modifier.weight(1f))
        if (on) Txt("✓", color = Slate.Accent, size = 18.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().press { onChange(!on) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, size = 16.sp, modifier = Modifier.weight(1f))
        Switch(on)
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Txt(text, color = Slate.Accent, size = 16.sp, modifier = Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp))
}

@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier.width(46.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (on) Color(0xFF34C759) else Color(0xFF48484F)).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White)) }
}
