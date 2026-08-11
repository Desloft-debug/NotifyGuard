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

    val EMERGENCY_WORDS = listOf(
        "экстренн", "чрезвычайн", "тревог", "эвакуац", "штормово",
        "мчс", "сирена", "воздушная", "угроза", "укрытие", "оповещение о",
        "emergency", "evacuat", "amber alert", "severe weather",
        "tsunami", "wildfire", "shelter in place", "civil alert",
        "notfall", "warnung", "katastrophen", "unwetter",
        "bevölkerungsschutz", "nina", "sirene"
    )

    val SYSTEM_WORDS = listOf(
        "заряд", "батаре", "аккумулятор", "энергосбереж", "зарядк",
        "память", "хранилищ", "перегрев", "температур",
        "обновление безопасности", "обновление системы", "системное обновление",
        "battery", "charging", "storage", "low power", "overheat",
        "security update", "system update", "android update",
        "akku", "aufladen", "speicher", "sicherheitsupdate"
    )

    val DELIVERY_WORDS = listOf(
        "заказ доставлен", "заказ передан", "заказ готов", "заказ собран",
        "доставлен", "доставлено", "прибыл", "прибыло", "в пункте выдачи",
        "готов к выдаче", "ожидает вас", "курьер", "заберите заказ",
        "получен на складе", "передан в доставку", "отправлен", "трек-номер",
        "delivered", "out for delivery", "arrived", "ready for pickup",
        "your order is", "picked up", "shipped", "tracking number",
        "zugestellt", "lieferung", "abholbereit"
    )

    val CODE_WORDS = listOf(
        "код", "одноразов", "подтвержд", "подтверди", "пароль",
        "авториз", "вход в аккаунт", "вход в систему", "секретный",
        "code", "otp", "one-time", "verification", "verify", "2fa", "tan",
        "passwort", "bestätigung", "bestätigen", "verifizierung", "einmalpasswort"
    )

    val MONEY_WORDS = listOf(
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

    /** Слова, встречающиеся почти исключительно в рекламных рассылках. */
    val PROMO_WORDS = listOf(
        // прямые призывы к покупке
        "скидк", "распродаж", "промокод", "промо-код", "купон", "ваучер",
        "спецпредложен", "специальное предложен", "персональное предложен",
        "специально для вас", "только для вас", "только сегодня", "только сейчас",
        "только в приложении", "в приложении дешевле", "эксклюзив",
        "успей", "успейте", "спеши", "торопит", "последний шанс",
        "не упусти", "не пропусти", "ограниченное предложение",
        "предложение ограничено", "акция действует", "предложение действует",
        "осталось мест", "мало осталось", "заканчивается",
        "суперцена", "лучшая цена", "снизили цену", "цена дня", "выгодная цена",
        "чёрная пятница", "черная пятница", "киберпонедельник",
        "закажи", "заказывай", "купи", "покупай", "приобрет", "оформи заказ",
        "в корзине", "забыли товар", "вернитесь в корзину", "товар ждёт",
        "новая коллекц", "новинки", "каталог", "ассортимент", "хиты продаж",
        "первый заказ", "закажи впервые",
        // подарки и розыгрыши
        "дарим", "розыгрыш", "выиграй", "выигрыш", "джекпот", "лотере",
        "приз", "призы", "конкурс", "бесплатная доставка", "подарок за",
        "участвуй", "крути барабан", "ежедневный бонус",
        // бонусные программы
        "бонусные баллы", "баллы сгорают", "баллы сгорят", "потратьте баллы",
        "накопите", "уровень кэшбэка", "повышенный кэшбэк",
        // финансовые предложения
        "рассрочк", "ипотек", "одобрен лимит", "предодобрен",
        "предварительно одобрен", "кредитная карта", "рефинанс", "страховк",
        "инвестиц", "инвестируй", "брокер", "доходность", "годовых",
        "вклад под", "заработок", "пассивный доход", "трейдинг", "торгуй",
        "криптовалют", "биткоин",
        // ставки
        "фрибет", "бонус на депозит", "экспресс дня", "коэффициент",
        // подписки и тарифы
        "подпишись", "подключи", "активируй", "попробуйте бесплатно",
        "пробный период", "продлите", "смените тариф", "обнови тариф",
        "подключите опцию", "выгодные условия", "спецтариф", "безлимит",
        // рекомендательные рассылки
        "вам понравится", "похожие товары", "смотрите также",
        "для вас подобрали", "специально подобрали", "мы подобрали", "советуем",
        // вовлечение
        "мы скучаем", "давно не заходили", "давно не заходил", "вернись",
        "вернитесь", "тебя ждут", "вас ждёт", "новые уровни",
        "вернись в игру", "играй сейчас", "скачай приложение",
        "установи приложение", "установите наше",
        // отзывы и опросы
        "оцените приложение", "оставьте отзыв", "пройдите опрос",
        "поделитесь мнением", "оцени нас",
        // рефералы
        "пригласи друга", "приглашай", "реферальн",
        // путешествия
        "забронируй", "бронируй", "горящие", "дешёвые авиабилеты",
        "билеты от", "туры от",
        // контент
        "новый сезон", "премьера", "смотри сейчас", "смотрите в",
        // обучение
        "вебинар", "мастер-класс", "запишись", "регистрация открыта",
        "старт потока", "бесплатный курс", "бесплатный вебинар",
        // знакомства
        "новый лайк", "тебя лайкнул", "новое совпадение", "тебе понравилась",
        // маркировка
        "реклама", "рекламное", "партнёрск", "спонсор",
        // English
        "sale", "discount", "promo code", "coupon", "voucher", "limited time",
        "last chance", "don't miss", "hurry", "act now", "exclusive offer",
        "special offer", "free trial", "subscribe now", "upgrade now",
        "shop now", "buy now", "order now", "new arrivals", "best price",
        "price drop", "black friday", "cyber monday", "cashback", "flash sale",
        "clearance", "save up to", "members only", "early access",
        "pre-order", "back in stock", "in your cart", "abandoned cart",
        "we miss you", "come back", "claim your", "you've won",
        "spin to win", "daily reward", "bonus points", "points expire",
        "refer a friend", "giveaway", "sweepstakes", "jackpot", "free bet",
        "book now", "flight deals", "hotel deals", "watch now", "play now",
        "install now", "new episode", "new season",
        "rate us", "leave a review", "take our survey",
        "webinar", "enroll now", "sponsored", "new match", "someone liked you",
        // Deutsch
        "rabatt", "gutschein", "sonderangebot", "gewinnspiel",
        "kostenlos testen", "jetzt sichern", "nur heute", "sparen",
        "schnäppchen", "jetzt kaufen", "letzte chance", "neu eingetroffen",
        "jetzt entdecken", "nur für kurze zeit", "prämie", "punkte verfallen",
        "jetzt buchen", "neue folge", "jetzt spielen", "werbung"
    )

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

        match(text, EMERGENCY_WORDS)?.let { return Verdict(false, "экстренное сообщение: «$it»") }
        match(text, SYSTEM_WORDS)?.let { return Verdict(false, "состояние устройства: «$it»") }
        match(text, s.allowWords)?.let { return Verdict(false, "ваше слово-исключение: «$it»") }
        match(text, s.blockWords)?.let { return Verdict(true, "ваше стоп-слово: «$it»") }
        match(text, s.remoteAllow)?.let { return Verdict(false, "онлайн-исключение: «$it»") }

        if (pkg in s.blockedApps) return BLOCK_APP
        if (pkg in s.allowedApps) return ALLOW_APP
        if (isPersonalMessage(n)) return ALLOW_MESSAGE

        match(text, DELIVERY_WORDS)?.let { return Verdict(false, "статус заказа: «$it»") }
        match(text, CODE_WORDS)?.let { return Verdict(false, "код подтверждения: «$it»") }
        match(text, MONEY_WORDS)?.let { return Verdict(false, "операция по счёту: «$it»") }
        match(text, s.remoteBlock)?.let { return Verdict(true, "онлайн-словарь: «$it»") }
        match(text, PROMO_WORDS)?.let { return Verdict(true, "рекламное слово: «$it»") }

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
