package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** Что делать с уведомлением и почему. */
data class Verdict(val block: Boolean, val reason: String)

object FilterRules {

    /** Пакеты, которые не трогаем ни при каких настройках. */
    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
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
        "com.android.emergency",
        "com.miui.powerkeeper",
        "com.samsung.android.lool",
        "com.huawei.systemmanager",
        "com.coloros.oppoguardelf",
        "com.oplus.battery"
    )

    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast", "emergency", "telecom", "incallui",
        "powerkeeper", "batteryservice", "systemui"
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
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_ERROR,
        Notification.CATEGORY_STATUS
    )

    /** Экстренные сообщения — пропускаем всегда, раньше любых блокировок. */
    private val EMERGENCY_WORDS = listOf(
        "экстренн", "чрезвычайн", "тревог", "эвакуац", "штормово",
        "мчс", "сирена", "воздушная", "угроза", "укрытие",
        "emergency", "evacuat", "amber alert", "severe weather",
        "tsunami", "wildfire", "shelter in place",
        "notfall", "warnung", "katastrophen", "unwetter",
        "bevölkerungsschutz", "nina", "sirene"
    )

    /**
     * Состояние устройства: заряд, память, обновления безопасности.
     * Именно сюда попадало уведомление про 10% заряда.
     */
    private val SYSTEM_WORDS = listOf(
        "заряд", "батаре", "аккумулятор", "энергосбереж", "зарядк",
        "память", "хранилищ", "перегрев", "температур",
        "battery", "charging", "storage", "low power", "overheat",
        "akku", "aufladen", "speicher"
    )

    /**
     * Коды подтверждения. Проверяются с границей слова, поэтому
     * "код" больше не срабатывает внутри "промокод".
     */
    private val CODE_WORDS = listOf(
        "код", "одноразов", "подтвержд", "подтверди", "пароль",
        "авториз", "вход в аккаунт", "вход в систему",
        "code", "otp", "one-time", "verification", "verify", "2fa", "tan",
        "passwort", "bestätigung", "bestätigen", "verifizierung", "einmalpasswort"
    )

    /**
     * Операции по счёту. Символов валют здесь нет намеренно:
     * из-за них любая реклама с ценником считалась банковской.
     */
    private val MONEY_WORDS = listOf(
        "перевод", "переведен", "переведён", "перечислен",
        "зачислен", "зачисление", "списан", "списание",
        "оплата", "оплачен", "снятие", "снят", "пополнен",
        "платеж", "платёж", "баланс", "поступлен", "транзакц",
        "покупка на", "с карты", "на карту", "по карте", "со счета", "со счёта",
        "überweisung", "gutschrift", "abbuchung", "lastschrift",
        "zahlung", "umsatz", "kontostand",
        "transfer", "transaction", "payment", "withdraw",
        "deposited", "debited", "credited", "balance"
    )

    /** Признаки рекламы и промо. */
    private val PROMO_WORDS = listOf(
        "скидк", "акци", "распродаж", "промокод", "бонус", "кэшбэк", "кешбэк",
        "спецпредложен", "персональное предложен", "специально для вас",
        "только для вас", "выгодн", "выгода", "только сегодня", "успей",
        "дарим", "подарок", "розыгрыш", "приз", "оформи", "подключи",
        "рассрочк", "кредит", "займ", "ипотек", "одобрен лимит", "предодобрен",
        "подписк", "подпишись", "тариф", "реклама", "приглас", "новинк",
        "закажи", "заказыв", "доставим", "бесплатная доставка", "промо",
        "суперцена", "снизили цену", "новая коллекц", "каталог", "купи",
        "sale", "discount", "promo", "offer", "deal", "coupon", "limited time",
        "subscribe", "upgrade now", "free trial", "don't miss", "shop now",
        "rabatt", "gutschein", "angebot", "aktion", "gewinnspiel",
        "kostenlos testen", "jetzt sichern", "nur heute"
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
        if (!sbn.isClearable) return Verdict(false, "несъёмное уведомление")

        val text = extractText(n.extras)

        // 1. Экстренное — выше всего, никакие настройки это не отменяют
        match(text, EMERGENCY_WORDS)?.let {
            return Verdict(false, "экстренное сообщение: «$it»")
        }
        // 2. Состояние устройства
        match(text, SYSTEM_WORDS)?.let {
            return Verdict(false, "состояние устройства: «$it»")
        }
        // 3. Слова-исключения, добавленные вручную
        match(text, prefs.customAllowWords.toList())?.let {
            return Verdict(false, "ваше слово-исключение: «$it»")
        }
        // 4. Стоп-слова, добавленные вручную — приоритетнее кодов и переводов
        match(text, prefs.customBlockWords.toList())?.let {
            return Verdict(true, "ваше стоп-слово: «$it»")
        }
        // 5. Списки приложений
        if (pkg in prefs.blockedApps) return Verdict(true, "приложение в чёрном списке")
        if (pkg in prefs.allowedApps) return Verdict(false, "приложение в белом списке")

        // 6. Коды и деньги важнее рекламных слов в том же тексте
        match(text, CODE_WORDS)?.let {
            return Verdict(false, "код подтверждения: «$it»")
        }
        match(text, MONEY_WORDS)?.let {
            return Verdict(false, "операция по счёту: «$it»")
        }
        // 7. Реклама
        match(text, PROMO_WORDS)?.let {
            return Verdict(true, "рекламное слово: «$it»")
        }
        return if (prefs.strictMode) {
            Verdict(true, "строгий режим: приложения нет в белом списке")
        } else {
            Verdict(false, "нет признаков рекламы")
        }
    }

    /**
     * Ищет первое совпавшее слово. Слово должно начинаться на границе:
     * перед ним не может стоять буква. Продолжение допускается,
     * поэтому "скидк" ловит "скидки", а "код" не ловит "промокод".
     */
    private fun match(text: String, words: List<String>): String? =
        words.firstOrNull { w ->
            val needle = w.trim().lowercase()
            needle.isNotEmpty() && regexFor(needle).containsMatchIn(text)
        }

    private val cache = HashMap<String, Regex>()

    private fun regexFor(needle: String): Regex = synchronized(cache) {
        cache.getOrPut(needle) {
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(needle))
        }
    }

    /** Проверка слова на тестовом тексте — используется экраном настроек. */
    fun testMatch(text: String, word: String): Boolean =
        match(text.lowercase(), listOf(word)) != null

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
        return if (s.length > 120) s.take(120) + "…" else s
    }
}
