package app.slate.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Launch counts and recents — powers dock recents and the "Suggestions" box in the App Library. */
class Usage(context: Context) {
    private val sp = context.getSharedPreferences("slate_usage", Context.MODE_PRIVATE)

    private val _recents = MutableStateFlow(sp.getString("recents", "").orEmpty().split('\n').filter { it.isNotBlank() })
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _counts = MutableStateFlow(
        runCatching {
            val o = JSONObject(sp.getString("counts", "{}")!!)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    )
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun record(key: String) {
        _recents.value = (listOf(key) + _recents.value.filter { it != key }).take(12)
        _counts.value = _counts.value + (key to (_counts.value[key] ?: 0) + 1)
        sp.edit {
            putString("recents", _recents.value.joinToString("\n"))
            putString("counts", JSONObject(_counts.value).toString())
        }
    }
}
