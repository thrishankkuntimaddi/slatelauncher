package app.slate

import android.app.Application
import app.slate.data.AppRepo
import app.slate.data.HomeStore
import app.slate.data.IconCache
import app.slate.data.Settings
import app.slate.data.Usage
import app.slate.data.WidgetData
import app.slate.host.AppWidgets

class SlateApp : Application() {
    lateinit var apps: AppRepo private set
    lateinit var icons: IconCache private set
    lateinit var home: HomeStore private set
    lateinit var settings: Settings private set
    lateinit var usage: Usage private set
    lateinit var widgetData: WidgetData private set
    lateinit var appWidgets: AppWidgets private set

    override fun onCreate() {
        super.onCreate()
        // The gesture service runs in its own tiny process; it needs none of the launcher's data.
        if (getProcessName().endsWith(":gestures")) return
        apps = AppRepo(this)
        icons = IconCache(this, apps)
        home = HomeStore(this)
        settings = Settings(this)
        usage = Usage(this)
        widgetData = WidgetData(this)
        appWidgets = AppWidgets(this)
    }
}
