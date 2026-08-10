package com.guard.notifyguard

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("notifyguard", Context.MODE_PRIVATE)

    init {
        if (!sp.getBoolean(KEY_SEEDED, false)) {
            sp.edit()
                .putStringSet(KEY_BLOCK_WORDS, DEFAULT_BLOCK_WORDS)
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
    }

    var filterEnabled: Boolean
        get() = sp.getBoolean(KEY_FILTER, true)
        set(v) = sp.edit().putBoolean(KEY_FILTER, v).apply()

    var strictMode: Boolean
        get() = sp.getBoolean(KEY_STRICT, false)
        set(v) = sp.edit().putBoolean(KEY_STRICT, v).apply()

    var silenceUnknownCalls: Boolean
        get() = sp.getBoolean(KEY_SILENCE_CALLS, false)
        set(v) = sp.edit().putBoolean(KEY_SILENCE_CALLS, v).apply()

    /** Хранить ли текст уведомления в журнале. */
    var storeLogText: Boolean
        get() = sp.getBoolean(KEY_STORE_TEXT, true)
        set(v) = sp.edit().putBoolean(KEY_STORE_TEXT, v).apply()

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "SYSTEM")
        }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString(KEY_THEME, v.name).apply()

    var lang: Lang
        get() = runCatching {
            Lang.valueOf(sp.getString(KEY_LANG, null) ?: "SYSTEM")
        }.getOrDefault(Lang.SYSTEM)
        set(v) = sp.edit().putString(KEY_LANG, v.name).apply()

    var allowedApps: Set<String>
        get() = sp.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_ALLOWED, v).apply()

    var blockedApps: Set<String>
        get() = sp.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCKED, v).apply()

    var customBlockWords: Set<String>
        get() = sp.getStringSet(KEY_BLOCK_WORDS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCK_WORDS, v).apply()

    var customAllowWords: Set<String>
        get() = sp.getStringSet(KEY_ALLOW_WORDS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_ALLOW_WORDS, v).apply()

    /**
     * Пакеты, от которых уже приходили уведомления.
     * Используется вместо чтения списка всех установленных приложений:
     * так приложению не нужно разрешение QUERY_ALL_PACKAGES.
     */
    var seenApps: Set<String>
        get() = sp.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_SEEN, v).apply()

    @Synchronized
    fun rememberApp(pkg: String) {
        val cur = seenApps
        if (pkg !in cur && cur.size < 400) seenApps = cur + pkg
    }

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
        return if (t.length < 2 || t.length > 40) null else t
    }

    fun toggleAllowed(pkg: String) {
        val set = allowedApps.toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        allowedApps = set
        if (pkg in blockedApps) blockedApps = blockedApps - pkg
    }

    companion object {
        /** Заполняется один раз при первом запуске, дальше правится пользователем. */
        val DEFAULT_BLOCK_WORDS = setOf(
            "акции", "деньги", "займы", "кредит", "обновление",
            "подписка", "процент", "рекомендации", "скидка"
        )

        private const val KEY_FILTER = "filter_enabled"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_SILENCE_CALLS = "silence_unknown_calls"
        private const val KEY_STORE_TEXT = "store_log_text"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANG = "lang"
        private const val KEY_ALLOWED = "allowed_apps"
        private const val KEY_BLOCKED = "blocked_apps"
        private const val KEY_BLOCK_WORDS = "custom_block_words"
        private const val KEY_ALLOW_WORDS = "custom_allow_words"
        private const val KEY_SEEN = "seen_apps"
        private const val KEY_SEEDED = "seeded_v2"
    }
}
