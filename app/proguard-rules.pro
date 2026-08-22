-keep class com.guard.notifyguard.GuardNotificationListener { *; }
-keep class com.guard.notifyguard.GuardCallScreeningService { *; }
-keep class com.guard.notifyguard.GuardForegroundService { *; }
-keep class com.guard.notifyguard.BootReceiver { *; }
-keep class com.guard.notifyguard.KeepAlive$Watchdog { *; }

-dontwarn org.json.**

-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
