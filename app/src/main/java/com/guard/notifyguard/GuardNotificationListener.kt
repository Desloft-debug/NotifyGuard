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

    /**
     * Ключи только что снятых уведомлений.
     *
     * onNotificationPosted прилетает и на обновление уже показанного уведомления,
     * с тем же key. Приложение, которое переставляет своё уведомление после снятия
     * (мессенджеры с липкими уведомлениями, часть банков), иначе загоняет нас
     * в цикл publish - cancel - publish: журнал вытесняется за секунды, шторка мигает.
     *
     * Обычный LRU. Доступ только из главного потока сервиса, замок не нужен.
     */
    private val recentlyHandled = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 200
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        known.addAll(prefs.seenApps)
    }

    override fun onListenerConnected() {
        alive = true
        GuardForegroundService.start(this)
        runCatching { activeNotifications?.forEach { handle(it) } }
    }

    override fun onListenerDisconnected() {
        alive = false
        // после обновления apk система отвязывает слушатель молча
        rebind(this)
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        recentlyHandled.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return

        if (known.add(pkg)) prefs.rememberApp(pkg)

        val snapshot = prefs.snapshot()
        if (!snapshot.filterEnabled) return

        val now = System.currentTimeMillis()
        val last = recentlyHandled[sbn.key]
        if (last != null && now - last < REPOST_WINDOW_MS) return

        val verdict = try {
            FilterRules.decide(sbn, snapshot)
        } catch (e: Exception) {
            Log.w(TAG, "Правила не отработали", e)
            return
        }
        if (!verdict.block) return

        recentlyHandled[sbn.key] = now

        // cancelNotification ничего не возвращает: получилось снять или нет — не узнать.
        runCatching { cancelNotification(sbn.key) }

        GuardLog.addNotification(
            this,
            LogEntry(
                pkg = pkg,
                title = if (snapshot.storeLogText) {
                    FilterRules.shortTitle(sbn.notification?.extras)
                } else "",
                reason = verdict.encode(),
                time = now
            )
        )
    }

    companion object {
        private const val TAG = "NotifyGuard"
        private const val REPOST_WINDOW_MS = 10_000L

        @Volatile
        private var alive = false

        fun component(context: Context): ComponentName =
            ComponentName(context, GuardNotificationListener::class.java)

        fun isPermitted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = component(context)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun rebind(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { requestRebind(component(context)) }
            }
        }

        // дёргается при открытии приложения и из воркера
        fun ensureBound(context: Context) {
            if (!alive && isPermitted(context)) rebind(context)
        }
    }
}
