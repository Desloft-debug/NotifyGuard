package com.guard.notifyguard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ts: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ts))

data class LogEntry(
    val pkg: String,
    val title: String,
    val reason: String,
    val time: Long
) {
    fun timeText(): String = fmt(time)
}

data class CallEntry(
    val number: String,
    val silenced: Boolean,
    val time: Long
) {
    fun timeText(): String = fmt(time)
}

/**
 * Локальный журнал. Ничего не покидает устройство.
 * Хранится ограниченное число записей, старые вытесняются.
 */
object GuardLog {

    private const val FILE = "notifyguard_log"
    private const val KEY_NOTIF = "entries"
    private const val KEY_CALLS = "calls"
    private const val LIMIT = 100

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun addNotification(context: Context, entry: LogEntry) {
        val store = sp(context)
        val arr = JSONArray(store.getString(KEY_NOTIF, "[]"))
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("p", entry.pkg)
                .put("t", entry.title)
                .put("r", entry.reason)
                .put("ts", entry.time)
        )
        for (i in 0 until minOf(arr.length(), LIMIT - 1)) out.put(arr.get(i))
        store.edit().putString(KEY_NOTIF, out.toString()).apply()
    }

    fun readNotifications(context: Context): List<LogEntry> {
        val arr = JSONArray(sp(context).getString(KEY_NOTIF, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LogEntry(o.optString("p"), o.optString("t"), o.optString("r"), o.optLong("ts"))
        }
    }

    @Synchronized
    fun addCall(context: Context, entry: CallEntry) {
        val store = sp(context)
        val arr = JSONArray(store.getString(KEY_CALLS, "[]"))
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("n", entry.number)
                .put("s", entry.silenced)
                .put("ts", entry.time)
        )
        for (i in 0 until minOf(arr.length(), LIMIT - 1)) out.put(arr.get(i))
        store.edit().putString(KEY_CALLS, out.toString()).apply()
    }

    fun readCalls(context: Context): List<CallEntry> {
        val arr = JSONArray(sp(context).getString(KEY_CALLS, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallEntry(o.optString("n"), o.optBoolean("s"), o.optLong("ts"))
        }
    }

    fun clearNotifications(context: Context) {
        sp(context).edit().remove(KEY_NOTIF).apply()
    }

    fun clearCalls(context: Context) {
        sp(context).edit().remove(KEY_CALLS).apply()
    }
}
