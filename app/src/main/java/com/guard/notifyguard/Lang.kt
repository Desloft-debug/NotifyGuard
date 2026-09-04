package com.guard.notifyguard

// Язык интерфейса. Лежит отдельно от Strings: его знают и Prefs, и журнал,
// а тащить ради этого в тесты весь Compose незачем.
enum class Lang { SYSTEM, RU, EN }
