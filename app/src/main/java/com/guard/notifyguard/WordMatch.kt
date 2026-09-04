package com.guard.notifyguard

/**
 * Поиск словарных слов в тексте уведомления.
 *
 * Держится отдельно от FilterRules: тот при инициализации трогает константы
 * android.app.Notification, а здесь Android нет вообще — тесты гоняются обычным JUnit.
 */
object WordMatch {

    /**
     * Слово ищется по началу: слева не должно быть буквы или цифры, продолжение свободное.
     * «скидк» ловит «скидки» и «скидка», «код» ловит «кодом», но не «промокод».
     *
     * Оба аргумента ждём уже в нижнем регистре (см. FilterRules.extractText и Prefs.normalize).
     */
    fun contains(text: String, word: String): Boolean {
        if (word.isEmpty() || word.length > text.length) return false
        var from = 0
        val last = text.length - word.length
        while (from <= last) {
            val i = text.indexOf(word, from)
            if (i < 0) return false
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
            from = i + 1
        }
        return false
    }

    /** Первое совпавшее слово или null. Порядок списка важен — это же слово уходит в журнал. */
    fun firstMatch(text: String, words: List<String>): String? {
        for (w in words) if (contains(text, w)) return w
        return null
    }
}
