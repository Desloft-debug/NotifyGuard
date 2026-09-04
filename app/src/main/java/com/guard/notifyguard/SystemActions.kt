package com.guard.notifyguard

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Всё, что дёргает систему: экраны настроек, роли, имена приложений.

/** Выданные разрешения. Пересчитываются на onResume — из настроек можно вернуться с чем угодно. */
internal data class AccessState(
    val listener: Boolean,
    val screening: Boolean,
    val contacts: Boolean
) {
    val allGranted: Boolean get() = listener && screening && contacts
}

@Composable
internal fun rememberAccess(refresh: Int): AccessState {
    val context = LocalContext.current
    return remember(refresh) {
        AccessState(
            listener = GuardNotificationListener.isPermitted(context),
            screening = isCallScreener(context),
            contacts = ContactsRepo.hasPermission(context)
        )
    }
}

/**
 * На части прошивок нужного экрана настроек просто нет и startActivity бросает.
 * Тогда открываем карточку приложения. false — не открылось вообще ничего.
 */
internal fun openSettings(
    context: Context,
    intent: Intent,
    fallbackToAppDetails: Boolean = true
): Boolean {
    if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return true
    if (!fallbackToAppDetails) return false
    val details = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(details); true }.getOrDefault(false)
}

internal fun openAppNotificationSettings(context: Context, pkg: String) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}

internal fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
}.getOrDefault(pkg)

internal fun isCallScreener(context: Context): Boolean {
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
        rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

internal fun requestScreeningRole(context: Context): Intent? {
    val rm = context.getSystemService(RoleManager::class.java) ?: return null
    if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
    return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
