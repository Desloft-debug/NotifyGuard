package com.guard.notifyguard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class GuardNotificationListener : NotificationListenerService() {

    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onListenerConnected() {
        runCatching { activeNotifications?.forEach { handle(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        prefs.rememberApp(sbn.packageName)

        if (!prefs.filterEnabled) return

        val verdict = try {
            FilterRules.decide(sbn, prefs)
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка правил, уведомление пропущено", e)
            return
        }
        if (!verdict.block) return

        runCatching { cancelNotification(sbn.key) }
            .onFailure { Log.w(TAG, "Не удалось снять уведомление", it) }

        GuardLog.addNotification(
            this,
            LogEntry(
                pkg = sbn.packageName,
                title = if (prefs.storeLogText) {
                    FilterRules.shortTitle(sbn.notification?.extras)
                } else "",
                reason = verdict.reason,
                time = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private const val TAG = "NotifyGuard"

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = ComponentName(context, GuardNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
