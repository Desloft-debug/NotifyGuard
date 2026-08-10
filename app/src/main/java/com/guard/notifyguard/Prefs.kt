package com.guard.notifyguard

import android.content.Context

/**
 * Настройки приложения. Читаются сервисами на каждом событии,
 * поэтому держим их простыми и без кеша.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("notifyguard", Context.MODE_PRIVATE)

    /** Главный выключатель фильтра уведомлений. */
    var filterEnabled: Boolean
        get() = sp.getBoolean(KEY_FILTER, true)
        set(v) = sp.edit().putBoolean(KEY_FILTER, v).apply()

    /**
     * Строгий режим: всё, что не попало в белый список приложений
     * и не похоже на код или перевод, снимается.
     */
    var strictMode: Boolean
        get() = sp.getBoolean(KEY_STRICT, false)
        set(v) = sp.edit().putBoolean(KEY_STRICT, v).apply()

    /** Приглушать звонки с номеров, которых нет в контактах. */
    var silenceUnknownCalls: Boolean
        get() = sp.getBoolean(KEY_SILENCE_CALLS, false)
        set(v) = sp.edit().putBoolean(KEY_SILENCE_CALLS, v).apply()

    /** Пакеты, уведомления которых не трогаем никогда. */
    var allowedApps: Set<String>
        get() = sp.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_ALLOWED, v).apply()

    /** Пакеты, уведомления которых снимаем всегда. */
    var blockedApps: Set<String>
        get() = sp.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCKED, v).apply()

    /**
     * Стоп-слова пользователя. Срабатывают раньше проверки на коды
     * и переводы, поэтому «кредит» скроет уведомление даже с суммой.
     * Экстренные сообщения и состояние устройства они не перекрывают.
     */
    var customBlockWords: Set<String>
        get() = sp.getStringSet(KEY_BLOCK_WORDS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCK_WORDS, v).apply()

    /** Слова-исключения: если встретились, уведомление не скрывается. */
    var customAllowWords: Set<String>
        get() = sp.getStringSet(KEY_ALLOW_WORDS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_ALLOW_WORDS, v).apply()

    fun addBlockWord(w: String) {
        val word = normalize(w) ?: return
        customBlockWords = customBlockWords + word
        customAllowWords = customAllowWords - word
    }

    fun removeBlockWord(w: String) {
        customBlockWords = customBlockWords - w
    }

    fun addAllowWord(w: String) {
        val word = normalize(w) ?: return
        customAllowWords = customAllowWords + word
        customBlockWords = customBlockWords - word
    }

    fun removeAllowWord(w: String) {
        customAllowWords = customAllowWords - w
    }

    private fun normalize(w: String): String? {
        val t = w.trim().lowercase()
        return if (t.length < 2) null else t
    }

    fun toggleAllowed(pkg: String) {
        val set = allowedApps.toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        allowedApps = set
        if (pkg in blockedApps) blockedApps = blockedApps - pkg
    }

    fun toggleBlocked(pkg: String) {
        val set = blockedApps.toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        blockedApps = set
        if (pkg in allowedApps) allowedApps = allowedApps - pkg
    }

    private companion object {
        const val KEY_FILTER = "filter_enabled"
        const val KEY_STRICT = "strict_mode"
        const val KEY_SILENCE_CALLS = "silence_unknown_calls"
        const val KEY_ALLOWED = "allowed_apps"
        const val KEY_BLOCKED = "blocked_apps"
        const val KEY_BLOCK_WORDS = "custom_block_words"
        const val KEY_ALLOW_WORDS = "custom_allow_words"
    }
}
