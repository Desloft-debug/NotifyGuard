package com.guard.notifyguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Контрольные тексты, которые не должны потеряться.
 *
 * Проверяем две вещи: что важное ловится защитным словарём (они идут раньше промо,
 * значит дойдёт даже в строгом режиме) и что ни одно рекламное слово на них
 * не срабатывает — на случай, если порядок проверок когда-нибудь переставят.
 *
 * Словари правлю чаще всего остального, а цена ошибки — потерянный код из банка.
 */
class DictionarySafetyTest {

    private val protective = listOf(
        "EMERGENCY" to Dictionaries.EMERGENCY,
        "SYSTEM" to Dictionaries.SYSTEM,
        "DELIVERY" to Dictionaries.DELIVERY,
        "CODE" to Dictionaries.CODE,
        "MONEY" to Dictionaries.MONEY
    )

    private fun protectedBy(text: String): String? =
        protective.firstOrNull { (_, words) -> WordMatch.firstMatch(text.lowercase(), words) != null }
            ?.first

    private val mustSurvive = listOf(
        "Кредит одобрен. Код подтверждения 4821",
        "Ваш код для входа: 903472",
        "Перевод 5 000 руб от Иван И. зачислен на карту",
        "Списание 1 299 руб, покупка на сайте",
        "Начислен процент по вкладу 1 240 руб",
        "Заказ доставлен в пункт выдачи",
        "Заказ собран, ожидает вас",
        "МЧС: штормовое предупреждение, оставайтесь дома",
        "Батарея заряжена на 100%",
        "Обновление системы готово к установке",
        "Your verification code is 662311",
        "Payment of 42.00 was debited from your card",
        "Your order has been delivered",
        "Emergency alert: severe weather in your area",
        "Ihr Bestätigungscode lautet 5512",
        "Sicherheitsupdate verfügbar"
    )

    @Test
    fun `важные сообщения ловятся защитным словарём`() {
        val missed = mustSurvive.filter { protectedBy(it) == null }
        assertEquals("не покрыты защитными словарями: $missed", emptyList<String>(), missed)
    }

    @Test
    fun `рекламные словари не срабатывают на важных сообщениях`() {
        for (text in mustSurvive) {
            val lower = text.lowercase()
            assertNull(
                "PROMO_RU сработал на: $text",
                WordMatch.firstMatch(lower, Dictionaries.PROMO_RU)
            )
            assertNull(
                "PROMO_EN сработал на: $text",
                WordMatch.firstMatch(lower, Dictionaries.PROMO_EN)
            )
        }
    }

    private val mustBeHidden = listOf(
        "Скидка 50% только сегодня! Успей купить" to Dictionaries.PROMO_RU,
        "Промокод SALE20 на первый заказ" to Dictionaries.PROMO_RU,
        "Вернитесь в корзину, товар ждёт" to Dictionaries.PROMO_RU,
        "Limited time offer: 30% discount, shop now" to Dictionaries.PROMO_EN,
        "You have won a free gift, claim now" to Dictionaries.PROMO_EN,
        "Jetzt sichern: nur heute Rabatt" to Dictionaries.PROMO_EN
    )

    @Test
    fun `реклама ловится рекламным словарём`() {
        for ((text, dict) in mustBeHidden) {
            assertNotNull(
                "не поймано: $text",
                WordMatch.firstMatch(text.lowercase(), dict)
            )
        }
    }

    @Test
    fun `бонусные баллы не считаются операцией по счёту`() {
        // денежный словарь идёт раньше рекламного, поэтому голого «начислен» там нет
        assertNull(WordMatch.firstMatch("вам начислено 500 бонусов", Dictionaries.MONEY))
        assertNotNull(
            WordMatch.firstMatch("начислен процент по вкладу", Dictionaries.MONEY)
        )
    }

    @Test
    fun `промокод не считается кодом подтверждения`() {
        val text = "ваш промокод sale20 действует до пятницы"
        assertNull(WordMatch.firstMatch(text, Dictionaries.CODE))
        assertNotNull(WordMatch.firstMatch(text, Dictionaries.PROMO_RU))
    }

    @Test
    fun `в словарях нет пустых строк и всё в нижнем регистре`() {
        val all = protective.flatMap { it.second } + Dictionaries.PROMO_RU + Dictionaries.PROMO_EN
        val bad = all.filter { it.isBlank() || it != it.lowercase() || it != it.trim() }
        assertEquals("плохие записи: $bad", emptyList<String>(), bad)
    }

    @Test
    fun `в словарях нет дубликатов`() {
        for ((name, words) in protective) {
            val dupes = words.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertEquals("$name: дубликаты $dupes", emptySet<String>(), dupes)
        }
        for ((name, words) in listOf("PROMO_RU" to Dictionaries.PROMO_RU, "PROMO_EN" to Dictionaries.PROMO_EN)) {
            val dupes = words.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertEquals("$name: дубликаты $dupes", emptySet<String>(), dupes)
        }
    }

    @Test
    fun `рекламное слово не перекрывает защитное`() {
        // если одно слово начинается с другого, исход зависит от порядка проверок —
        // такие пары ловим здесь, а не на телефоне
        val promo = Dictionaries.PROMO_RU + Dictionaries.PROMO_EN
        val guard = protective.flatMap { it.second }
        val conflicts = promo.filter { p -> guard.any { g -> g.startsWith(p) || p.startsWith(g) } }
        assertEquals("пересечение промо и защитных словарей: $conflicts", emptyList<String>(), conflicts)
    }
}
