package com.guard.notifyguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.service.notification.Adjustment
import android.service.notification.NotificationAssistantService
import android.service.notification.StatusBarNotification

/**
 * Необязательный слой. Вызывается ДО показа уведомления и понижает его
 * важность, поэтому баннер и звук не появляются вовсе — в отличие от
 * GuardNotificationListener, который успевает только снять уже показанное.
 *
 * Работает, только если приложение выбрано ассистентом уведомлений
 * в настройках системы. На части прошивок этот выбор недоступен —
 * тогда сервис просто не запускается, а фильтр работает через listener.
 */
class GuardAssistantService : NotificationAssistantService() {

    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onNotificationEnqueued(sbn: StatusBarNotification): Adjustment? =
        adjust(sbn)

    override fun onNotificationEnqueued(
        sbn: StatusBarNotification,
        channel: NotificationChannel
    ): Adjustment? = adjust(sbn)

    private fun adjust(sbn: StatusBarNotification): Adjustment? {
        if (!prefs.filterEnabled) return null
        if (sbn.packageName == packageName) return null

        val verdict = runCatching { FilterRules.decide(sbn, prefs) }.getOrNull() ?: return null
        if (!verdict.block) return null

        val signals = Bundle().apply {
            // Adjustment.KEY_IMPORTANCE = "key_importance"
            putInt("key_importance", NotificationManager.IMPORTANCE_MIN)
        }
        return Adjustment(sbn.packageName, sbn.key, signals, verdict.reason, sbn.user)
    }

    override fun onNotificationSnoozedUntilContext(sbn: StatusBarNotification, hint: String) = Unit
    override fun onNotificationDirectReplied(key: String) = Unit
    override fun onNotificationPosted(sbn: StatusBarNotification) = Unit
    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
