package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** Код причины. В журнал пишется код, а не готовая фраза — текст собирает ReasonText. */
enum class ReasonCode {
    SYSTEM_APP,
    NO_TEXT,
    ONGOING,
    CATEGORY_PROTECTED,
    CATEGORY_PROMO,
    EMERGENCY,
    DEVICE_STATE,
    USER_ALLOW_WORD,
    USER_BLOCK_WORD,
    REMOTE_ALLOW,
    REMOTE_BLOCK,
    APP_BLOCKED,
    APP_ALLOWED,
    PERSONAL_MESSAGE,
    DELIVERY,
    CODE,
    MONEY,
    PROMO_WORD,
    STRICT_MODE,
    CLEAN
}

data class Verdict(
    val block: Boolean,
    val code: ReasonCode,
    /** Слово или категория, из-за которых принято решение. Пусто, если правило без параметра. */
    val word: String = ""
) {
    fun encode(): String = if (word.isEmpty()) code.name else code.name + SEP + word

    companion object {
        // служебный разделитель, в словарях такого символа быть не может
        const val SEP = '\u001F'
    }
}

/** Настройки в том виде, в каком их видит фильтр. Собирается в Prefs.snapshot(). */
data class Snapshot(
    val filterEnabled: Boolean,
    val strictMode: Boolean,
    val storeLogText: Boolean,
    val allowedApps: Set<String>,
    val blockedApps: Set<String>,
    val blockWords: List<String>,
    val allowWords: List<String>,
    val remoteBlock: List<String>,
    val remoteAllow: List<String>,
    val region: Region,
    val promoWords: List<String>
)

/** Всё, что нужно для решения. Без этого ядро фильтра не проверить без Robolectric. */
data class NotificationInput(
    val pkg: String,
    val category: String?,
    val clearable: Boolean,
    val personal: Boolean,
    /** Склеенный текст уведомления, уже в нижнем регистре. */
    val text: String
)

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

    // Те же компоненты в прошивках вендоров зовутся по-разному, ловим по куску имени.
    // Кусок ищем внутри сегментов и только у системных префиксов: иначе безусловный
    // пропуск получит какая-нибудь ru.telecom.reklama, и перебить это будет нечем.
    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast", "emergency", "telecom", "incallui",
        "powerkeeper", "batteryservice", "systemui", "systemupdate"
    )

    // попадётся прошивка с незнакомым префиксом — дописать сюда
    private val SYSTEM_PREFIXES = listOf(
        "android.", "com.android.", "com.google.android.",
        "com.samsung.android.", "com.sec.android.",
        "com.miui.", "com.xiaomi.", "com.huawei.", "com.hihonor.",
        "com.coloros.", "com.oplus.", "com.oppo.", "com.realme.",
        "com.vivo.", "com.bbk.", "com.transsion.", "com.infinix.",
        "com.motorola.", "com.sonymobile.", "com.lge.", "com.asus.",
        "com.qualcomm.", "com.mediatek.", "org.lineageos.", "com.letv."
    )

    private val PROTECTED_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
        Notification.CATEGORY_EVENT, Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_ERROR,
        Notification.CATEGORY_STATUS
    )

    /** Обёртка над Android-типом, вся логика в перегрузке ниже. */
    fun decide(sbn: StatusBarNotification, s: Snapshot): Verdict {
        val n = sbn.notification ?: return Verdict(false, ReasonCode.NO_TEXT)
        return decide(
            NotificationInput(
                pkg = sbn.packageName.lowercase(),
                category = n.category,
                clearable = sbn.isClearable,
                personal = isPersonalMessage(n),
                text = extractText(n.extras)
            ),
            s
        )
    }

    /**
     * Порядок проверок значим и расписан в DEVELOPMENT.md.
     * Коротко: системное и экстренное выше всего, слова пользователя выше словарей.
     */
    fun decide(input: NotificationInput, s: Snapshot): Verdict {
        if (isProtectedPackage(input.pkg)) return Verdict(false, ReasonCode.SYSTEM_APP)

        if (input.category in PROTECTED_CATEGORIES) {
            return Verdict(false, ReasonCode.CATEGORY_PROTECTED, input.category.orEmpty())
        }
        if (!input.clearable) return Verdict(false, ReasonCode.ONGOING)
        if (input.category == Notification.CATEGORY_PROMO) {
            return Verdict(true, ReasonCode.CATEGORY_PROMO)
        }

        val text = input.text
        if (text.isEmpty()) return Verdict(false, ReasonCode.NO_TEXT)

        findMatch(text, Dictionaries.EMERGENCY)?.let { return Verdict(false, ReasonCode.EMERGENCY, it) }
        findMatch(text, Dictionaries.SYSTEM)?.let { return Verdict(false, ReasonCode.DEVICE_STATE, it) }
        findMatch(text, s.allowWords)?.let { return Verdict(false, ReasonCode.USER_ALLOW_WORD, it) }
        findMatch(text, s.blockWords)?.let { return Verdict(true, ReasonCode.USER_BLOCK_WORD, it) }
        findMatch(text, s.remoteAllow)?.let { return Verdict(false, ReasonCode.REMOTE_ALLOW, it) }

        if (input.pkg in s.blockedApps) return Verdict(true, ReasonCode.APP_BLOCKED)
        if (input.pkg in s.allowedApps) return Verdict(false, ReasonCode.APP_ALLOWED)
        if (input.personal) return Verdict(false, ReasonCode.PERSONAL_MESSAGE)

        findMatch(text, Dictionaries.DELIVERY)?.let { return Verdict(false, ReasonCode.DELIVERY, it) }
        findMatch(text, Dictionaries.CODE)?.let { return Verdict(false, ReasonCode.CODE, it) }
        findMatch(text, Dictionaries.MONEY)?.let { return Verdict(false, ReasonCode.MONEY, it) }
        findMatch(text, s.remoteBlock)?.let { return Verdict(true, ReasonCode.REMOTE_BLOCK, it) }
        findMatch(text, s.promoWords)?.let { return Verdict(true, ReasonCode.PROMO_WORD, it) }

        return if (s.strictMode) Verdict(true, ReasonCode.STRICT_MODE)
        else Verdict(false, ReasonCode.CLEAN)
    }

    private fun isProtectedPackage(pkg: String): Boolean {
        if (pkg in PROTECTED_PACKAGES) return true
        if (SYSTEM_PREFIXES.none { pkg.startsWith(it) }) return false
        return pkg.split('.').any { seg ->
            PROTECTED_FRAGMENTS.any { seg.contains(it) }
        }
    }

    private fun isPersonalMessage(n: Notification): Boolean {
        val extras = n.extras ?: return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("MessagingStyle", true) ||
            extras.containsKey(Notification.EXTRA_MESSAGES)
    }

    private fun findMatch(text: String, words: List<String>): String? =
        WordMatch.firstMatch(text, words)

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

    /** Заголовок для журнала: 120 символов влезает в карточку, дальше многоточие. */
    fun shortTitle(extras: Bundle?): String {
        val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val b = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val s = listOfNotNull(t, b).joinToString(" — ").ifBlank { "—" }
        return if (s.length > 120) s.take(120) + "…" else s
    }
}
