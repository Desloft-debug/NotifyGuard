package com.guard.notifyguard

/** Тип номера, определённый по его виду. */
enum class NumberKind { MOBILE, LANDLINE, TOLL_FREE, PREMIUM, SHORT, HIDDEN, FOREIGN, UNKNOWN }

/** Уровень подозрительности. Это оценка по косвенным признакам, не приговор. */
enum class RiskLevel { LOW, MEDIUM, HIGH, UNKNOWN }

data class NumberInfo(
    val raw: String,
    val e164: String,
    val kind: NumberKind,
    val country: String,
    val region: String,
    val operator: String,
    val risk: RiskLevel,
    /** Ключи признаков — переводятся на экране. */
    val signals: List<String>
)

/**
 * Разбор номера и оценка риска без единого сетевого запроса.
 *
 * Публичных бесплатных API, которые честно отдают «вероятность мошенничества»,
 * не существует: сервисы вроде определителей номера работают по закрытым базам
 * и требуют ключ либо запрещают автоматические запросы. Поэтому здесь только то,
 * что можно посчитать на устройстве:
 *  - принадлежность номера по нумерации (регион, тип, оператор);
 *  - структурные признаки (платный префикс, странная длина);
 *  - поведение в журнале самого телефона (частота, массовый обзвон с одного диапазона).
 * За внешней проверкой пользователь переходит сам, по кнопке, в браузер.
 */
object NumberLookup {

    private val COUNTRIES = mapOf(
        "7" to "Россия / Казахстан", "380" to "Украина", "375" to "Беларусь",
        "374" to "Армения", "994" to "Азербайджан", "995" to "Грузия",
        "996" to "Киргизия", "992" to "Таджикистан", "998" to "Узбекистан",
        "373" to "Молдова", "49" to "Германия", "48" to "Польша",
        "44" to "Великобритания", "33" to "Франция", "39" to "Италия",
        "34" to "Испания", "1" to "США / Канада", "90" to "Турция",
        "972" to "Израиль", "86" to "Китай", "91" to "Индия",
        "62" to "Индонезия", "234" to "Нигерия", "254" to "Кения",
        "27" to "ЮАР", "55" to "Бразилия", "52" to "Мексика",
        "371" to "Латвия", "370" to "Литва", "372" to "Эстония",
        "420" to "Чехия", "421" to "Словакия", "36" to "Венгрия",
        "40" to "Румыния", "359" to "Болгария", "381" to "Сербия",
        "998" to "Узбекистан", "976" to "Монголия", "84" to "Вьетнам"
    )

    /** Географические коды России. Список неполный — только крупные. */
    private val RU_REGIONS = mapOf(
        "495" to "Москва", "499" to "Москва", "498" to "Московская область",
        "812" to "Санкт-Петербург", "813" to "Ленинградская область",
        "343" to "Екатеринбург", "351" to "Челябинск", "347" to "Уфа",
        "342" to "Пермь", "383" to "Новосибирск", "381" to "Омск",
        "391" to "Красноярск", "395" to "Иркутск", "385" to "Барнаул",
        "384" to "Кемерово", "382" to "Томск", "423" to "Владивосток",
        "421" to "Хабаровск", "411" to "Якутск", "426" to "Ижевск",
        "843" to "Казань", "831" to "Нижний Новгород", "846" to "Самара",
        "845" to "Саратов", "842" to "Ульяновск", "833" to "Киров",
        "861" to "Краснодар", "863" to "Ростов-на-Дону", "862" to "Сочи",
        "865" to "Ставрополь", "844" to "Волгоград", "473" to "Воронеж",
        "472" to "Белгород", "483" to "Брянск", "485" to "Ярославль",
        "487" to "Тула", "482" to "Тверь", "491" to "Рязань",
        "492" to "Владимир", "494" to "Коломна", "817" to "Вологда",
        "815" to "Мурманск", "818" to "Архангельск", "821" to "Сыктывкар",
        "834" to "Саранск", "836" to "Йошкар-Ола", "835" to "Чебоксары",
        "841" to "Пенза", "848" to "Тольятти", "352" to "Курган",
        "345" to "Тюмень", "349" to "Ханты-Мансийск"
    )

