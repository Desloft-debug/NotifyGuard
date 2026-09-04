package com.guard.notifyguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object KeepAlive {

    private const val WORK = "guard-watchdog"

    fun isBatteryUnrestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Экраны автозапуска у вендоров: системного API для проверки нет,
    // поэтому просто открываем нужный экран, если он есть на устройстве.
    private val VENDOR_SCREENS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
    )

    private fun vendorIntent(context: Context): Intent? {
        val pm = context.packageManager
        for ((pkg, cls) in VENDOR_SCREENS) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }

    fun hasVendorScreen(context: Context): Boolean = vendorIntent(context) != null

    fun openVendorScreen(context: Context) {
        val intent = vendorIntent(context) ?: Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // 15 минут - минимум, который разрешает WorkManager. Чаще всё равно не дадут.
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }

    class Watchdog(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val ctx = applicationContext
            GuardNotificationListener.ensureBound(ctx)
            if (Prefs(ctx).keepAlive) GuardForegroundService.start(ctx)
            return Result.success()
        }
    }
}
