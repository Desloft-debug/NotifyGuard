package com.guard.notifyguard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /**
     * SHA-256 в нижнем регистре, если он есть в тексте релиза (его пишет release.yml).
     * Пусто — сумму не проверяем, подпись APK сверяется в любом случае.
     */
    val sha256: String = ""
)

// Обновление через публичный API GitHub, без токена.
object Updater {

    private const val API =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MAX_JSON_BYTES = 1024 * 1024
    private const val MAX_APK_BYTES = 64L * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    // Ссылка на APK приходит из json, так что хост проверяем на каждом шаге —
    // включая редиректы, отсюда и ручной их разбор в open().
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "api.github.com",
        "codeload.github.com"
    )

    private fun isAllowed(url: URL): Boolean {
        if (!url.protocol.equals("https", ignoreCase = true)) return false
        val host = url.host.lowercase()
        return host in ALLOWED_HOSTS || host.endsWith(".githubusercontent.com")
    }

    fun shouldCheck(prefs: Prefs): Boolean =
        prefs.updateCheckEnabled &&
            System.currentTimeMillis() - prefs.lastUpdateCheck > DAY_MS

    suspend fun fetchLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(URL(API)) {
                it.setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = conn.use { c ->
                // 403 у GitHub без токена — это лимит 60 запросов в час на IP,
                // на общем Wi-Fi выбирается влёгкую.
                require(c.responseCode == HttpURLConnection.HTTP_OK) {
                    if (c.responseCode == 403) "rate limited" else "HTTP ${c.responseCode}"
                }
                readLimited(c, MAX_JSON_BYTES)
            }
            val json = JSONObject(body)

            val tag = json.optString("tag_name")
            val notes = json.optString("body").take(600)
            val assets = json.optJSONArray("assets")

            var url = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            require(tag.isNotBlank() && url.isNotBlank()) { "no apk in release" }
            require(isAllowed(URL(url))) { "apk url is not on github" }

            ReleaseInfo(
                tag = tag,
                version = tag.removePrefix("v"),
                notes = notes,
                apkUrl = url,
                sizeBytes = size,
                sha256 = findSha256(json.optString("body"))
            )
        }
    }

    /** Ищет в описании релиза "sha256: <64 hex>", регистр и разделитель любые. */
    private fun findSha256(body: String): String =
        Regex("sha-?256[^0-9a-f]{0,4}([0-9a-fA-F]{64})", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.lowercase().orEmpty()

    fun isNewer(current: String, remote: String): Boolean {
        val a = parseVersion(current)
        val b = parseVersion(remote)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    // "v3.1-beta2" -> [3, 1]. Суффикс отрезаем целиком: иначе "3.1-beta"
    // превращается в [3] и выглядит как 3.0.
    private fun parseVersion(v: String): List<Int> {
        val core = v.removePrefix("v").substringBefore('-').substringBefore('+')
        val out = ArrayList<Int>(4)
        for (part in core.split('.')) {
            val n = part.trim().toIntOrNull() ?: return emptyList()
            out.add(n)
        }
        return out
    }

    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(isAllowed(URL(info.apkUrl))) { "apk url is not on github" }

            val dir = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(dir, "NotifyGuard-${info.tag}.apk")

            val conn = open(URL(info.apkUrl)) {
                it.connectTimeout = 15_000
                it.readTimeout = 30_000
            }
            conn.use { c ->
                require(c.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${c.responseCode}" }

                val total = if (info.sizeBytes > 0) info.sizeBytes else c.contentLength.toLong()
                require(total <= MAX_APK_BYTES) { "apk too big" }

                c.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            done += read
                            require(done <= MAX_APK_BYTES) { "apk too big" }
                            output.write(buffer, 0, read)

                            // не чаще 10 раз в секунду, иначе Compose захлёбывается
                            // на каждом прочитанном куске
                            val now = System.currentTimeMillis()
                            if (total > 0 && now - lastReported >= 100) {
                                lastReported = now
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            require(file.length() > 0) { "empty file" }
            onProgress(1f)

            if (info.sha256.isNotBlank()) {
                require(sha256(file) == info.sha256) { "checksum mismatch" }
            }
            file
        }
    }

    /**
     * Сверяет подпись скачанного APK с подписью установленного приложения.
     * Система откажет и сама, но уже в диалоге установщика — после выданного
     * разрешения и потраченного трафика.
     */
    fun signatureMatches(context: Context, file: File): Boolean {
        val result = runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val archive = pm.getPackageArchiveInfo(
                file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
            )
            if (archive == null || archive.packageName != context.packageName) {
                false
            } else {
                @Suppress("DEPRECATION")
                val installed = pm.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                val downloaded = signerDigests(archive.signingInfo)
                val current = signerDigests(installed.signingInfo)
                downloaded.isNotEmpty() && downloaded.intersect(current).isNotEmpty()
            }
        }
        return result.getOrDefault(false)
    }

    private fun signerDigests(info: SigningInfo?): Set<String> {
        if (info == null) return emptySet()
        // history — на случай ротации ключа, apkContentsSigners — если подписантов несколько
        val certs = if (info.hasMultipleSigners()) info.apkContentsSigners
        else info.signingCertificateHistory
        return certs?.mapNotNull { runCatching { sha256(it.toByteArray()) }.getOrNull() }
            ?.toSet().orEmpty()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun releasesPageUrl(): String =
        "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases"

    // Редиректы разбираем сами: instanceFollowRedirects не даёт проверить хост
    // на промежуточных переходах.
    private fun open(
        start: URL,
        configure: (HttpURLConnection) -> Unit = {}
    ): HttpURLConnection {
        var url = start
        var hops = 0
        while (true) {
            require(isAllowed(url)) { "host not allowed: ${url.host}" }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "NotifyGuard")
                configure(this)
            }
            val code = conn.responseCode
            if (code !in 300..399) return conn

            val location = conn.getHeaderField("Location")
            conn.disconnect()
            require(!location.isNullOrBlank()) { "redirect without location" }
            require(++hops <= MAX_REDIRECTS) { "too many redirects" }
            url = URL(url, location)
        }
    }

    private fun readLimited(conn: HttpURLConnection, limit: Int): String {
        val out = java.io.ByteArrayOutputStream(32 * 1024)
        conn.inputStream.use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                require(out.size() + n <= limit) { "response too big" }
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
