package com.guard.notifyguard

import android.telecom.Call
import android.telecom.CallScreeningService

// Звонок с номера вне контактов проходит беззвучно.
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
            .setSilenceCall(true)
            .setRejectCall(false)
            .setDisallowCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, silenced)

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
