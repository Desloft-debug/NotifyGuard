package com.guard.notifyguard

/**
 * Перевод кода причины в текст. Вынесено отдельным файлом, а не в data class Strings,
 * чтобы не расширять конструктор на 213 параметров ради двух десятков строк.
 *
 * Записи журнала, сделанные версиями до 3.1, хранят готовый русский текст без разделителя.
 * Такие строки распознаются по отсутствию известного кода и показываются как есть —
 * старый журнал не ломается и не требует миграции.
 */
object ReasonText {

    fun render(stored: String, lang: Lang): String {
        if (stored.isBlank()) return ""

        val sep = stored.indexOf(Verdict.SEP)
        val codeName = if (sep >= 0) stored.substring(0, sep) else stored
        val word = if (sep >= 0) stored.substring(sep + 1) else ""

        val code = runCatching { ReasonCode.valueOf(codeName) }.getOrNull()
            ?: return stored // запись из старой версии

        val template = if (isRussian(lang)) RU[code] else EN[code]
        return template?.format(word) ?: codeName
    }

    private fun isRussian(lang: Lang): Boolean = when (lang) {
        Lang.RU -> true
        Lang.EN -> false
        Lang.SYSTEM -> java.util.Locale.getDefault().language.equals("ru", true)
    }

    private val RU = mapOf(
        ReasonCode.SYSTEM_APP to "системное приложение",
        ReasonCode.NO_TEXT to "нет текста",
        ReasonCode.ONGOING to "несъёмное уведомление",
        ReasonCode.CATEGORY_PROTECTED to "категория «%s»",
        ReasonCode.CATEGORY_PROMO to "категория «реклама»",
        ReasonCode.EMERGENCY to "экстренное сообщение: «%s»",
        ReasonCode.DEVICE_STATE to "состояние устройства: «%s»",
        ReasonCode.USER_ALLOW_WORD to "ваше слово-исключение: «%s»",
        ReasonCode.USER_BLOCK_WORD to "ваше стоп-слово: «%s»",
        ReasonCode.REMOTE_ALLOW to "онлайн-исключение: «%s»",
        ReasonCode.REMOTE_BLOCK to "онлайн-словарь: «%s»",
        ReasonCode.APP_BLOCKED to "приложение в чёрном списке",
        ReasonCode.APP_ALLOWED to "приложение в белом списке",
        ReasonCode.PERSONAL_MESSAGE to "личное сообщение",
        ReasonCode.DELIVERY to "статус заказа: «%s»",
        ReasonCode.CODE to "код подтверждения: «%s»",
        ReasonCode.MONEY to "операция по счёту: «%s»",
        ReasonCode.PROMO_WORD to "рекламное слово: «%s»",
        ReasonCode.STRICT_MODE to "строгий режим",
        ReasonCode.CLEAN to "нет признаков рекламы"
    )

    private val EN = mapOf(
        ReasonCode.SYSTEM_APP to "system app",
        ReasonCode.NO_TEXT to "no text",
        ReasonCode.ONGOING to "ongoing notification",
        ReasonCode.CATEGORY_PROTECTED to "category \"%s\"",
        ReasonCode.CATEGORY_PROMO to "category \"promo\"",
        ReasonCode.EMERGENCY to "emergency alert: \"%s\"",
        ReasonCode.DEVICE_STATE to "device status: \"%s\"",
        ReasonCode.USER_ALLOW_WORD to "your exception word: \"%s\"",
        ReasonCode.USER_BLOCK_WORD to "your stop word: \"%s\"",
        ReasonCode.REMOTE_ALLOW to "online exception: \"%s\"",
        ReasonCode.REMOTE_BLOCK to "online dictionary: \"%s\"",
        ReasonCode.APP_BLOCKED to "app is blocklisted",
        ReasonCode.APP_ALLOWED to "app is allowlisted",
        ReasonCode.PERSONAL_MESSAGE to "personal message",
        ReasonCode.DELIVERY to "order status: \"%s\"",
        ReasonCode.CODE to "verification code: \"%s\"",
        ReasonCode.MONEY to "account activity: \"%s\"",
        ReasonCode.PROMO_WORD to "promo word: \"%s\"",
        ReasonCode.STRICT_MODE to "strict mode",
        ReasonCode.CLEAN to "no signs of advertising"
    )
}
