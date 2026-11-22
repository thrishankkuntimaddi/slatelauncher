package app.slate.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Per-widget saved state (to-dos, notes, habits, settings) as small JSON blobs keyed by widget id. */
class WidgetData(context: Context) {
    private val sp = context.getSharedPreferences("slate_widget_data", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Bumps on every write so widgets recompose. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun get(id: String): JSONObject = runCatching { JSONObject(sp.getString(id, "{}")!!) }.getOrDefault(JSONObject())

    fun put(id: String, o: JSONObject) {
        sp.edit { putString(id, o.toString()) }
        _version.value++
    }

    fun update(id: String, f: (JSONObject) -> Unit) = put(id, get(id).also(f))

    fun remove(id: String) = sp.edit { remove(id) }
}
