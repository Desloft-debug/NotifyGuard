package com.guard.notifyguard

// Какой рекламный словарь брать.
enum class Region { RU, EN, ALL }

object Dictionaries {

    /* ------- защитные списки, общие для всех регионов ------- */

    val EMERGENCY = listOf(
        "экстренн", "чрезвычайн", "тревог", "эвакуац", "штормово",
        "мчс", "сирена", "воздушная", "угроза", "укрытие", "оповещение о",
        "emergency", "evacuat", "amber alert", "severe weather",
        "tsunami", "wildfire", "shelter in place", "civil alert",
        "notfall", "warnung", "katastrophen", "unwetter",
        "bevölkerungsschutz", "nina", "sirene"
    )

    val SYSTEM = listOf(
        "заряд", "батаре", "аккумулятор", "энергосбереж", "зарядк",
        "память", "хранилищ", "перегрев", "температур",
        "обновление безопасности", "обновление системы", "системное обновление",
        "battery", "charging", "storage", "low power", "overheat",
        "security update", "system update", "android update",
        "akku", "aufladen", "speicher", "sicherheitsupdate"
    )

    val DELIVERY = listOf(
        "заказ доставлен", "заказ передан", "заказ готов", "заказ собран",
        "доставлен", "доставлено", "прибыл", "прибыло", "в пункте выдачи",
        "готов к выдаче", "ожидает вас", "курьер", "заберите заказ",
        "получен на складе", "передан в доставку", "отправлен", "трек-номер",
        "delivered", "out for delivery", "arrived", "ready for pickup",
        "your order is", "picked up", "shipped", "tracking number",
        "zugestellt", "lieferung", "abholbereit"
    )

    val CODE = listOf(
        "код", "одноразов", "подтвержд", "подтверди", "пароль",
        "авториз", "вход в аккаунт", "вход в систему", "секретный",
        "code", "otp", "one-time", "verification", "verify", "2fa", "tan",
        "passwort", "bestätigung", "bestätigen", "verifizierung", "einmalpasswort"
    )

    val MONEY = listOf(
        "перевод", "переведен", "переведён", "перечислен",
        "зачислен", "зачисление", "списан", "списание",
        // Голое «начислен» нельзя: ловит «вам начислено 500 бонусов».
        // Только проценты по вкладу.
        "начислен процент", "начислены процент", "начисление процент",
        "оплата", "оплачен", "снятие", "снят", "пополнен",
        "платеж", "платёж", "поступлен", "транзакц", "чек по операции",
        "покупка на", "с карты", "на карту", "по карте", "со счета", "со счёта",
        "überweisung", "gutschrift", "abbuchung", "lastschrift",
        "zahlung", "umsatz", "kontostand",
        "transfer", "transaction", "payment", "withdraw",
        "deposited", "debited", "credited"
    )

    /* ------- рекламные слова по регионам ------- */

    val PROMO_RU = listOf(
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
        "реклама", "рекламное", "партнёрск", "спонсор"
    )

    // Английский плюс немецкий: отдельного словаря под DE пока нет.
    val PROMO_EN = listOf(
        // покупки
        "sale", "discount", "promo code", "coupon", "voucher", "clearance",
        "flash sale", "limited time", "limited offer", "offer ends",
        "last chance", "don't miss", "hurry", "act now", "ends tonight",
        "while supplies last", "only today", "today only",
        "exclusive offer", "special offer", "members only", "early access",
        "shop now", "buy now", "order now", "grab yours", "add to cart",
        "in your cart", "abandoned cart", "you left", "still thinking",
        "new arrivals", "new collection", "best price", "lowest price",
        "price drop", "save up to", "up to 50", "extra off",
        "black friday", "cyber monday", "boxing day", "pre-order",
        "back in stock", "restocked", "bestseller", "trending now",
        // подарки и розыгрыши
        "giveaway", "sweepstakes", "you've won", "you have won",
        "claim your", "claim now", "spin to win", "lucky winner",
        "free gift", "gift for you", "prize", "jackpot",
        "daily reward", "bonus points", "points expire", "rewards expire",
        // финансы
        "cashback", "loan", "instant loan", "payday", "credit card offer",
        "pre-approved", "approved limit", "refinance", "mortgage rate",
        "insurance quote", "invest now", "trading", "crypto", "bitcoin",
        "passive income", "earn money", "make money", "guaranteed return",
        "free bet", "deposit bonus", "odds boost", "place your bet",
        // подписки
        "subscribe now", "upgrade now", "free trial", "trial ends",
        "renew now", "go premium", "unlock premium", "cancel anytime",
        // рекомендации
        "recommended for you", "picked for you", "you might like",
        "similar items", "see also", "based on your",
        // вовлечение
        "we miss you", "come back", "long time no see", "your friends are",
        "streak", "don't lose your", "play now", "install now",
        "download our app", "new level", "new episode", "new season",
        "watch now", "stream now",
        // отзывы
        "rate us", "leave a review", "take our survey", "share your feedback",
        // рефералы
        "refer a friend", "invite friends", "referral bonus",
        // путешествия
        "book now", "flight deals", "hotel deals", "last minute",
        "cheap flights", "fares from",
        // обучение
        "webinar", "enroll now", "register now", "seats are limited",
        "free course", "masterclass",
        // знакомства
        "new match", "someone liked you", "new like", "you have a match",
        // маркировка
        "sponsored", "advertisement", "partner offer",
        // Deutsch
        "rabatt", "gutschein", "sonderangebot", "angebot", "aktion",
        "gewinnspiel", "kostenlos testen", "jetzt sichern", "nur heute",
        "sparen", "schnäppchen", "jetzt kaufen", "letzte chance",
        "neu eingetroffen", "jetzt entdecken", "nur für kurze zeit",
        "prämie", "punkte verfallen", "jetzt buchen", "neue folge",
        "jetzt spielen", "werbung", "unverbindlich", "kredit", "darlehen"
    )

    // lazy, потому что promoFor() дёргается на каждое уведомление
    private val PROMO_ALL: List<String> by lazy { PROMO_RU + PROMO_EN }

    fun promoFor(region: Region): List<String> = when (region) {
        Region.RU -> PROMO_RU
        Region.EN -> PROMO_EN
        Region.ALL -> PROMO_ALL
    }

    fun promoSize(region: Region): Int = promoFor(region).size
}
