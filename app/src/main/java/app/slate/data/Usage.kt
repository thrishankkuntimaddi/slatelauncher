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

    /** True once the user allows Usage access, so recents include apps opened from anywhere. */
    fun hasSystemAccess(context: Context): Boolean {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** Rebuild recents from Android's usage history (last 3 days of apps coming to the front). */
    fun refreshFromSystem(context: Context, apps: List<App>) {
        if (!hasSystemAccess(context) || apps.isEmpty()) return
        val usm = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 3L * 24 * 60 * 60_000, now) }.getOrNull() ?: return
        val e = android.app.usage.UsageEvents.Event()
        val order = LinkedHashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) order[e.packageName] = e.timeStamp
        }
        val byPkg = apps.groupBy { it.packageName }
        // Launchers, and system apps HyperOS opens on its own (install scans, cleanup).
        val skip = setOf(
            context.packageName, "com.miui.home", "app.nolauncher", "com.android.systemui",
            "com.miui.securitycenter", "com.miui.cleanmaster", "com.miui.cleaner", "com.miui.securityadd",
            "com.google.android.packageinstaller", "com.android.packageinstaller", "com.lbe.security.miui",
        )
        // Only apps still open: on the Recents screen when we last saw it, or opened since.
        val open = app.slate.service.OpenTasks.read(context)
        val keys = order.entries.sortedByDescending { it.value }
            .filter { it.key !in skip }
            .filter { (pkg, at) ->
                open == null || at > open.at || byPkg[pkg].orEmpty().any { it.label.lowercase() in open.labels }
            }
            .map { it.key }
            .mapNotNull { byPkg[it]?.firstOrNull()?.key }
            .distinct().take(12)
        if (keys != _recents.value) synchronized(this) {
            _recents.value = keys
            sp.edit { putString("recents", keys.joinToString("\n")) }
        }
    }

    fun record(key: String) {
        _recents.value = (listOf(key) + _recents.value.filter { it != key }).take(12)
        _counts.value = _counts.value + (key to (_counts.value[key] ?: 0) + 1)
        sp.edit {
            putString("recents", _recents.value.joinToString("\n"))
            putString("counts", JSONObject(_counts.value).toString())
        }
    }
}
