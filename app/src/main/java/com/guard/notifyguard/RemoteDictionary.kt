package com.guard.notifyguard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteDict(
    val version: Int,
    val updated: String,
    val block: List<String>,
    val allow: List<String>
) {
    val isEmpty: Boolean get() = block.isEmpty() && allow.isEmpty()
}

/**
 * Словарь, который лежит в репозитории и обновляется без выпуска новой версии.
 *
 * Ограничения намеренные:
 *  - удалённые слова НЕ могут перекрыть экстренные оповещения, состояние
 *    устройства, коды подтверждения и операции по счёту — эти списки
 *    зашиты в приложение и проверяются раньше;
 *  - слово короче трёх символов игнорируется, иначе одна опечатка
 *    в файле скрыла бы половину уведомлений у всех сразу;
 *  - объём ограничен, слишком большой файл отбрасывается целиком.
 */
object RemoteDictionary {

    private const val URL_TEMPLATE =
        "https://raw.githubusercontent.com/%s/%s/main/dictionary.json"

    private const val MAX_WORDS = 3000
    private const val MIN_LEN = 3
    private const val MAX_LEN = 40
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun url(): String =
        String.format(URL_TEMPLATE, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)

    /** Слова, которые удалённый словарь не имеет права блокировать. */
    private val PROTECTED_WORDS: Set<String> by lazy {
        (FilterRules.EMERGENCY_WORDS +
            FilterRules.SYSTEM_WORDS +
            FilterRules.CODE_WORDS +
            FilterRules.MONEY_WORDS +
            FilterRules.DELIVERY_WORDS).toSet()
    }

    fun shouldSync(prefs: Prefs): Boolean =
        prefs.remoteDictEnabled &&
            System.currentTimeMillis() - prefs.remoteDictFetched > DAY_MS

    fun cached(prefs: Prefs): RemoteDict {
        if (!prefs.remoteDictEnabled) return EMPTY
        val raw = prefs.remoteDictJson
        if (raw.isBlank()) return EMPTY
        return runCatching { parse(JSONObject(raw)) }.getOrDefault(EMPTY)
    }

    suspend fun sync(prefs: Prefs): Result<RemoteDict> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "NotifyGuard")
                setRequestProperty("Accept", "application/json")
                // Экономит трафик: сервер ответит 304, если файл не менялся
                prefs.remoteDictEtag.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("If-None-Match", it)
                }
            }

            try {
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    prefs.remoteDictFetched = System.currentTimeMillis()
                    return@runCatching cached(prefs)
                }
                require(conn.responseCode == HttpURLConnection.HTTP_OK) {
                    "HTTP ${conn.responseCode}"
                }
                val body = conn.inputStream.bufferedReader().readText()
                require(body.length < 512 * 1024) { "Словарь слишком большой" }

                val json = JSONObject(body)
                val dict = parse(json)
                require(!dict.isEmpty) { "Пустой словарь" }

                prefs.remoteDictJson = body
                prefs.remoteDictEtag = conn.getHeaderField("ETag").orEmpty()
                prefs.remoteDictFetched = System.currentTimeMillis()
                dict
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parse(json: JSONObject): RemoteDict {
        val block = words(json.optJSONArray("block"))
            .filter { w -> PROTECTED_WORDS.none { it == w } }
        val allow = words(json.optJSONArray("allow"))
        return RemoteDict(
            version = json.optInt("version", 0),
            updated = json.optString("updated"),
            block = block,
            allow = allow
        )
    }

    private fun words(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(minOf(arr.length(), MAX_WORDS))
        for (i in 0 until minOf(arr.length(), MAX_WORDS)) {
            val w = arr.optString(i).trim().lowercase()
            if (w.length in MIN_LEN..MAX_LEN) out.add(w)
        }
        return out.distinct()
    }

    fun clear(prefs: Prefs) {
        prefs.remoteDictJson = ""
        prefs.remoteDictEtag = ""
        prefs.remoteDictFetched = 0L
    }

    private val EMPTY = RemoteDict(0, "", emptyList(), emptyList())
}
