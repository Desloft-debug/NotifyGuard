package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** Что делать с уведомлением и почему. */
data class Verdict(val block: Boolean, val reason: String)

object FilterRules {

    /**
     * Пакеты, которые не трогаем ни при каких настройках:
     * система, телефон, будильник, экстренные оповещения.
     */
    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver.module",
        "com.google.android.cellbroadcastservice",
        "com.google.android.apps.safetyhub",
        "com.android.emergency"
    )

    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast",
        "emergency",
        "telecom",
        "incallui"
    )

    /** Категории Android, которые всегда важнее рекламы. */
    private val PROTECTED_CATEGORIES = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_EVENT,
        Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_PROGRESS
    )

    /** Экстренные сообщения — пропускаем всегда. */
    private val EMERGENCY_WORDS = listOf(
        "экстренн", "чрезвычайн", "тревог", "эвакуац", "штормово", "предупрежден о",
        "мчс", "сирена", "воздушная",
        "emergency", "evacuat", "amber alert", "severe weather", "tsunami", "wildfire",
        "notfall", "warnung", "katastrophen", "unwetter", "bevölkerungsschutz", "nina"
    )

    /** Коды подтверждения и деньги — то, что нужно видеть. */
    private val KEEP_WORDS = listOf(
        // коды
        "код", "одноразов", "подтвержд", "пароль", "вход в", "авториз",
        "code", "otp", "one-time", "verification", "verify", "2fa", "tan",
        "passwort", "bestätigung", "bestätigen", "verifizierung", "einmalpasswort",
        // деньги
        "перевод", "перевел", "перевела", "зачислен", "списан", "оплат", "покупка",
        "баланс", "счёт", "счет", "карта", "карты", "снятие", "пополнен", "платеж", "платёж",
        "überweisung", "gutschrift", "abbuchung", "lastschrift", "zahlung", "umsatz", "konto",
        "transfer", "transaction", "payment", "withdraw", "deposit", "received",
        "руб", "₽", "eur", "€", "usd", "$"
    )

    /** Признаки рекламы и промо. */
    private val PROMO_WORDS = listOf(
        "скидк", "акци", "распродаж", "промокод", "бонус", "кэшбэк", "кешбэк",
        "спецпредложен", "выгодн", "только сегодня", "успей", "дарим", "подарок",
        "розыгрыш", "приз", "оформи", "подключи", "оформить", "рассрочк", "кредит наличными",
        "подписк", "тариф", "реклама", "приглас", "новинк", "успейте", "бесплатно получи",
        "sale", "discount", "promo", "offer", "deal", "coupon", "% off", "limited time",
        "subscribe", "upgrade now", "free trial", "don't miss",
        "rabatt", "gutschein", "angebot", "aktion", "gewinnspiel", "kostenlos testen",
        "jetzt sichern", "nur heute"
    )

    fun decide(sbn: StatusBarNotification, prefs: Prefs): Verdict {
        val pkg = sbn.packageName.lowercase()

        if (pkg in PROTECTED_PACKAGES || PROTECTED_FRAGMENTS.any { pkg.contains(it) }) {
            return Verdict(false, "системное приложение")
        }

        val n = sbn.notification ?: return Verdict(false, "пустое уведомление")

        if (n.category in PROTECTED_CATEGORIES) {
            return Verdict(false, "категория ${n.category}")
        }
        // Постоянные уведомления (музыка, навигация, фоновые службы) не трогаем
        if (!sbn.isClearable) return Verdict(false, "несъёмное уведомление")

        val text = extractText(n.extras)

        if (EMERGENCY_WORDS.any { text.contains(it) }) {
            return Verdict(false, "экстренное сообщение")
        }
        if (pkg in prefs.blockedApps) {
            return Verdict(true, "приложение в чёрном списке")
        }
        if (pkg in prefs.allowedApps) {
            return Verdict(false, "приложение в белом списке")
        }
        // Код или перевод важнее промо, даже если в тексте есть и то и другое
        if (KEEP_WORDS.any { text.contains(it) }) {
            return Verdict(false, "код или операция по счёту")
        }
        if (PROMO_WORDS.any { text.contains(it) }) {
            return Verdict(true, "похоже на рекламу")
        }
        return if (prefs.strictMode) {
            Verdict(true, "строгий режим: приложения нет в белом списке")
        } else {
            Verdict(false, "нет признаков рекламы")
        }
    }

    /** Собирает весь видимый текст уведомления в одну строку в нижнем регистре. */
    fun extractText(extras: Bundle?): String {
        if (extras == null) return ""
        val keys = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT
        )
        val sb = StringBuilder()
        for (k in keys) {
            val v = extras.getCharSequence(k)
            if (!v.isNullOrBlank()) sb.append(v).append(' ')
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
            sb.append(it).append(' ')
        }
        return sb.toString().lowercase()
    }

    /** Короткая подпись уведомления для журнала. */
    fun shortTitle(extras: Bundle?): String {
        val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val b = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val s = listOfNotNull(t, b).joinToString(" — ").ifBlank { "без текста" }
        return if (s.length > 90) s.take(90) + "…" else s
    }
}
