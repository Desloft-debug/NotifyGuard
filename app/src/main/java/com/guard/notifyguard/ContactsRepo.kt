package com.guard.notifyguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactsRepo {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Есть ли номер в контактах.
     * Если доступа к контактам нет, считаем номер знакомым —
     * лучше пропустить звонок, чем заглушить всех подряд.
     */
    fun isKnown(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        if (!hasPermission(context)) return true

        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(true)
    }
}
