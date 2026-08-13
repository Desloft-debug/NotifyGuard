package com.guard.notifyguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

// Проверка обновлений через публичный API GitHub.
object Updater {

    private const val API =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun shouldCheck(prefs: Prefs): Boolean =
        prefs.updateCheckEnabled &&
            System.currentTimeMillis() - prefs.lastUpdateCheck > DAY_MS

    suspend fun fetchLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NotifyGuard")
            }
            val body = conn.use { it.inputStream.bufferedReader().readText() }
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
            require(tag.isNotBlank() && url.isNotBlank()) { "В релизе нет APK" }
            ReleaseInfo(tag, tag.removePrefix("v"), notes, url, size)
        }
    }

    fun isNewer(current: String, remote: String): Boolean {
        val a = current.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val b = remote.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(dir, "NotifyGuard-${info.tag}.apk")

            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NotifyGuard")
            }
            conn.use { c ->
                val total = if (info.sizeBytes > 0) info.sizeBytes else c.contentLength.toLong()
                c.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            require(file.length() > 0) { "Пустой файл" }
            file
        }
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

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
