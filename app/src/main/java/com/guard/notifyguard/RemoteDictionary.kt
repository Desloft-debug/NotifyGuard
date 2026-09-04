package com.guard.notifyguard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

// Словарь из репозитория: правится без выпуска новой версии приложения.
object RemoteDictionary {

    private const val URL_TEMPLATE =
        "https://raw.githubusercontent.com/%s/%s/main/dictionary.json"

    private const val MAX_WORDS = 3000
    private const val MIN_LEN = 3
    private const val MAX_LEN = 40
    private const val MAX_BODY_BYTES = 512 * 1024
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun url(): String =
        String.format(URL_TEMPLATE, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)

    private val PROTECTED_WORDS: Set<String> by lazy {
        (Dictionaries.EMERGENCY +
            Dictionaries.SYSTEM +
            Dictionaries.CODE +
            Dictionaries.MONEY +
            Dictionaries.DELIVERY).toSet()
    }

    fun shouldSync(prefs: Prefs): Boolean =
        prefs.remoteDictEnabled &&
            System.currentTimeMillis() - prefs.remoteDictFetched > DAY_MS

    // Ключ разбора: длина + хеш json и регион. Сравнивать строку на 512 КБ целиком
    // на каждое уведомление смысла нет, а коллизия хеша безобидна — словарь просто
    // останется прежним до следующей синхронизации.
    private data class MemoKey(val length: Int, val hash: Int, val region: Region)

    // объявляем раньше memo: инициализаторы в object идут сверху вниз
    private val EMPTY = RemoteDict(0, "", emptyList(), emptyList())

    @Volatile
    private var memoKey: MemoKey? = null

    @Volatile
    private var memo: RemoteDict = EMPTY

    fun cached(prefs: Prefs): RemoteDict {
        if (!prefs.remoteDictEnabled) return EMPTY
        val raw = prefs.remoteDictJson
        if (raw.isBlank()) return EMPTY

        val key = MemoKey(raw.length, raw.hashCode(), prefs.region)
        memoKey?.let { if (it == key) return memo }

        val parsed = runCatching { parse(JSONObject(raw), key.region) }.getOrDefault(EMPTY)
        memo = parsed
        memoKey = key
        return parsed
    }

    private fun invalidate() {
        memoKey = null
        memo = EMPTY
    }

    suspend fun sync(prefs: Prefs): Result<RemoteDict> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "NotifyGuard")
                setRequestProperty("Accept", "application/json")
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

                val body = readLimited(conn)

                val json = JSONObject(body)
                val dict = parse(json, prefs.region)
                require(!dict.isEmpty) { "Пустой словарь" }

                prefs.remoteDictJson = body
                prefs.remoteDictEtag = conn.getHeaderField("ETag").orEmpty()
                prefs.remoteDictFetched = System.currentTimeMillis()
                invalidate()
                dict
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun readLimited(conn: HttpURLConnection): String {
        val out = ByteArrayOutputStream(32 * 1024)
        conn.inputStream.use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                require(out.size() + n <= MAX_BODY_BYTES) { "Словарь слишком большой" }
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    // В файле есть общие списки block/allow и разделы по регионам.
    private fun parse(json: JSONObject, region: Region): RemoteDict {
        val sections = when (region) {
            Region.RU -> listOf("ru")
            Region.EN -> listOf("en")
            Region.ALL -> listOf("ru", "en")
        }

        val block = ArrayList<String>()
        val allow = ArrayList<String>()
        block += words(json.optJSONArray("block"))
        allow += words(json.optJSONArray("allow"))
        for (name in sections) {
            val sec = json.optJSONObject(name) ?: continue
            block += words(sec.optJSONArray("block"))
            allow += words(sec.optJSONArray("allow"))
        }

        // Потолок именно на итог: в файле шесть массивов, и лимит на каждый
        // из них по отдельности дал бы 18 000 слов на проход по тексту.
        return RemoteDict(
            version = json.optInt("version", 0),
            updated = json.optString("updated"),
            block = block.distinct().filter { it !in PROTECTED_WORDS }.take(MAX_WORDS),
            allow = allow.distinct().take(MAX_WORDS)
        )
    }

    private fun words(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val limit = minOf(arr.length(), MAX_WORDS)
        val out = ArrayList<String>(limit)
        for (i in 0 until limit) {
            val w = arr.optString(i).trim().lowercase()
            if (w.length in MIN_LEN..MAX_LEN) out.add(w)
        }
        return out.distinct()
    }

    fun clear(prefs: Prefs) {
        prefs.remoteDictJson = ""
        prefs.remoteDictEtag = ""
        prefs.remoteDictFetched = 0L
        invalidate()
    }
}
