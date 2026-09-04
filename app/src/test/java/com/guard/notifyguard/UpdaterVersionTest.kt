package com.guard.notifyguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сравнение версий: либо назойливое «обновитесь» на ту же версию, либо молчание. */
class UpdaterVersionTest {

    @Test
    fun `более новая версия распознаётся`() {
        assertTrue(Updater.isNewer("3.0", "3.1"))
        assertTrue(Updater.isNewer("3.0", "4.0"))
        assertTrue(Updater.isNewer("3.9", "3.10"))
    }

    @Test
    fun `префикс v не мешает`() {
        assertTrue(Updater.isNewer("3.0", "v3.1"))
        assertTrue(Updater.isNewer("v3.0", "3.1"))
        assertFalse(Updater.isNewer("v3.0", "v3.0"))
    }

    @Test
    fun `одинаковые версии не считаются обновлением`() {
        assertFalse(Updater.isNewer("3.0", "3.0"))
        assertFalse(Updater.isNewer("3.0", "3.0.0"))
        assertFalse(Updater.isNewer("3.0.0", "3.0"))
    }

    @Test
    fun `старая версия не предлагается`() {
        assertFalse(Updater.isNewer("3.1", "3.0"))
        assertFalse(Updater.isNewer("3.10", "3.9"))
        assertFalse(Updater.isNewer("4.0", "3.99"))
    }

    @Test
    fun `дополнительный разряд считается новее`() {
        assertTrue(Updater.isNewer("3.0", "3.0.1"))
        assertFalse(Updater.isNewer("3.0.1", "3.0"))
    }

    @Test
    fun `суффикс пре-релиза не ломает разбор`() {
        assertTrue(Updater.isNewer("3.0", "3.1-beta"))
        assertFalse(Updater.isNewer("3.1", "3.1-beta"))
    }

    @Test
    fun `мусор не приводит к предложению обновиться`() {
        assertFalse(Updater.isNewer("3.0", "latest"))
        assertFalse(Updater.isNewer("3.0", ""))
        assertFalse(Updater.isNewer("", "3.0"))
        assertFalse(Updater.isNewer("3.0", "nightly-2026-09-01"))
    }
}
