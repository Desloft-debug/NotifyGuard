package com.guard.notifyguard

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Звонок с номера, которого нет в контактах, проходит беззвучно:
 * без рингтона и без вибрации. Звонок не сбрасывается и остаётся
 * в журнале вызовов — пропущенный номер видно как обычно.
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
        if (ContactsRepo.isKnown(this, number)) {
            respondToCall(callDetails, allow)
            return
        }

        val silenced = CallResponse.Builder()
            .setSilenceCall(true)      // без звука и вибрации
            .setRejectCall(false)      // звонок не сбрасываем
            .setDisallowCall(false)
            .setSkipCallLog(false)     // остаётся в журнале вызовов
            .setSkipNotification(false) // пропущенный виден в шторке
            .build()
        respondToCall(callDetails, silenced)
    }
}
