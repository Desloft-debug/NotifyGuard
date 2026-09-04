package com.guard.notifyguard

/**
 * Поиск словарных слов в тексте уведомления.
 *
 * Вынесено из FilterRules отдельным объектом по двум причинам. Во-первых, это
 * единственная по-настоящему алгоритмическая часть фильтра, и её хочется покрыть
 * тестами. Во-вторых, FilterRules при инициализации трогает константы android.app.Notification,
 * из-за чего обычный JVM-тест пришлось бы запускать через Robolectric. Здесь нет
 * ни одной зависимости от Android — тесты запускаются в testDebugUnitTest как есть.
 *
 * Раньше поиск делался регулярками вида "(?<![\p{L}\p{N}])" + Regex.escape(needle)
 * со словарём скомпилированных шаблонов в ConcurrentHashMap, из которого ничего
 * не выселялось. Шаблон целиком состоял из экранированного литерала и одного lookbehind,
 * то есть делал ровно то же, что indexOf с проверкой символа слева — только в разы
 * медленнее и с растущим кешем. На одно уведомление приходится несколько сотен
 * таких проверок, и все они в главном потоке слушателя.
 */
object WordMatch {

    /**
     * Слово ищется по началу: перед ним не должно стоять буквы или цифры,
     * продолжение допускается.
     *
     * «скидк» ловит «скидки» и «скидка», «код» ловит «кодом»,
     * но не срабатывает внутри «промокод».
     *
     * Оба параметра ожидаются в нижнем регистре — так их готовят
     * FilterRules.extractText() и Prefs.normalize().
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

    /** Первое совпавшее слово или null. Порядок списка сохраняется — он значим для причины. */
    fun firstMatch(text: String, words: List<String>): String? {
        for (w in words) if (contains(text, w)) return w
        return null
    }

    /** Проверка произвольного текста произвольным словом — для экрана «проверить текст». */
    fun matches(text: String, word: String): Boolean =
        contains(text.lowercase(), word.trim().lowercase())
}
