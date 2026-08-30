package com.guard.notifyguard

import android.content.Context

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
    val promoWords: List<String>
)

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("notifyguard", Context.MODE_PRIVATE)

    @Volatile
    private var cached: Snapshot? = null

    @Volatile
    private var cachedAt: Long = 0L

    init {
        if (!sp.getBoolean(KEY_SEEDED, false)) {
            sp.edit()
                .putStringSet(KEY_BLOCK_WORDS, DEFAULT_BLOCK_WORDS)
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
    }

    // Кеш на CACHE_MS: не перечитываем коллекции на каждое уведомление
    fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_MS) return it }

        val reg = region
        val remote = RemoteDictionary.cached(this)
        val fresh = Snapshot(
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
        cached = fresh
        cachedAt = now
        return fresh
    }

    var filterEnabled: Boolean
        get() = sp.getBoolean(KEY_FILTER, true)
        set(v) = sp.edit().putBoolean(KEY_FILTER, v).apply()

    var strictMode: Boolean
        get() = sp.getBoolean(KEY_STRICT, false)
        set(v) = sp.edit().putBoolean(KEY_STRICT, v).apply()

    // Постоянный сервис: без него процесс выгружается при очистке памяти
    var keepAlive: Boolean
        get() = sp.getBoolean(KEY_KEEP_ALIVE, true)
        set(v) = sp.edit().putBoolean(KEY_KEEP_ALIVE, v).apply()

    var silenceUnknownCalls: Boolean
        get() = sp.getBoolean(KEY_SILENCE_CALLS, false)
        set(v) = sp.edit().putBoolean(KEY_SILENCE_CALLS, v).apply()

    var storeLogText: Boolean
        get() = sp.getBoolean(KEY_STORE_TEXT, true)
        set(v) = sp.edit().putBoolean(KEY_STORE_TEXT, v).apply()

    var updateCheckEnabled: Boolean
        get() = sp.getBoolean(KEY_UPDATE_CHECK, false)
        set(v) = sp.edit().putBoolean(KEY_UPDATE_CHECK, v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_CHECK, v).apply()

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

    var region: Region
        get() = runCatching {
            Region.valueOf(sp.getString(KEY_REGION, null) ?: defaultRegion().name)
        }.getOrDefault(defaultRegion())
        set(v) = sp.edit().putString(KEY_REGION, v.name).apply()

    var regionChosen: Boolean
        get() = sp.getBoolean(KEY_REGION_CHOSEN, false)
        set(v) = sp.edit().putBoolean(KEY_REGION_CHOSEN, v).apply()

    private fun defaultRegion(): Region =
        if (java.util.Locale.getDefault().language.equals("ru", true)) Region.RU
        else Region.EN

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

    fun toggleBlocked(pkg: String) {
        val set = blockedApps.toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        blockedApps = set
        if (pkg in allowedApps) allowedApps = allowedApps - pkg
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
        private const val KEY_KEEP_ALIVE = "keep_alive"
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
        private const val CACHE_MS = 5_000L
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_REGION = "region"
        private const val KEY_REGION_CHOSEN = "region_chosen"
        private const val KEY_REMOTE_ON = "remote_dict_on"
        private const val KEY_REMOTE_JSON = "remote_dict_json"
        private const val KEY_REMOTE_ETAG = "remote_dict_etag"
        private const val KEY_REMOTE_TIME = "remote_dict_time"
    }
}
