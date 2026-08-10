package com.guard.notifyguard

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

data class Verdict(val block: Boolean, val reason: String)

object FilterRules {

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
        "com.google.android.gms",
        "com.miui.powerkeeper",
        "com.samsung.android.lool",
        "com.huawei.systemmanager",
        "com.coloros.oppoguardelf",
        "com.oplus.battery"
    )

    private val PROTECTED_FRAGMENTS = listOf(
        "cellbroadcast", "emergency", "telecom", "incallui",
        "powerkeeper", "batteryservice", "systemui", "systemupdate"
    )

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

    private val EMERGENCY_WORDS = listOf(
        "экстренн", "чрезвычайн", "тревог", "эвакуац", "штормово",
        "мчс", "сирена", "воздушная", "угроза", "укрытие", "оповещение о",
        "emergency", "evacuat", "amber alert", "severe weather",
        "tsunami", "wildfire", "shelter in place", "civil alert",
        "notfall", "warnung", "katastrophen", "unwetter",
        "bevölkerungsschutz", "nina", "sirene"
    )

    /**
     * Состояние устройства и системные обновления.
     * Проверяется раньше стоп-слов, поэтому слово "обновление"
     * в списке пользователя не скрывает обновления безопасности.
     */
    private val SYSTEM_WORDS = listOf(
        "заряд", "батаре", "аккумулятор", "энергосбереж", "зарядк",
        "память", "хранилищ", "перегрев", "температур",
        "обновление безопасности", "обновление системы", "системное обновление",
        "battery", "charging", "storage", "low power", "overheat",
        "security update", "system update", "android update",
        "akku", "aufladen", "speicher", "sicherheitsupdate"
    )

    /** Статусы заказов и доставки — их ждут. */
    private val DELIVERY_WORDS = listOf(
        "заказ доставлен", "заказ передан", "заказ готов", "заказ собран",
        "доставлен", "доставлено", "прибыл", "прибыло", "в пункте выдачи",
        "готов к выдаче", "ожидает вас", "курьер", "заберите заказ",
        "получен на складе", "передан в доставку", "отправлен",
        "delivered", "out for delivery", "arrived", "ready for pickup",
        "your order is", "picked up", "shipped",
        "zugestellt", "lieferung", "abholbereit"
    )

    private val CODE_WORDS = listOf(
        "код", "одноразов", "подтвержд", "подтверди", "пароль",
        "авториз", "вход в аккаунт", "вход в систему", "секретный",
        "code", "otp", "one-time", "verification", "verify", "2fa", "tan",
        "passwort", "bestätigung", "bestätigen", "verifizierung", "einmalpasswort"
    )

    private val MONEY_WORDS = listOf(
        "перевод", "переведен", "переведён", "перечислен",
        "зачислен", "зачисление", "списан", "списание",
        "оплата", "оплачен", "снятие", "снят", "пополнен",
        "платеж", "платёж", "поступлен", "транзакц", "чек по операции",
        "покупка на", "с карты", "на карту", "по карте", "со счета", "со счёта",
        "überweisung", "gutschrift", "abbuchung", "lastschrift",
        "zahlung", "umsatz", "kontostand",
        "transfer", "transaction", "payment", "withdraw",
        "deposited", "debited", "credited"
    )

    /**
     * Слова, которые в обычной переписке и банковских уведомлениях
     * практически не встречаются — только в рекламных рассылках.
     */
    private val PROMO_WORDS = listOf(
        // прямые призывы к покупке
        "скидк", "распродаж", "промокод", "промо-код", "промокоды", "купон",
        "ваучер", "спецпредложен", "специальное предложен", "персональное предложен",
        "специально для вас", "только для вас", "только сегодня", "только сейчас",
        "успей", "успейте", "спеши", "торопит", "последний шанс", "не упусти",
        "не пропусти", "ограниченное предложение", "предложение ограничено",
        "суперцена", "лучшая цена", "снизили цену", "цена дня", "выгодная цена",
        "чёрная пятница", "черная пятница", "киберпонедельник",
        "закажи", "заказывай", "купи", "покупай", "приобрет", "оформи заказ",
        "в корзине", "забыли товар", "вернитесь в корзину", "товар ждёт",
        "новая коллекц", "новинки", "каталог", "ассортимент", "хиты продаж",
        // подарки и розыгрыши
        "дарим", "розыгрыш", "выиграй", "выигрыш", "джекпот", "лотере",
        "приз", "призы", "конкурс", "бесплатная доставка", "подарок за",
        // финансовые предложения
        "рассрочк", "ипотек", "одобрен лимит", "предодобрен", "предварительно одобрен",
        "кредитная карта", "рефинанс", "страховк", "инвестиц", "брокер",
        "доходность", "годовых", "вклад под", "заработок", "пассивный доход",
        // подписки и тарифы
        "подпишись", "подключи", "активируй", "попробуйте бесплатно",
        "пробный период", "продлите", "смените тариф", "обнови тариф",
        // рекомендательные рассылки
        "вам понравится", "похожие товары", "смотрите также", "для вас подобрали",
        "специально подобрали", "мы подобрали", "советуем",
        // рефералы и вовлечение
        "пригласи друга", "реферальн", "оцените приложение", "оставьте отзыв",
        "пройдите опрос", "вернись в игру", "тебя ждут", "новые уровни",
        "скачай приложение", "установи приложение",
        // обучение и вебинары
        "вебинар", "мастер-класс", "запишись", "регистрация открыта",
        "места заканчиваются", "старт потока", "бесплатный курс",
        // маркировка
        "реклама", "рекламное", "партнёрск", "спонсор",
        // English
        "sale", "discount", "promo code", "coupon", "voucher", "limited time",
        "last chance", "don't miss", "hurry", "act now", "exclusive offer",
        "special offer", "free trial", "subscribe now", "upgrade now",
        "shop now", "buy now", "order now", "new arrivals", "best price",
        "price drop", "black friday", "cyber monday", "cashback",
        "refer a friend", "giveaway", "sweepstakes", "jackpot",
        "rate us", "leave a review", "back in stock", "in your cart",
        "webinar", "enroll now", "sponsored",
        // Deutsch
        "rabatt", "gutschein", "sonderangebot", "gewinnspiel",
        "kostenlos testen", "jetzt sichern", "nur heute", "sparen",
        "schnäppchen", "jetzt kaufen", "letzte chance", "neu eingetroffen",
        "unverbindlich", "werbung"
    )

    fun decide(sbn: StatusBarNotification, prefs: Prefs): Verdict {
        val pkg = sbn.packageName.lowercase()

        if (pkg in PROTECTED_PACKAGES || PROTECTED_FRAGMENTS.any { pkg.contains(it) }) {
            return Verdict(false, "системное приложение")
        }

        val n = sbn.notification ?: return Verdict(false, "пустое уведомление")

        if (n.category in PROTECTED_CATEGORIES) return Verdict(false, "категория ${n.category}")
        if (!sbn.isClearable) return Verdict(false, "несъёмное уведомление")

        // Явно рекламная категория — снимаем сразу
        if (n.category == Notification.CATEGORY_PROMO) {
            return Verdict(true, "категория «реклама»")
        }

        val text = extractText(n.extras)

        match(text, EMERGENCY_WORDS)?.let { return Verdict(false, "экстренное сообщение: «$it»") }
        match(text, SYSTEM_WORDS)?.let { return Verdict(false, "состояние устройства: «$it»") }
        match(text, prefs.customAllowWords.toList())?.let {
            return Verdict(false, "ваше слово-исключение: «$it»")
        }
        match(text, prefs.customBlockWords.toList())?.let {
            return Verdict(true, "ваше стоп-слово: «$it»")
        }

        if (pkg in prefs.blockedApps) return Verdict(true, "приложение в чёрном списке")
        if (pkg in prefs.allowedApps) return Verdict(false, "приложение в белом списке")

        if (isPersonalMessage(n)) return Verdict(false, "личное сообщение")

        match(text, DELIVERY_WORDS)?.let { return Verdict(false, "статус заказа: «$it»") }
        match(text, CODE_WORDS)?.let { return Verdict(false, "код подтверждения: «$it»") }
        match(text, MONEY_WORDS)?.let { return Verdict(false, "операция по счёту: «$it»") }
        match(text, PROMO_WORDS)?.let { return Verdict(true, "рекламное слово: «$it»") }

        return if (prefs.strictMode) {
            Verdict(true, "строгий режим: приложения нет в белом списке")
        } else {
            Verdict(false, "нет признаков рекламы")
        }
    }

    /**
     * Личная переписка. Мессенджеры оформляют такие уведомления
     * стилем MessagingStyle — рекламные рассылки внутри тех же
     * приложений обычно приходят обычным стилем.
     */
    private fun isPersonalMessage(n: Notification): Boolean {
        val extras = n.extras ?: return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val messagingStyle = template.contains("MessagingStyle", ignoreCase = true) ||
            extras.containsKey(Notification.EXTRA_MESSAGES)
        return messagingStyle && n.category != Notification.CATEGORY_PROMO
    }

    private fun match(text: String, words: List<String>): String? =
        words.firstOrNull { w ->
            val needle = w.trim().lowercase()
            needle.isNotEmpty() && regexFor(needle).containsMatchIn(text)
        }

    private val cache = HashMap<String, Regex>()

    private fun regexFor(needle: String): Regex = synchronized(cache) {
        cache.getOrPut(needle) { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(needle)) }
    }

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

    fun shortTitle(extras: Bundle?): String {
        val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val b = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val s = listOfNotNull(t, b).joinToString(" — ").ifBlank { "—" }
        return if (s.length > 120) s.take(120) + "…" else s
    }
}
