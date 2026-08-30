package com.guard.notifyguard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

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
        out.use { it.write(export(prefs).toByteArray()) }
    }

    fun read(context: Context, uri: Uri, prefs: Prefs): Result<Int> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Нет доступа к файлу")
        val text = input.use {
            val bytes = it.readBytes()
            require(bytes.size <= MAX_BYTES) { "Файл слишком большой" }
            String(bytes)
        }
        apply(text, prefs)
    }

    // Файл может быть чужим или испорченным: читаем только известные ключи,
    // длины и количество ограничиваем, всё остальное игнорируем.
    fun apply(text: String, prefs: Prefs): Int {
        val root = JSONObject(text)
        require(root.optInt("format") == FORMAT) { "Неизвестный формат" }
        var changed = 0

        fun flag(key: String, set: (Boolean) -> Unit) {
            if (root.has(key)) {
                set(root.getBoolean(key)); changed++
            }
        }
        flag("filterEnabled") { prefs.filterEnabled = it }
        flag("strictMode") { prefs.strictMode = it }
        flag("silenceUnknownCalls") { prefs.silenceUnknownCalls = it }
        flag("storeLogText") { prefs.storeLogText = it }
        flag("keepAlive") { prefs.keepAlive = it }
        flag("updateCheck") { prefs.updateCheckEnabled = it }
        flag("remoteDict") { prefs.remoteDictEnabled = it }

        root.optString("region").takeIf { it.isNotBlank() }?.let { v ->
            runCatching { prefs.region = Region.valueOf(v) }.onSuccess { changed++ }
        }
        root.optString("theme").takeIf { it.isNotBlank() }?.let { v ->
            runCatching { prefs.themeMode = ThemeMode.valueOf(v) }.onSuccess { changed++ }
        }
        root.optString("lang").takeIf { it.isNotBlank() }?.let { v ->
            runCatching { prefs.lang = Lang.valueOf(v) }.onSuccess { changed++ }
        }

        words(root, "blockWords")?.let { prefs.customBlockWords = it; changed += it.size }
        words(root, "allowWords")?.let { prefs.customAllowWords = it; changed += it.size }
        packages(root, "allowedApps")?.let { prefs.allowedApps = it; changed += it.size }
        packages(root, "blockedApps")?.let { prefs.blockedApps = it; changed += it.size }
        return changed
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
