package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

data class Verdict(val block: Boolean, val reason: String)

object FilterRules {

    private val PROTECTED_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.settings",
        "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer",
        "com.android.deskclock", "com.google.android.deskclock",
        "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver.module", "com.google.android.cellbroadcastservice",
        "com.google.android.apps.safetyhub", "com.android.emergency",
        "com.google.android.gms", "com.miui.powerkeeper", "com.samsung.android.lool",
        "com.huawei.systemmanager", "com.coloros.oppoguardelf", "com.oplus.battery"
    )

    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast", "emergency", "telecom", "incallui",
        "powerkeeper", "batteryservice", "systemui", "systemupdate"
    )

    private val PROTECTED_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
        Notification.CATEGORY_EVENT, Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_ERROR,
        Notification.CATEGORY_STATUS
    )

    // Списки живут в Dictionaries. Здесь — только псевдонимы,
    // чтобы экран словаря и RemoteDictionary обращались в одно место.
    val EMERGENCY_WORDS get() = Dictionaries.EMERGENCY
    val SYSTEM_WORDS get() = Dictionaries.SYSTEM
    val DELIVERY_WORDS get() = Dictionaries.DELIVERY
    val CODE_WORDS get() = Dictionaries.CODE
    val MONEY_WORDS get() = Dictionaries.MONEY

    fun decide(sbn: StatusBarNotification, s: Snapshot): Verdict {
        val pkg = sbn.packageName.lowercase()

        if (pkg in PROTECTED_PACKAGES || PROTECTED_FRAGMENTS.any { pkg.contains(it) }) {
            return ALLOW_SYSTEM
        }

        val n = sbn.notification ?: return ALLOW_EMPTY
        if (n.category in PROTECTED_CATEGORIES) return Verdict(false, "категория ${n.category}")
        if (!sbn.isClearable) return ALLOW_ONGOING
        if (n.category == Notification.CATEGORY_PROMO) return BLOCK_PROMO_CATEGORY

        val text = extractText(n.extras)
        if (text.isEmpty()) return ALLOW_EMPTY

        match(text, Dictionaries.EMERGENCY)?.let { return Verdict(false, "экстренное сообщение: «$it»") }
        match(text, Dictionaries.SYSTEM)?.let { return Verdict(false, "состояние устройства: «$it»") }
        match(text, s.allowWords)?.let { return Verdict(false, "ваше слово-исключение: «$it»") }
        match(text, s.blockWords)?.let { return Verdict(true, "ваше стоп-слово: «$it»") }
        match(text, s.remoteAllow)?.let { return Verdict(false, "онлайн-исключение: «$it»") }

        if (pkg in s.blockedApps) return BLOCK_APP
        if (pkg in s.allowedApps) return ALLOW_APP
        if (isPersonalMessage(n)) return ALLOW_MESSAGE

        match(text, Dictionaries.DELIVERY)?.let { return Verdict(false, "статус заказа: «$it»") }
        match(text, Dictionaries.CODE)?.let { return Verdict(false, "код подтверждения: «$it»") }
        match(text, Dictionaries.MONEY)?.let { return Verdict(false, "операция по счёту: «$it»") }
        match(text, s.remoteBlock)?.let { return Verdict(true, "онлайн-словарь: «$it»") }
        match(text, s.promoWords)?.let { return Verdict(true, "рекламное слово: «$it»") }

        return if (s.strictMode) BLOCK_STRICT else ALLOW_CLEAN
    }

    private val ALLOW_SYSTEM = Verdict(false, "системное приложение")
    private val ALLOW_EMPTY = Verdict(false, "нет текста")
    private val ALLOW_ONGOING = Verdict(false, "несъёмное уведомление")
    private val ALLOW_APP = Verdict(false, "приложение в белом списке")
    private val ALLOW_MESSAGE = Verdict(false, "личное сообщение")
    private val ALLOW_CLEAN = Verdict(false, "нет признаков рекламы")
    private val BLOCK_APP = Verdict(true, "приложение в чёрном списке")
    private val BLOCK_STRICT = Verdict(true, "строгий режим")
    private val BLOCK_PROMO_CATEGORY = Verdict(true, "категория «реклама»")

    private fun isPersonalMessage(n: Notification): Boolean {
        val extras = n.extras ?: return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("MessagingStyle", true) ||
            extras.containsKey(Notification.EXTRA_MESSAGES)
    }

    private val cache = ConcurrentHashMap<String, Regex>()

    private fun match(text: String, words: List<String>): String? {
        for (w in words) {
            if (regexFor(w).containsMatchIn(text)) return w
        }
        return null
    }

    private fun regexFor(needle: String): Regex =
        cache.getOrPut(needle) { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(needle)) }

    fun matches(text: String, word: String): Boolean =
        regexFor(word.trim().lowercase()).containsMatchIn(text.lowercase())

    fun extractText(extras: Bundle?): String {
        if (extras == null) return ""
        val sb = StringBuilder(96)
        for (k in TEXT_KEYS) {
            val v = extras.getCharSequence(k)
            if (!v.isNullOrBlank()) sb.append(v).append(' ')
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
            sb.append(it).append(' ')
        }
        return sb.toString().lowercase()
    }

    private val TEXT_KEYS = arrayOf(
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TITLE_BIG,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
        Notification.EXTRA_INFO_TEXT
    )

    fun shortTitle(extras: Bundle?): String {
        val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val b = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val s = listOfNotNull(t, b).joinToString(" — ").ifBlank { "—" }
        return if (s.length > 120) s.take(120) + "…" else s
    }
}
