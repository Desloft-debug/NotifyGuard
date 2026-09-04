package com.guard.notifyguard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Записи журнала. Отдельно от GuardLog: их разбирает ещё и NumberLookup,
// а Android им не нужен вовсе.

// SimpleDateFormat не потокобезопасен, а форматируем и из UI, и из слушателя.
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
