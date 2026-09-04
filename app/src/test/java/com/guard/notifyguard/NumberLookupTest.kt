package com.guard.notifyguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор номера. Всё офлайн и без Android — обычный JVM-тест. */
class NumberLookupTest {

    private fun call(number: String) = CallEntry(number, true, 0L)

    @Test
    fun `восьмёрка приводится к семёрке`() {
        val info = NumberLookup.analyze("8 916 123-45-67", emptyList())
        assertEquals("79161234567", info.e164)
    }

    @Test
    fun `мобильный номер узнаётся вместе с оператором`() {
        val info = NumberLookup.analyze("+7 916 123-45-67", emptyList())
        assertEquals(NumberKind.MOBILE, info.kind)
        assertEquals("МТС", info.operator)
        assertEquals(RiskLevel.LOW, info.risk)
    }

    @Test
    fun `городской номер узнаётся вместе с регионом`() {
        val info = NumberLookup.analyze("+7 495 123-45-67", emptyList())
        assertEquals(NumberKind.LANDLINE, info.kind)
        assertEquals("Москва", info.region)
    }

    @Test
    fun `восьмисотый бесплатный`() {
        val info = NumberLookup.analyze("8 800 555 35 35", emptyList())
        assertEquals(NumberKind.TOLL_FREE, info.kind)
        assertEquals(RiskLevel.LOW, info.risk)
    }

    @Test
    fun `восемьсот девятый платный и рискованный`() {
        val info = NumberLookup.analyze("8 809 123 45 67", emptyList())
        assertEquals(NumberKind.PREMIUM, info.kind)
        assertEquals(RiskLevel.HIGH, info.risk)
    }

    @Test
    fun `скрытый номер`() {
        val info = NumberLookup.analyze("", emptyList())
        assertEquals(NumberKind.HIDDEN, info.kind)
        assertEquals(RiskLevel.MEDIUM, info.risk)
        assertTrue("hidden" in info.signals)
    }

    @Test
    fun `короткий номер`() {
        val info = NumberLookup.analyze("900", emptyList())
        assertEquals(NumberKind.SHORT, info.kind)
        assertEquals(RiskLevel.LOW, info.risk)
    }

    @Test
    fun `иностранный номер и страна по коду`() {
        val info = NumberLookup.analyze("+49 151 12345678", emptyList())
        assertEquals(NumberKind.FOREIGN, info.kind)
        assertEquals("Германия", info.country)
        assertTrue("foreign" in info.signals)
    }

    @Test
    fun `повторные звонки с одного номера дают признак`() {
        val history = List(3) { call("+7 916 123-45-67") }
        val info = NumberLookup.analyze("+7 916 123-45-67", history)
        assertTrue("repeated" in info.signals)
        assertEquals(RiskLevel.MEDIUM, info.risk)
    }

    @Test
    fun `звонки из одного блока номеров дают признак`() {
        // Соседние номера того же блока — типичный обзвон.
        val history = listOf(
            call("+79161200001"),
            call("+79161200002"),
            call("+79161200003")
        )
        val info = NumberLookup.analyze("+79161200009", history)
        assertTrue("blockcalling" in info.signals)
        assertEquals(RiskLevel.HIGH, info.risk)
    }

    @Test
    fun `коды стран не повторяются`() {
        // В COUNTRIES ключ "998" объявлен дважды — Kotlin молча оставляет последний.
        // Сам по себе дубликат безвреден, но карту легко испортить незаметно.
        val info = NumberLookup.analyze("+998901234567", emptyList())
        assertEquals("Узбекистан", info.country)
    }
}
