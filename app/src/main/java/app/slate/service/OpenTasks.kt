package app.slate.service

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The app names last seen on the system Recents screen, and when. Written by [GestureService]
 * (its own process), read by the launcher. A plain file, since SharedPreferences don't sync
 * between processes.
 */
object OpenTasks {
    const val ACTION_CHANGED = "app.slate.action.TASKS_CHANGED"

    data class Snapshot(val at: Long, val labels: Set<String>)

    private fun file(context: Context) = File(context.filesDir, "open_tasks.json")

    fun write(context: Context, labels: List<String>) {
        val old = read(context)
        val set = labels.map { it.lowercase() }.toSet()
        if (old != null && old.labels == set) return
        runCatching {
            val tmp = File(context.filesDir, "open_tasks.tmp")
            tmp.writeText(JSONObject().put("at", System.currentTimeMillis()).put("labels", JSONArray(set.toList())).toString())
            tmp.renameTo(file(context))
        }
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    fun read(context: Context): Snapshot? = runCatching {
        val o = JSONObject(file(context).readText())
        val a = o.getJSONArray("labels")
        Snapshot(o.getLong("at"), (0 until a.length()).map { a.getString(it) }.toSet())
    }.getOrNull()
}
