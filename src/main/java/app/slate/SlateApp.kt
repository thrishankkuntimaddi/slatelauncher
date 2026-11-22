package app.slate

import android.app.Application
import app.slate.data.AppRepo
import app.slate.data.HomeStore
import app.slate.data.IconCache
import app.slate.data.Settings
import app.slate.data.Usage

class SlateApp : Application() {
    lateinit var apps: AppRepo private set
    lateinit var icons: IconCache private set
    lateinit var home: HomeStore private set
    lateinit var settings: Settings private set
    lateinit var usage: Usage private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepo(this)
        icons = IconCache(this, apps)
        home = HomeStore(this)
        settings = Settings(this)
        usage = Usage(this)
    }
}
