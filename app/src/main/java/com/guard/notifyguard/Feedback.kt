package com.guard.notifyguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.net.URLEncoder

/**
 * Обратная связь через GitHub Issues.
 *
 * Обращение не отправляется напрямую из приложения намеренно: для записи
 * в репозиторий нужен токен, а любой токен, зашитый в APK, извлекается
 * из него за пару минут — и вместе с ним чужой доступ на запись.
 * Поэтому приложение открывает форму создания issue с уже заполненными
 * полями, а отправляет её сам пользователь под своей учётной записью.
 * Так же видно, что именно уходит.
 */
object Feedback {

    private const val MAX_BODY = 5000

    fun issueUrl(title: String, body: String): String {
        val base = "https://github.com/${BuildConfig.GITHUB_OWNER}/" +
            "${BuildConfig.GITHUB_REPO}/issues/new"
        val t = enc(title.take(120))
        val b = enc(body.take(MAX_BODY))
        return "$base?title=$t&body=$b"
    }

    /** Технические данные — прикладываются только по желанию пользователя. */
    fun diagnostics(): String = buildString {
        appendLine("---")
        appendLine("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    fun open(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    fun copy(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("NotifyGuard", text))
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
