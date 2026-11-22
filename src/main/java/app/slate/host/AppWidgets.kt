package app.slate.host

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle

/** Hosts real Android widgets from installed apps (Calendar, Keep, Gmail, …). */
class AppWidgets(context: Context) {
    private val app = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    val host = AppWidgetHost(app, 0x51A7E)

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedWith(compareBy({ it.loadLabel(app.packageManager).lowercase() }))

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return host.createView(context, id, info)
    }

    fun resize(id: Int, wDp: Int, hDp: Int) {
        runCatching {
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
            })
        }
    }

    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }
}
