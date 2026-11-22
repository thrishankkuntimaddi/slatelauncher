package app.slate.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IconStyle { Colour, Mono }
/** Landscape grid; portrait swaps rows and columns, so pages hold the same apps either way. */
enum class Density(val label: String, val landCols: Int, val landRows: Int) {
    Large("Large icons · 6 × 4", 6, 4),
    Medium("Medium · 6 × 5", 6, 5),
    Small("Small · 8 × 6", 8, 6),
}

data class Prefs(
    val iconStyle: IconStyle = IconStyle.Colour,
    val labels: Boolean = true,
    val density: Density = Density.Large,
    val dim: Int = 15,              // wallpaper dimming, percent
    val widgetsOnHome: Boolean = true,
    val dockRecents: Boolean = true,
    val hidden: Set<String> = emptySet(),
)

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("slate_settings", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(
        Prefs(
            iconStyle = runCatching { IconStyle.valueOf(sp.getString("icon_style", null)!!) }.getOrDefault(IconStyle.Colour),
            labels = sp.getBoolean("labels", true),
            density = runCatching { Density.valueOf(sp.getString("density", null)!!) }.getOrDefault(Density.Large),
            dim = sp.getInt("dim", 15),
            widgetsOnHome = sp.getBoolean("widgets", true),
            dockRecents = sp.getBoolean("dock_recents", true),
            hidden = sp.getStringSet("hidden", emptySet()).orEmpty().toSet(),
        )
    )
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    fun update(f: (Prefs) -> Prefs) {
        val p = f(_prefs.value)
        _prefs.value = p
        sp.edit {
            putString("icon_style", p.iconStyle.name)
            putBoolean("labels", p.labels)
            putString("density", p.density.name)
            putInt("dim", p.dim)
            putBoolean("widgets", p.widgetsOnHome)
            putBoolean("dock_recents", p.dockRecents)
            putStringSet("hidden", p.hidden)
        }
    }
}
