# Проверки Twitch 2.0 — 2026-11-08

## Выполнено
- Сетевые пробы на настоящем Twitch: каталог, эфир, запись, клип (часть видеоданных), чтение IRC. См. research-twitch.md и tools/probe_twitch.py. Это не проверка декодирования APK.
- 27 модульных проверок прошли: API parsing5, auth5, IRC6, playback4, updater7. Затем добавлены проверки bounded history и IRC server-command parsing; требуют следующего общего прогона.
- Отдельный эмулятор Twitch20Test, emulator-5560, API30/x86,1280×720,density213. Новый data directory `.local/avd`; прежний OprawaTVTest не изменён.
- Gradle dependencies compatibility checked by actual build. OkHttp5.5.1 требует SDK37, выбран4.12.1 с актуальным security backport; platform research updated.

## Пока не подтверждено
Первый подписанный APK ещё проходит сборку/интеграцию. Реальный вход пользователя, Android-видео, управление, нагрузка, физический TV, публичный релиз и OTA-переход не считаются проверенными до появления соответствующих записей.

## Известные исправленные ошибки подготовки
- Kotlin2.3 требует compilerOptions DSL вместо старого kotlinOptions.jvmTarget.
- Twitch streams(first:40) отклоняется; проверен лимит30 для общего каталога.
- IRC websocket требует отдельного кадра на каждую login-команду.
- GitHub plug-in не предоставляет create-repository/release-upload; новый репозиторий создан в браузере, дальнейшая публикация использует разрешённые доступные средства.
- Signing key initially inaccessible to Java because recursive ACL operation removed file grants; explicit current-user file grants restored, no identity replaced.
