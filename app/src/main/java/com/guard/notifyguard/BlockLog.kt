package com.guard.notifyguard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val pkg: String,
    val title: String,
    val reason: String,
    val time: Long
) {
    fun timeText(): String =
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(time))
}

/** Последние снятые уведомления — чтобы было видно, что именно скрыто. */
object BlockLog {

    private const val FILE = "notifyguard_log"
    private const val KEY = "entries"
    private const val LIMIT = 100

    @Synchronized
    fun add(context: Context, entry: LogEntry) {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(KEY, "[]"))
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("p", entry.pkg)
                .put("t", entry.title)
                .put("r", entry.reason)
                .put("ts", entry.time)
        )
        for (i in 0 until minOf(arr.length(), LIMIT - 1)) out.put(arr.get(i))
        sp.edit().putString(KEY, out.toString()).apply()
    }

    fun read(context: Context): List<LogEntry> {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(KEY, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LogEntry(
                pkg = o.optString("p"),
                title = o.optString("t"),
                reason = o.optString("r"),
                time = o.optLong("ts")
            )
        }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
