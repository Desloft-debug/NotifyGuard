package com.guard.notifyguard

import android.telecom.Call
import android.telecom.CallScreeningService
import java.util.concurrent.Executors

/**
 * Звонок с номера вне контактов проходит беззвучно.
 *
 * onScreenCall приходит в главный поток, а ответить надо за считаные секунды.
 * Поиск по контактам — это запрос к провайдеру, на большой адресной книге он
 * не мгновенный, поэтому уводим его в свой поток. respondToCall оттуда звать можно.
 */
class GuardCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val allow = CallResponse.Builder().build()

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, allow)
            return
        }

        val prefs = Prefs(this)
        if (!prefs.silenceUnknownCalls) {
            respondToCall(callDetails, allow)
            return
        }

        val number = callDetails.handle?.schemeSpecificPart

        io.execute {
            // при любой ошибке считаем номер знакомым: лучше не приглушить,
            // чем заглушить нужный звонок
            val known = runCatching { ContactsRepo.isKnown(this, number) }.getOrDefault(true)

            if (known) {
                runCatching { respondToCall(callDetails, allow) }
                return@execute
            }

            val silenced = CallResponse.Builder()
                .setSilenceCall(true)
                .setRejectCall(false)
                .setDisallowCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            runCatching { respondToCall(callDetails, silenced) }

            GuardLog.addCall(
                this,
                CallEntry(
                    number = number ?: "",
                    silenced = true,
                    time = System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        private val io = Executors.newSingleThreadExecutor { r ->
            Thread(r, "guard-screening").apply { isDaemon = true }
        }
    }
}
