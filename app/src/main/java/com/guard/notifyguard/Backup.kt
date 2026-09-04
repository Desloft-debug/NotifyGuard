package com.guard.notifyguard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

// Копия настроек пишется в файл через системный выбор места,
// поэтому сохранить можно в Google Диск, локально или куда угодно ещё.
object Backup {

    private const val FORMAT = 1
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_WORDS = 2000
    private const val MAX_APPS = 1000

    fun export(prefs: Prefs): String = JSONObject().apply {
        put("format", FORMAT)
        put("app", BuildConfig.VERSION_NAME)
        put("created", System.currentTimeMillis())
        put("filterEnabled", prefs.filterEnabled)
        put("strictMode", prefs.strictMode)
        put("silenceUnknownCalls", prefs.silenceUnknownCalls)
        put("storeLogText", prefs.storeLogText)
        put("keepAlive", prefs.keepAlive)
        put("updateCheck", prefs.updateCheckEnabled)
        put("remoteDict", prefs.remoteDictEnabled)
        put("region", prefs.region.name)
        put("theme", prefs.themeMode.name)
        put("lang", prefs.lang.name)
        put("blockWords", JSONArray(prefs.customBlockWords.sorted()))
        put("allowWords", JSONArray(prefs.customAllowWords.sorted()))
        put("allowedApps", JSONArray(prefs.allowedApps.sorted()))
        put("blockedApps", JSONArray(prefs.blockedApps.sorted()))
    }.toString(2)

    fun write(context: Context, uri: Uri, prefs: Prefs): Result<Unit> = runCatching {
        val out = context.contentResolver.openOutputStream(uri)
            ?: error("Нет доступа к файлу")
        out.use { it.write(export(prefs).toByteArray(Charsets.UTF_8)) }
    }

    fun read(context: Context, uri: Uri, prefs: Prefs): Result<Int> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Нет доступа к файлу")
        // Раньше здесь был readBytes() и только потом проверка длины: файл на два
        // гигабайта из облачного хранилища ронял приложение по OOM раньше проверки.
        val text = input.use { readLimited(it, MAX_BYTES) }
        apply(text, prefs)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): String {
        val out = ByteArrayOutputStream(16 * 1024)
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            require(out.size() + n <= limit) { "Файл слишком большой" }
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Файл может быть чужим или испорченным: читаем только известные ключи,
     * длины и количество ограничиваем, всё остальное игнорируем.
     *
     * Разбор и запись разделены. Раньше настройки писались по одной прямо по ходу
     * разбора, и файл, испорченный на середине, оставлял часть настроек из копии,
     * а часть старыми — понять, какие именно, было нельзя. Теперь любая ошибка
     * происходит до первой записи.
     *
     * Возвращает количество применённых пунктов. Значение сейчас нигде не показывается,
     * но оно логически осмысленно: настройка — один, каждый список — один.
     */
    fun apply(text: String, prefs: Prefs): Int {
        val plan = parse(text)
        return commit(plan, prefs)
    }

    private class Plan {
        val flags = LinkedHashMap<String, Boolean>()
        var region: Region? = null
        var theme: ThemeMode? = null
        var lang: Lang? = null
        var blockWords: Set<String>? = null
        var allowWords: Set<String>? = null
        var allowedApps: Set<String>? = null
        var blockedApps: Set<String>? = null

        val size: Int
            get() = flags.size +
                listOfNotNull(region, theme, lang).size +
                listOfNotNull(blockWords, allowWords, allowedApps, blockedApps).size
    }

    private val FLAG_KEYS = listOf(
        "filterEnabled", "strictMode", "silenceUnknownCalls", "storeLogText",
        "keepAlive", "updateCheck", "remoteDict"
    )

    private fun parse(text: String): Plan {
        val root = JSONObject(text)
        require(root.optInt("format") == FORMAT) { "Неизвестный формат" }

        val plan = Plan()
        for (key in FLAG_KEYS) {
            // getBoolean бросит исключение на мусорном значении — и это правильно:
            // до записи ещё ничего не дошло, пользователь узнает, что файл битый.
            if (root.has(key)) plan.flags[key] = root.getBoolean(key)
        }

        root.optString("region").takeIf { it.isNotBlank() }?.let { v ->
            plan.region = runCatching { Region.valueOf(v) }.getOrNull()
        }
        root.optString("theme").takeIf { it.isNotBlank() }?.let { v ->
            plan.theme = runCatching { ThemeMode.valueOf(v) }.getOrNull()
        }
        root.optString("lang").takeIf { it.isNotBlank() }?.let { v ->
            plan.lang = runCatching { Lang.valueOf(v) }.getOrNull()
        }

        plan.blockWords = words(root, "blockWords")
        plan.allowWords = words(root, "allowWords")
        plan.allowedApps = packages(root, "allowedApps")
        plan.blockedApps = packages(root, "blockedApps")
        return plan
    }

    private fun commit(plan: Plan, prefs: Prefs): Int {
        plan.flags.forEach { (key, value) ->
            when (key) {
                "filterEnabled" -> prefs.filterEnabled = value
                "strictMode" -> prefs.strictMode = value
                "silenceUnknownCalls" -> prefs.silenceUnknownCalls = value
                "storeLogText" -> prefs.storeLogText = value
                "keepAlive" -> prefs.keepAlive = value
                "updateCheck" -> prefs.updateCheckEnabled = value
                "remoteDict" -> prefs.remoteDictEnabled = value
            }
        }
        plan.region?.let { prefs.region = it }
        plan.theme?.let { prefs.themeMode = it }
        plan.lang?.let { prefs.lang = it }
        plan.blockWords?.let { prefs.customBlockWords = it }
        plan.allowWords?.let { prefs.customAllowWords = it }
        plan.allowedApps?.let { prefs.allowedApps = it }
        plan.blockedApps?.let { prefs.blockedApps = it }
        return plan.size
    }

    private fun words(root: JSONObject, key: String): Set<String>? {
        val arr = root.optJSONArray(key) ?: return null
        val out = LinkedHashSet<String>()
        for (i in 0 until minOf(arr.length(), MAX_WORDS)) {
            val w = arr.optString(i).trim().lowercase()
            if (w.length in 2..40) out.add(w)
        }
        return out
    }

    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    private fun packages(root: JSONObject, key: String): Set<String>? {
        val arr = root.optJSONArray(key) ?: return null
        val out = LinkedHashSet<String>()
        for (i in 0 until minOf(arr.length(), MAX_APPS)) {
            val p = arr.optString(i).trim()
            if (p.length <= 200 && PKG.matches(p)) out.add(p)
        }
        return out
    }

    fun fileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "notifyguard-$stamp.json"
    }
}
