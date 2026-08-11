package com.guard.notifyguard

import android.content.Context
import android.content.SharedPreferences

/**
 * Снимок настроек, нужных фильтру. Читается на каждом уведомлении,
 * поэтому собирается один раз и переиспользуется, пока настройки
 * не изменятся. Без этого на каждое уведомление создавалось
 * шесть новых коллекций.
 */
data class Snapshot(
    val filterEnabled: Boolean,
    val strictMode: Boolean,
    val storeLogText: Boolean,
    val allowedApps: Set<String>,
    val blockedApps: Set<String>,
    val blockWords: List<String>,
    val allowWords: List<String>,
    val remoteBlock: List<String>,
    val remoteAllow: List<String>,
    val region: Region,
    /** Рекламные слова выбранного региона — собираются один раз. */
    val promoWords: List<String>
)

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("notifyguard", Context.MODE_PRIVATE)

    @Volatile
    private var cached: Snapshot? = null

    private val invalidator = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        cached = null
    }

    init {
        if (!sp.getBoolean(KEY_SEEDED, false)) {
            sp.edit()
                .putStringSet(KEY_BLOCK_WORDS, DEFAULT_BLOCK_WORDS)
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
        sp.registerOnSharedPreferenceChangeListener(invalidator)
    }

    /** Дешёвое чтение для сервисов. */
    fun snapshot(): Snapshot {
        cached?.let { return it }
        val reg = region
        val remote = RemoteDictionary.cached(this)
        val s = Snapshot(
            filterEnabled = filterEnabled,
            strictMode = strictMode,
            storeLogText = storeLogText,
            allowedApps = allowedApps,
            blockedApps = blockedApps,
            blockWords = customBlockWords.toList(),
            allowWords = customAllowWords.toList(),
            remoteBlock = remote.block,
            remoteAllow = remote.allow,
            region = reg,
            promoWords = Dictionaries.promoFor(reg)
        )
        cached = s
        return s
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

    var storeLogText: Boolean
        get() = sp.getBoolean(KEY_STORE_TEXT, true)
        set(v) = sp.edit().putBoolean(KEY_STORE_TEXT, v).apply()

    /**
     * Проверять ли обновления на GitHub при открытии приложения.
     * По умолчанию выключено: скачивание чего-либо из сети должно быть
     * осознанным решением пользователя, а не поведением по умолчанию.
     * Этого же требуют правила F-Droid и IzzyOnDroid.
     */
    var updateCheckEnabled: Boolean
        get() = sp.getBoolean(KEY_UPDATE_CHECK, false)
        set(v) = sp.edit().putBoolean(KEY_UPDATE_CHECK, v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_CHECK, v).apply()

    /** Подтягивать ли словарь из репозитория. */
    var remoteDictEnabled: Boolean
        get() = sp.getBoolean(KEY_REMOTE_ON, true)
        set(v) = sp.edit().putBoolean(KEY_REMOTE_ON, v).apply()

    var remoteDictJson: String
        get() = sp.getString(KEY_REMOTE_JSON, "") ?: ""
        set(v) = sp.edit().putString(KEY_REMOTE_JSON, v).apply()

    var remoteDictEtag: String
        get() = sp.getString(KEY_REMOTE_ETAG, "") ?: ""
        set(v) = sp.edit().putString(KEY_REMOTE_ETAG, v).apply()

    var remoteDictFetched: Long
        get() = sp.getLong(KEY_REMOTE_TIME, 0L)
        set(v) = sp.edit().putLong(KEY_REMOTE_TIME, v).apply()

    /**
     * Регион словаря. Выбирается при первом запуске:
     * русские рекламные слова англоязычному пользователю только мешают.
     */
    var region: Region
        get() = runCatching {
            Region.valueOf(sp.getString(KEY_REGION, null) ?: defaultRegion().name)
        }.getOrDefault(defaultRegion())
        set(v) = sp.edit().putString(KEY_REGION, v.name).apply()

    /** Показывать ли экран выбора региона. */
    var regionChosen: Boolean
        get() = sp.getBoolean(KEY_REGION_CHOSEN, false)
        set(v) = sp.edit().putBoolean(KEY_REGION_CHOSEN, v).apply()

    private fun defaultRegion(): Region =
        if (java.util.Locale.getDefault().language.equals("ru", true)) Region.RU
        else Region.EN

    /** Инструкция скрыта пользователем. */
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDING, v).apply()

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
        val DEFAULT_BLOCK_WORDS = setOf(
            "акции", "деньги", "займы", "кредит", "обновление",
            "подписка", "процент", "рекомендации", "скидка"
        )

        private const val KEY_FILTER = "filter_enabled"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_SILENCE_CALLS = "silence_unknown_calls"
        private const val KEY_STORE_TEXT = "store_log_text"
        private const val KEY_UPDATE_CHECK = "update_check"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANG = "lang"
        private const val KEY_ALLOWED = "allowed_apps"
        private const val KEY_BLOCKED = "blocked_apps"
        private const val KEY_BLOCK_WORDS = "custom_block_words"
        private const val KEY_ALLOW_WORDS = "custom_allow_words"
        private const val KEY_SEEN = "seen_apps"
        private const val KEY_SEEDED = "seeded_v2"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_REGION = "region"
        private const val KEY_REGION_CHOSEN = "region_chosen"
        private const val KEY_REMOTE_ON = "remote_dict_on"
        private const val KEY_REMOTE_JSON = "remote_dict_json"
        private const val KEY_REMOTE_ETAG = "remote_dict_etag"
        private const val KEY_REMOTE_TIME = "remote_dict_time"
    }
}
