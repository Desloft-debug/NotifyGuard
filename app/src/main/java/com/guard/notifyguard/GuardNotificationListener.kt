package com.guard.notifyguard

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class GuardNotificationListener : NotificationListenerService() {

    private lateinit var prefs: Prefs
    private val known = HashSet<String>(64)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        known.addAll(prefs.seenApps)
    }

    override fun onListenerConnected() {
        connected = true
        prefs.listenerSeenAt = System.currentTimeMillis()
        runCatching { activeNotifications?.forEach { handle(it) } }
    }

    override fun onListenerDisconnected() {
        connected = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { requestRebind(component(this)) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return

        if (known.add(pkg)) prefs.rememberApp(pkg)
        prefs.listenerSeenAt = System.currentTimeMillis()

        val snapshot = prefs.snapshot()
        if (!snapshot.filterEnabled) return

        val verdict = try {
            FilterRules.decide(sbn, snapshot)
        } catch (e: Exception) {
            Log.w(TAG, "Правила не отработали, уведомление оставлено", e)
            return
        }
        if (!verdict.block) return

        val cancelled = runCatching { cancelNotification(sbn.key) }.isSuccess

        GuardLog.addNotification(
            this,
            LogEntry(
                pkg = pkg,
                title = if (snapshot.storeLogText) {
                    FilterRules.shortTitle(sbn.notification?.extras)
                } else "",
                reason = if (cancelled) verdict.reason else verdict.reason + " (не удалось снять)",
                time = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private const val TAG = "NotifyGuard"

        // Живо ли соединение с системой.
        @Volatile
        var connected: Boolean = false
            private set

        fun component(context: Context): ComponentName =
            ComponentName(context, GuardNotificationListener::class.java)

        fun isPermitted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = component(context)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        // Разрешение выдано, но соединения нет и событий давно не было — почти наверняка сервис отвязан…
        fun looksStalled(context: Context, prefs: Prefs): Boolean {
            if (!isPermitted(context)) return false
            if (connected) return false
            val seen = prefs.listenerSeenAt
            return seen == 0L || System.currentTimeMillis() - seen > 10 * 60 * 1000
        }

        fun rebind(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { requestRebind(component(context)) }
            }
        }

        fun isEnabled(context: Context): Boolean = isPermitted(context)
    }
}
