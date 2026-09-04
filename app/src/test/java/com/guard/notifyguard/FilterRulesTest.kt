package com.guard.notifyguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Порядок правил в decide(). Ради этого NotificationInput и отделён
 * от StatusBarNotification — иначе пришлось бы тащить Robolectric.
 *
 * Значения категорий тут литералами: это ровно то, что лежит
 * в Notification.CATEGORY_*, зато тест не зависит от android.jar.
 */
class FilterRulesTest {

    private fun snapshot(
        strict: Boolean = false,
        block: List<String> = emptyList(),
        allow: List<String> = emptyList(),
        remoteBlock: List<String> = emptyList(),
        remoteAllow: List<String> = emptyList(),
        blockedApps: Set<String> = emptySet(),
        allowedApps: Set<String> = emptySet(),
        region: Region = Region.RU
    ) = Snapshot(
        filterEnabled = true,
        strictMode = strict,
        storeLogText = true,
        allowedApps = allowedApps,
        blockedApps = blockedApps,
        blockWords = block,
        allowWords = allow,
        remoteBlock = remoteBlock,
        remoteAllow = remoteAllow,
        region = region,
        promoWords = Dictionaries.promoFor(region)
    )

    private fun input(
        text: String,
        pkg: String = "com.shop.app",
        category: String? = null,
        clearable: Boolean = true,
        personal: Boolean = false
    ) = NotificationInput(pkg, category, clearable, personal, text.lowercase())

    @Test
    fun `реклама скрывается`() {
        val v = FilterRules.decide(input("Скидка 50%, успей купить"), snapshot())
        assertTrue(v.block)
        assertEquals(ReasonCode.PROMO_WORD, v.code)
    }

    @Test
    fun `код подтверждения остаётся`() {
        val v = FilterRules.decide(input("Ваш код для входа: 903472"), snapshot())
        assertFalse(v.block)
        assertEquals(ReasonCode.CODE, v.code)
    }

    @Test
    fun `стоп-слово пользователя главнее кода и суммы`() {
        val s = snapshot(block = listOf("кредит"))
        val v = FilterRules.decide(input("Кредит одобрен, код 4821"), s)
        assertTrue(v.block)
        assertEquals(ReasonCode.USER_BLOCK_WORD, v.code)
        assertEquals("кредит", v.word)
    }

    @Test
    fun `слово-исключение главнее стоп-слова`() {
        val s = snapshot(block = listOf("скидк"), allow = listOf("аэрофлот"))
        val v = FilterRules.decide(input("Аэрофлот: скидка на билеты"), s)
        assertFalse(v.block)
        assertEquals(ReasonCode.USER_ALLOW_WORD, v.code)
    }

    @Test
    fun `экстренное стоп-словом не перебить`() {
        val s = snapshot(block = listOf("мчс"))
        val v = FilterRules.decide(input("МЧС: штормовое предупреждение"), s)
        assertFalse(v.block)
        assertEquals(ReasonCode.EMERGENCY, v.code)
    }

    @Test
    fun `личное сообщение не трогаем даже с рекламным словом`() {
        val v = FilterRules.decide(input("держи промокод на скидку", personal = true), snapshot())
        assertFalse(v.block)
        assertEquals(ReasonCode.PERSONAL_MESSAGE, v.code)
    }

    @Test
    fun `чёрный список приложений сильнее белого`() {
        val s = snapshot(
            blockedApps = setOf("com.shop.app"),
            allowedApps = setOf("com.shop.app")
        )
        val v = FilterRules.decide(input("любой текст"), s)
        assertTrue(v.block)
        assertEquals(ReasonCode.APP_BLOCKED, v.code)
    }

    @Test
    fun `онлайн-словарь не перебивает операцию по счёту`() {
        val s = snapshot(remoteBlock = listOf("перевод"))
        val v = FilterRules.decide(input("Перевод 5 000 руб зачислен"), s)
        assertFalse(v.block)
        assertEquals(ReasonCode.MONEY, v.code)
    }

    @Test
    fun `системное приложение не фильтруется`() {
        val v = FilterRules.decide(input("скидка 50%", pkg = "com.android.systemui"), snapshot())
        assertFalse(v.block)
        assertEquals(ReasonCode.SYSTEM_APP, v.code)
    }

    @Test
    fun `вендорский пакет узнаётся по куску имени`() {
        val v = FilterRules.decide(
            input("скидка 50%", pkg = "com.miui.cellbroadcastreceiver"), snapshot()
        )
        assertEquals(ReasonCode.SYSTEM_APP, v.code)
    }

    @Test
    fun `чужой пакет со словом telecom не считается системным`() {
        // из-за этого случая кусок имени и ищется только у системных префиксов
        val v = FilterRules.decide(input("скидка 50%", pkg = "ru.telecom.reklama"), snapshot())
        assertTrue(v.block)
        assertEquals(ReasonCode.PROMO_WORD, v.code)
    }

    @Test
    fun `несъёмное уведомление и защищённая категория остаются`() {
        val ongoing = FilterRules.decide(input("скидка", clearable = false), snapshot())
        assertEquals(ReasonCode.ONGOING, ongoing.code)

        val call = FilterRules.decide(input("скидка", category = "call"), snapshot())
        assertEquals(ReasonCode.CATEGORY_PROTECTED, call.code)
        assertFalse(call.block)
    }

    @Test
    fun `категория promo скрывается без всяких слов`() {
        val v = FilterRules.decide(input("ничего особенного", category = "promo"), snapshot())
        assertTrue(v.block)
        assertEquals(ReasonCode.CATEGORY_PROMO, v.code)
    }

    @Test
    fun `строгий режим скрывает всё непонятное`() {
        val text = input("напоминание о встрече в четверг")
        assertEquals(ReasonCode.CLEAN, FilterRules.decide(text, snapshot()).code)

        val strict = FilterRules.decide(text, snapshot(strict = true))
        assertTrue(strict.block)
        assertEquals(ReasonCode.STRICT_MODE, strict.code)
    }

    @Test
    fun `пустой текст не скрывается никогда`() {
        val v = FilterRules.decide(input("", clearable = true), snapshot(strict = true))
        assertFalse(v.block)
        assertEquals(ReasonCode.NO_TEXT, v.code)
    }

    @Test
    fun `в журнал уходит код и слово`() {
        val v = FilterRules.decide(input("промокод на первый заказ"), snapshot())
        assertEquals("PROMO_WORD" + Verdict.SEP + v.word, v.encode())
        assertEquals("рекламное слово: «${v.word}»", ReasonText.render(v.encode(), Lang.RU))
    }
}
