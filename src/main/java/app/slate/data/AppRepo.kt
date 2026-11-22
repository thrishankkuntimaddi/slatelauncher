package app.slate.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class App(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val installed: Long,
)

/** Every launchable activity, for every user profile, kept fresh as apps come and go. */
class AppRepo(private val context: Context) {
    private val la = context.getSystemService(LauncherApps::class.java)
    private val um = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _apps = MutableStateFlow<List<App>>(emptyList())
    val apps: StateFlow<List<App>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String?, u: UserHandle?) = refresh()
        override fun onPackageAdded(p: String?, u: UserHandle?) = refresh()
        override fun onPackageChanged(p: String?, u: UserHandle?) = refresh()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
    }

    init {
        la.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        scope.launch { mutex.withLock { runCatching { load() }.onSuccess { _apps.value = it } } }
    }

    private fun load(): List<App> {
        val self = context.packageName
        return um.userProfiles.flatMap { user ->
            val serial = um.getSerialNumberForUser(user)
            la.getActivityList(null, user)
                .filter { it.applicationInfo.packageName != self }
                .map { info ->
                    App(
                        key = "${info.componentName.flattenToShortString()}#$serial",
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = user,
                        installed = info.firstInstallTime,
                    )
                }
        }.sortedBy { it.label.lowercase() }
    }

    fun activityInfo(app: App): LauncherActivityInfo? =
        runCatching { la.getActivityList(app.packageName, app.user).firstOrNull { it.componentName == app.component } }.getOrNull()

    fun launch(app: App, bounds: Rect? = null, opts: Bundle? = null): Boolean =
        runCatching { la.startMainActivity(app.component, app.user, bounds, opts) }.isSuccess

    fun details(app: App): Boolean =
        runCatching { la.startAppDetailsActivity(app.component, app.user, null, null) }.isSuccess
}
