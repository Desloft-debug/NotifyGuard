package com.guard.notifyguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.net.URLEncoder

// Обратная связь через GitHub Issues.
object Feedback {

    private const val MAX_BODY = 5000

    fun issueUrl(title: String, body: String): String {
        val base = "https://github.com/${BuildConfig.GITHUB_OWNER}/" +
            "${BuildConfig.GITHUB_REPO}/issues/new"
        val t = enc(title.take(120))
        val b = enc(body.take(MAX_BODY))
        return "$base?title=$t&body=$b"
    }

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
