# Подача на площадки

## Перед подачей

- [ ] В `app/build.gradle.kts` подставлены правильные `GITHUB_OWNER` и `GITHUB_REPO`
- [ ] В репозитории есть `LICENSE`
- [ ] У репозитория заполнено описание (About) и указан язык
- [ ] Есть релиз с тегом `vX.X` и прикреплённым подписанным APK
- [ ] APK подписан релизным ключом, не отладочным
- [ ] Добавлены скриншоты (см. ниже) — без них заявку не примут

## Скриншоты

Нужно 3–5 штук в `fastlane/metadata/android/ru/images/phoneScreenshots/` и то же для `en-US`.
Только PNG или JPG, имена по порядку: `1.png`, `2.png`, `3.png`.

Что снять: главный экран с включённым фильтром, журнал скрытых уведомлений,
карточку номера, словарь, инструкцию.

Снять с телефона: кнопка питания вместе с уменьшением громкости.
С эмулятора Android Studio: значок фотоаппарата на панели справа.

Перед съёмкой стоит включить тёмную тему на паре кадров — так листинг выглядит живее.

## IzzyOnDroid

Порог входа ниже, чем у основного F-Droid: собирать приложение у себя они не будут,
берут APK из ваших релизов.

1. Открыть issue в трекере: https://gitlab.com/IzzyOnDroid/repo/-/issues
2. Заголовок: `[App Request] Quiet Notifications`
3. В теле указать:

```
Repo: https://github.com/OWNER/NotifyGuard
License: MIT
Fastlane metadata: fastlane/metadata/android/ (en-US, ru)
Releases: signed APK attached to each tag
AntiFeatures: none
Summary: Hides ad notifications and silences calls from numbers outside your contacts.

The app requests INTERNET only for the opt-in update check and the shared word
list; both are off or user-triggered. The self-updater is strictly opt-in and
disabled by default. dependenciesInfo is disabled in the build.
```

Проверяют вручную, ответ обычно за несколько дней.

## F-Droid (основной репозиторий)

Сложнее: собирают из исходников на своих серверах, очередь может занять месяцы.
Имеет смысл подавать после того, как приложение поживёт в IzzyOnDroid.

1. Issue в https://gitlab.com/fdroid/rfp с шаблоном RFP
2. Сборка не должна требовать ничего, кроме публичных репозиториев Maven
3. Полезно заранее проверить локально: `fdroid build` в песочнице

Учтите: в основном F-Droid встроенный автообновлятор помечается как AntiFeature
`UpstreamNonFree` или требует отключения. Проще собрать для них вариант без
модуля обновлений либо оставить его выключенным по умолчанию — что уже сделано.

## Obtainium

Ничего делать не нужно: пользователь добавляет ссылку на репозиторий, приложение
само находит релизы. Достаточно упоминания в README — уже добавлено.

## Google Play (необязательно)

25 долларов единоразово, верификация личности, 12 тестировщиков на 14 дней
непрерывного закрытого тестирования для личных аккаунтов.
`QUERY_ALL_PACKAGES` из приложения убран, так что главное препятствие снято,
но потребуется политика конфиденциальности и объяснение доступа к уведомлениям.
