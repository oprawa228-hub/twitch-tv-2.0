# Состояние Twitch 2.0 для продолжения

Обновлено 2026-11-08. Работа продолжается по [TASK.txt](TASK.txt) и единственному [PLAN.md](PLAN.md). **Полное поручение не выполнено:** первый signed APK уже работает, но остаются полный объём проверки, измерения и настоящий GitHub → in-app OTA переход.

## Что уже есть

- Самостоятельный проект `D:/Project/Twitch TV 2.0`, пакет `com.oprawa.twitch20`. Старый проект — read-only ориентир, не база кода и не канал публикации.
- AGP 8.13.2, Gradle 8.13, JDK 17, Kotlin/Compose compiler 2.3.10, SDK 36/min SDK 23, Compose BOM 2026.06.01, TV 1.1.0, Media3 1.11.1, **OkHttp 4.12.1**, Coil 3.4.0. Не возвращать 5.5.1: Android AAR требует SDK 37.
- Реальные каталог/live/IRC, auth/Helix, источник видео, плеер/панели, settings previews, bounded history, updater. Наличие кода не означает полной проверки; [TEST_RESULTS.md](TEST_RESULTS.md).
- На signed minified release в отдельном TV emulator состоялись live + chat и Back в каталог. Найденный TLS close crash на UI исправлен и повторно проверен. Одно first-frame наблюдение — 1625 мс после resolve, не benchmark.
- Реальный вход `oprawa` завершён; Helix загрузил отслеживаемый live-канал. Затем пользователь попросил пропустить дальнейшую проверку аккаунта. Не просить повторно логин/пароль/токен и не описывать вход как непроверенный. Refresh/expiry/revoke остаются отдельными непроверенными сценариями.
- Последние JVM: **38/38 PASS**, XML timestamp 13:20:55 UTC. После исправления focus helper последний diagnostic Android run: **2/2 PASS, 26.628 с**. Прежние два failures уже не latest; debug run не заменяет staged release QA или process-restart.

## Артефакты и среда

- Staged signed candidate: [Twitch20-0.1.0-alpha.1.apk](../dist/v0.1.0-alpha.1/Twitch20-0.1.0-alpha.1.apk), versionCode 1, 2 711 016 байт. SHA-256 `f0e467ac08cb3fc985d016783b2d0e8af9ff23d008f76b771e9d802ca26f2a88`; рядом update.json/SHA256SUMS.txt.
- APK **не опубликован**. Metadata apkUrl — будущий адрес. [stage_release.py](../tools/stage_release.py) не загружает файлы и запрещает незаметно заменить отличающийся candidate тем же именем.
- Новый публичный [oprawa228-hub/twitch-tv-2.0](https://github.com/oprawa228-hub/twitch-tv-2.0) создан. APK-релиза/OTA ещё нет; исходники/assets публикует root.
- Эмулятор `Twitch20Test`, `emulator-5560`, API 30/x86, 1280×720/density 213; отдельная `.local/avd`. Root восстанавливает точный staged signed release после diagnostic debug tests. Auth persistence после диагностических переустановок отдельно не подтверждена; это не основание снова запрашивать личный вход сейчас.
- JDK 17 старого проекта используется read-only; новый SDK — `.tools/android-sdk` этого проекта. Локальные пути/SDK/cache не публиковать.
- Public Twitch app зарегистрирован; публичный Client ID в gradle.properties. Client secret в APK не нужен.
- Signing key: `C:/Users/Opraw/.android/signing/twitch20/release.jks`; backup `D:/Private-Backups/Twitch20-signing`. Пароли не выводить/публиковать. Это локальная, не внешняя/offline копия. Certificate SHA-256 есть в TEST_RESULTS/update.json; идентичность не менять.
- GitHub connector используется по доступным capabilities, Chrome — для создания repo/релизов. Личный GitHub credential в приложение не встраивать. Root управляет публикацией.

## Следующие шаги

1. Native QA точного staged signed release: каталог/поиск/канал → live → качество/панели → Back; VOD/clip/resume; режимы чата; параметры/Save/Cancel/long OK/process-restart.
2. Release-like network/lifecycle/длительные нагрузки и измерения с raw evidence. Физического TV нет — отделять emulator evidence от ARM/декодерного QA.
3. Исправить существенные регрессии и выполнить независимый review; записать фактические результаты.
4. README/лицензии/сборка/исходники и два signed test releases. Настоящий download → system installer → новая версия с сохранением данных; сравнить hashes публикации, загрузки и установленного артефакта. Только затем заявлять проверенный OTA.

## Не потерять при продолжении

Панели не владеют ExoPlayer; HTTP cleanup не выполняется main; root Viewing владеет lifecycle, Retry заново разрешает signed URL. Live IRC работает только при foreground live session, чтобы draft включения сразу показывал настоящий чат. Гостевое video недокументировано; OAuth не гарантирует subscriber-only. Root единолично запускает builds/device и управляет ключом/версиями/публикацией. Один владелец файла; отчёты отдельных агентов не заменяют интегрированную проверку.