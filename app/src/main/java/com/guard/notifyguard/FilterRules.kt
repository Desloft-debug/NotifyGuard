package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

/**
 * Код причины решения. Раньше причина собиралась здесь готовой русской строкой
 * и в таком виде уходила в журнал — у пользователя с английским интерфейсом
 * весь журнал оставался на русском. Теперь наружу отдаётся код, а текст собирает UI
 * (см. ReasonText.kt).
 */
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
    /** Форма для хранения в журнале. Разделитель — служебный символ, в словарях его быть не может. */
    fun encode(): String = if (word.isEmpty()) code.name else code.name + SEP + word

    companion object {
        const val SEP = '\u001F'
    }
}

/**
 * Всё, что нужно для решения. Отделено от StatusBarNotification, чтобы ядро фильтра
 * можно было гонять в обычном юнит-тесте без Robolectric.
 */
data class NotificationInput(
    val pkg: String,
    val category: String?,
    val clearable: Boolean,
    val personal: Boolean,
    /** Уже склеенный и приведённый к нижнему регистру текст уведомления. */
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

    /**
     * Куски имени, по которым узнаются те же системные компоненты в прошивках вендоров.
     * Раньше проверялись через pkg.contains() по всему имени — в результате
     * безусловный пропуск получало любое приложение вроде ru.telecom.reklama
     * или com.example.emergencyshop, и переопределить это пользователь не мог:
     * проверка стоит раньше чёрного списка.
     *
     * Теперь фрагмент ищется только внутри сегментов имени и только у пакетов
     * с системным префиксом. Если появится прошивка с неизвестным префиксом,
     * её достаточно дописать в SYSTEM_PREFIXES.
     */
    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast", "emergency", "telecom", "incallui",
        "powerkeeper", "batteryservice", "systemui", "systemupdate"
    )

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

    val EMERGENCY_WORDS get() = Dictionaries.EMERGENCY
    val SYSTEM_WORDS get() = Dictionaries.SYSTEM
    val DELIVERY_WORDS get() = Dictionaries.DELIVERY
    val CODE_WORDS get() = Dictionaries.CODE
    val MONEY_WORDS get() = Dictionaries.MONEY

    /** Обёртка над Android-типом. Вся логика — в перегрузке ниже. */
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
     * Порядок проверок описан в DEVELOPMENT.md и намеренно оставлен прежним:
     * слова пользователя выигрывают у встроенных словарей.
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

    /** Проверка одного слова по произвольному тексту — для экрана «проверить текст». */
    fun matches(text: String, word: String): Boolean = WordMatch.matches(text, word)

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
