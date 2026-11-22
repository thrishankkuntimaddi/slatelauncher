package app.slate

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification

/** Posts a notification when a focus/break session ends, even if Slate isn't on screen. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Focus timer", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Slate")
            .setContentText(intent.getStringExtra("msg") ?: "Timer finished")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(42, n) }
    }

    companion object {
        private const val CHANNEL = "focus_timer"

        private fun pending(context: Context, msg: String?) = PendingIntent.getBroadcast(
            context, 7, Intent(context, TimerReceiver::class.java).putExtra("msg", msg),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun schedule(context: Context, at: Long, msg: String) {
            val am = context.getSystemService(AlarmManager::class.java)
            runCatching { am.setWindow(AlarmManager.RTC_WAKEUP, at, 30_000, pending(context, msg)) }
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(pending(context, null))
        }
    }
}
