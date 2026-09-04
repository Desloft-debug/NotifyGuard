package com.guard.notifyguard

import android.telecom.Call
import android.telecom.CallScreeningService
import java.util.concurrent.Executors

/**
 * Звонок с номера вне контактов проходит беззвучно.
 *
 * onScreenCall вызывается в главном потоке, а системе нужен ответ за считаные секунды.
 * Раньше прямо здесь делался contentResolver.query к провайдеру контактов: на телефоне
 * с большой адресной книгой это блокировка главного потока в момент входящего звонка.
 * Теперь поиск уходит в отдельный поток, а respondToCall вызывается оттуда —
 * API это допускает, ответ по определению асинхронный.
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
            // Если что-то пойдёт не так, звонок должен пройти обычным образом:
            // не приглушить лишний раз хуже, чем заглушить нужный.
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