    /** Мобильные диапазоны. Приблизительно: номера переносятся между операторами. */
    private val RU_OPERATORS = listOf(
        Triple(900, 902, "Tele2"), Triple(903, 906, "Билайн"),
        Triple(908, 908, "Tele2"), Triple(909, 909, "Билайн"),
        Triple(910, 919, "МТС"), Triple(920, 931, "МегаФон"),
        Triple(932, 932, "МегаФон"), Triple(933, 933, "МегаФон"),
        Triple(934, 935, "МегаФон"), Triple(936, 938, "МегаФон"),
        Triple(939, 939, "МегаФон"), Triple(950, 953, "Tele2"),
        Triple(958, 958, "виртуальные операторы"), Triple(960, 968, "Билайн"),
        Triple(969, 969, "виртуальные операторы"), Triple(977, 978, "МТС"),
        Triple(980, 989, "МТС"), Triple(991, 996, "Tele2"),
        Triple(999, 999, "МегаФон / Yota")
    )

    fun analyze(raw: String, history: List<CallEntry>): NumberInfo {
        val digits = raw.filter { it.isDigit() }
        val signals = mutableListOf<String>()

        if (raw.isBlank() || digits.isEmpty()) {
            return NumberInfo(
                raw, "", NumberKind.HIDDEN, "", "", "",
                RiskLevel.MEDIUM, listOf("hidden")
            )
        }

        // Приводим к единому виду: 8XXX -> 7XXX
        val e164 = when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.drop(1)
            else -> digits
        }

        if (digits.length in 3..5) {
            return NumberInfo(
                raw, e164, NumberKind.SHORT, "", "", "",
                RiskLevel.LOW, listOf("short")
            )
        }

        var country = ""
        var region = ""
        var operator = ""
        var kind = NumberKind.UNKNOWN

        if (e164.startsWith("7") && e164.length == 11) {
            country = "Россия / Казахстан"
            val def = e164.substring(1, 4)
            val code = def.toIntOrNull() ?: 0
            when {
                def == "800" -> {
                    kind = NumberKind.TOLL_FREE
                    region = "бесплатный по России"
                    signals.add("tollfree")
                }
                def == "809" -> {
                    kind = NumberKind.PREMIUM
                    region = "платный вызов"
                    signals.add("premium")
                }
                code in 900..999 -> {
                    kind = NumberKind.MOBILE
                    operator = RU_OPERATORS.firstOrNull { code >= it.first && code <= it.second }
                        ?.third.orEmpty()
                }
                else -> {
                    kind = NumberKind.LANDLINE
                    region = RU_REGIONS[def].orEmpty()
                    if (region.isNotEmpty()) signals.add("landline")
                }
            }
        } else {
            kind = NumberKind.FOREIGN
            country = COUNTRIES.entries
                .filter { e164.startsWith(it.key) }
                .maxByOrNull { it.key.length }
                ?.value.orEmpty()
            signals.add("foreign")
        }

        // Поведенческие признаки из журнала самого телефона
        val sameNumber = history.count { normalize(it.number) == e164 }
        if (sameNumber >= 3) signals.add("repeated")

        if (e164.length >= 8) {
            val block = e164.take(e164.length - 5)
            val neighbours = history
                .map { normalize(it.number) }
                .filter { it != e164 && it.startsWith(block) }
                .distinct()
                .size
            if (neighbours >= 3) signals.add("blockcalling")
        }

        if (kind != NumberKind.SHORT && (digits.length < 10 || digits.length > 15)) {
            signals.add("length")
        }

        val risk = when {
            signals.contains("premium") -> RiskLevel.HIGH
            signals.contains("blockcalling") -> RiskLevel.HIGH
            signals.contains("length") -> RiskLevel.MEDIUM
            signals.contains("repeated") -> RiskLevel.MEDIUM
            signals.contains("foreign") && country.isEmpty() -> RiskLevel.MEDIUM
            signals.contains("hidden") -> RiskLevel.MEDIUM
            kind == NumberKind.TOLL_FREE -> RiskLevel.LOW
            kind == NumberKind.MOBILE || kind == NumberKind.LANDLINE -> RiskLevel.LOW
            else -> RiskLevel.UNKNOWN
        }

        return NumberInfo(raw, e164, kind, country, region, operator, risk, signals)
    }

    private fun normalize(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("8")) "7" + d.drop(1) else d
    }

    /** Публичные справочники. Пользователь открывает их сам, в браузере. */
    fun lookupLinks(e164: String): List<Pair<String, String>> {
        if (e164.isBlank()) return emptyList()
        return listOf(
            "kto-zvonil.net" to "https://www.kto-zvonil.net/number/$e164",
            "neberitrubku.ru" to "https://neberitrubku.ru/nomer-telefona/$e164",
            "tellows" to "https://www.tellows.de/num/$e164",
            "Поиск" to "https://www.google.com/search?q=%22%2B$e164%22"
        )
    }
}
