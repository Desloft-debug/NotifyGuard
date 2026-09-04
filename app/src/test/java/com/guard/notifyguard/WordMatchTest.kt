package com.guard.notifyguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Границы слов. Правило одно: перед словом не должно стоять буквы или цифры,
 * продолжение допускается.
 *
 * Эти проверки заодно фиксируют эквивалентность старой регулярке
 * "(?<![\p{L}\p{N}])" + Regex.escape(needle) — если кто-то решит вернуть regex,
 * тесты покажут, совпадает ли поведение.
 */
class WordMatchTest {

    @Test
    fun `слово в начале строки находится`() {
        assertTrue(WordMatch.contains("скидка 50%", "скидк"))
    }

    @Test
    fun `слово после пробела находится`() {
        assertTrue(WordMatch.contains("только сегодня скидка", "скидк"))
    }

    @Test
    fun `окончание не мешает`() {
        assertTrue(WordMatch.contains("получите скидки", "скидк"))
        assertTrue(WordMatch.contains("код: 4821", "код"))
        assertTrue(WordMatch.contains("воспользуйтесь кодом", "код"))
    }

    @Test
    fun `внутри другого слова не срабатывает`() {
        // Ради этого правила всё и затевалось: промокод не должен считаться
        // кодом подтверждения.
        assertFalse(WordMatch.contains("ваш промокод sale20", "код"))
        assertFalse(WordMatch.contains("акционерное общество", "акции"))
    }

    @Test
    fun `цифра слева тоже граница слова не даёт`() {
        assertFalse(WordMatch.contains("qr7код", "код"))
    }

    @Test
    fun `знаки препинания границей считаются`() {
        assertTrue(WordMatch.contains("внимание,скидка", "скидк"))
        assertTrue(WordMatch.contains("(скидка)", "скидк"))
        assertTrue(WordMatch.contains("тема:код", "код"))
    }

    @Test
    fun `второе вхождение находится если первое внутри слова`() {
        // «промокод» не подходит, но дальше в тексте есть отдельное «код»
        assertTrue(WordMatch.contains("промокод и код 1234", "код"))
    }

    @Test
    fun `пустое слово и слишком длинное не находятся`() {
        assertFalse(WordMatch.contains("любой текст", ""))
        assertFalse(WordMatch.contains("код", "код подтверждения"))
    }

    @Test
    fun `firstMatch возвращает первое по порядку списка`() {
        val words = listOf("скидк", "промокод")
        // порядок значим: причина в журнале показывает именно это слово
        assertTrue(WordMatch.firstMatch("промокод и скидка", words) == "скидк")
    }

    @Test
    fun `firstMatch на пустом списке возвращает null`() {
        assertTrue(WordMatch.firstMatch("любой текст", emptyList()) == null)
    }

    @Test
    fun `matches сам приводит регистр`() {
        assertTrue(WordMatch.matches("СКИДКА 50%", "  Скидк  "))
        assertTrue(WordMatch.matches("Ihr RABATT", "rabatt"))
    }
}
