# Сервисы вызываются системой по имени из манифеста
-keep class com.guard.notifyguard.GuardNotificationListener { *; }
-keep class com.guard.notifyguard.GuardCallScreeningService { *; }

# org.json используется для журнала и разбора ответа GitHub
-dontwarn org.json.**

# Compose и Kotlin метаданные
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
