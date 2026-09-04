package com.guard.notifyguard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Локальный журнал: последние LIMIT записей по уведомлениям и столько же по звонкам.
 *
 * Списки держим в памяти под общим замком, на диск пишем из отдельного потока.
 * Иначе запись из слушателя и очистка с экрана лезут в SharedPreferences
 * из разных потоков и порядок операций перестаёт быть очевидным.
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

    private val lock = Any()
    private var notifList: MutableList<LogEntry>? = null
    private var callList: MutableList<CallEntry>? = null

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun addNotification(context: Context, entry: LogEntry) {
        val app = context.applicationContext
        val json = synchronized(lock) {
            val list = notifications(app)
            list.add(0, entry)
            while (list.size > LIMIT) list.removeAt(list.size - 1)
            notificationsJson(list)
        }
        save(app, KEY_NOTIF, json)
    }

    fun addCall(context: Context, entry: CallEntry) {
        val app = context.applicationContext
        val json = synchronized(lock) {
            val list = calls(app)
            list.add(0, entry)
            while (list.size > LIMIT) list.removeAt(list.size - 1)
            callsJson(list)
        }
        save(app, KEY_CALLS, json)
    }

    fun readNotifications(context: Context): List<LogEntry> =
        synchronized(lock) { notifications(context).toList() }

    fun readCalls(context: Context): List<CallEntry> =
        synchronized(lock) { calls(context).toList() }

    fun clearNotifications(context: Context) {
        val app = context.applicationContext
        synchronized(lock) { notifications(app).clear() }
        save(app, KEY_NOTIF, "[]")
    }

    fun clearCalls(context: Context) {
        val app = context.applicationContext
        synchronized(lock) { calls(app).clear() }
        save(app, KEY_CALLS, "[]")
    }

    private fun save(context: Context, key: String, json: String) {
        io.execute {
            runCatching { sp(context).edit().putString(key, json).apply() }
        }
    }

    // Читаем с диска один раз за жизнь процесса, дальше работаем со списком в памяти.
    private fun notifications(context: Context): MutableList<LogEntry> =
        notifList ?: loadNotifications(context).also { notifList = it }

    private fun calls(context: Context): MutableList<CallEntry> =
        callList ?: loadCalls(context).also { callList = it }

    private fun loadNotifications(context: Context): MutableList<LogEntry> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_NOTIF, "[]"))
        val out = ArrayList<LogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(LogEntry(o.optString("p"), o.optString("t"), o.optString("r"), o.optLong("ts")))
        }
        out
    }.getOrDefault(ArrayList())

    private fun loadCalls(context: Context): MutableList<CallEntry> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_CALLS, "[]"))
        val out = ArrayList<CallEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(CallEntry(o.optString("n"), o.optBoolean("s"), o.optLong("ts")))
        }
        out
    }.getOrDefault(ArrayList())

    private fun notificationsJson(list: List<LogEntry>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("p", e.pkg)
                    .put("t", e.title)
                    .put("r", e.reason)
                    .put("ts", e.time)
            )
        }
        return arr.toString()
    }

    private fun callsJson(list: List<CallEntry>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("n", e.number)
                    .put("s", e.silenced)
                    .put("ts", e.time)
            )
        }
        return arr.toString()
    }
}
