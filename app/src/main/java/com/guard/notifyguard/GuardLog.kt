package com.guard.notifyguard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val formatter = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
}

private fun fmt(ts: Long): String = formatter.get()!!.format(Date(ts))

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
 * Локальный журнал. Записи складываются в один фоновый поток:
 * колбэки сервиса уведомлений приходят в главный поток, и запись
 * JSON прямо в них подтормаживала бы интерфейс системы.
 */
object GuardLog {

    private const val FILE = "notifyguard_log"
    private const val KEY_NOTIF = "entries"
    private const val KEY_CALLS = "calls"
    private const val LIMIT = 100

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "guard-log").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun addNotification(context: Context, entry: LogEntry) {
        val app = context.applicationContext
        io.execute {
            runCatching {
                val store = sp(app)
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
        }
    }

    fun addCall(context: Context, entry: CallEntry) {
        val app = context.applicationContext
        io.execute {
            runCatching {
                val store = sp(app)
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
        }
    }

    fun readNotifications(context: Context): List<LogEntry> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_NOTIF, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LogEntry(o.optString("p"), o.optString("t"), o.optString("r"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    fun readCalls(context: Context): List<CallEntry> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_CALLS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallEntry(o.optString("n"), o.optBoolean("s"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    fun clearNotifications(context: Context) {
        sp(context).edit().remove(KEY_NOTIF).apply()
    }

    fun clearCalls(context: Context) {
        sp(context).edit().remove(KEY_CALLS).apply()
    }
}
