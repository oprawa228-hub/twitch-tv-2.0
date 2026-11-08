# Проверки Twitch 2.0

Кандидат **beta.5 / code7**: [121 JVM PASS, 8 Android PASS и native-проверки; отдельные ошибки и pending доставка](BACKEND_FINAL.md).

Предыдущий доставленный этап **beta.4 / code6**: [отдельный отчёт с 72 JVM-тестами, шестью Android-сценариями и внешними D-pad проверками](BACKEND_BETA4.md). Ниже сохранены результаты предыдущих выпусков; ограничения первой страницы относятся к beta.3. В beta.4 после входа используется Helix-пагинация.

Состояние на 2026-11-08. Реальные signed APK, diagnostic debug tests и сетевые пробы различаются. **Подтверждена цепочка OTA alpha.2/code2 → beta.2/code4 → финальный опубликованный beta.3/code5.** Доставленный результат — beta; ограничения дополнительного покрытия перечислены отдельно. Raw evidence/XML/PNG/log/trace исключены из Git и доступны только в локальном workspace; указанные пути доказательств не являются публичными downloads.

## Версии и среда

- Основной отдельный TV AVD `Twitch20Test`, `emulator-5560`, API 30/x86, 1280×720, 4 vCPU / 2 GB / SwiftShader. Здесь проверен signed minified alpha.2, versionCode 2. Старый AVD не изменён.
- Второй изолированный `Twitch20Release`, `emulator-5562`, тот же Android TV API 30 образ, 2 vCPU / 1,5 GB. Использован для beta release smoke и network recovery. Это не физический ARM TV.
- **Опубликован [v0.1.0-alpha.2](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-alpha.2)**. [Manifest](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-alpha.2/update.json), [checksum](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-alpha.2/SHA256SUMS.txt); APK SHA-256 `f55fd979bdda85ff77aa678df0f37212a82b20ef1a55c7776956c94882e57ed7`.
- Опубликован [v0.1.0-beta.2](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.2), **versionCode 4**, signed/minified, 2 717 559 байт: [APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.2/Twitch20-0.1.0-beta.2.apk), [manifest](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.2/update.json), [checksum](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.2/SHA256SUMS.txt). SHA-256 `c2ff1871db96b00d6a273c6068a46def6b8657e7cc08ebfb55f0c557d7e5079f`. Root подтвердил native release smoke на 5562 и идентичность скачанных публичных файлов локальным; это ещё не OTA-переход.
- Постоянный signing certificate SHA-256 `f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7`. Ключ/пароли не находятся в исходниках.
- Исходники опубликованы в отдельном [репозитории](https://github.com/oprawa228-hub/twitch-tv-2.0); release-tag source commit — [13545b8](https://github.com/oprawa228-hub/twitch-tv-2.0/commit/13545b8c14942af0529be204d85c91b24a9f3fee), финальные документы публикуются последующим commit. Наличие кода и GitHub release не доказывает обновление установленного приложения.

- Финальный [v0.1.0-beta.3 / code5](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.3): [APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.3/Twitch20-0.1.0-beta.3.apk), 2 717 565 байт; [manifest](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.3/update.json). SHA-256 `9b5fc5fc7b836a60b1bb0971cedd0cf3e5e491d298138a72dfb8e3ee8c1cf844`. Сборка (`evidence/build-beta3.log`, локально) успешна. Изменения после beta.2: CATEGORY_LAUNCHER для кнопки Open системного installer и текст о возможном завершении приложения при разрешении установки; плеер/decoder policy не менялись.

## Подтверждённые Android-сценарии

| Сценарий | Результат и границы | Доказательство |
|---|---|---|
| Настоящий каталог/live/IRC | Изображения и видео Twitch, реальный чат одновременно в signed release. Twitch commercial-break slate не подменяется | Live (`evidence/android/live-playing.png`, локально), XML (`evidence/android/live-playing.xml`, локально) |
| Back и возврат | TLS cleanup crash воспроизведён, исправлен, повтор успешно завершён | Crash (`evidence/back-crash-retraced.log`, локально), после fix (`evidence/android/back-fixed.xml`, локально) |
| Шесть повторов live → Back | Все 6 циклов alpha.2: decoded=true, returned=true. Это шесть открытий, а не 20+ переключений | Результаты (`evidence/repeat-playback.json`, локально), log (`evidence/repeat-playback.log`, локально) |
| Clip в Android | Видеоклип реально декодирован; один кадр не проверяет всю историю/seek/возвраты | Decoded clip (`evidence/android/clip-decoded.png`, локально), XML (`evidence/android/clip-decoded.xml`, локально) |
| Ошибка сети → восстановление | На release beta.1/5562 root отключил Wi-Fi, получил error 2001, вернул сеть, нажал Retry; появился новый кадр за 2121 мс после resolve | Network log (`evidence/beta1-network.log`, локально), восстановленное видео (`evidence/android/network-recovered-video.png`, локально) |
| Один Back из player settings | На beta.2 release одно нажатие закрывает правую панель, HUD остаётся | Панель (`evidence/android/beta2-settings-open.xml`, локально), один Back (`evidence/android/beta2-one-back.xml`, локально) |
| Настоящий OAuth | Пользователь завершил DCF, приложение показало вход `oprawa` | Auth XML (`evidence/android/auth-status.xml`, локально) |
| Личный Helix каталог | После входа загружен отслеживаемый live-канал `TyutcheV_V` | XML (`evidence/android/followed-channels.xml`, локально); PNG снят раньше, на загрузке |
| Общие настройки/панели | Реальный рендер, diagnostic Save/Cancel и вложенный возврат; не все параметры/process-restart | Preview (`evidence/android/settings-preview.png`, локально), panel (`evidence/android/player-settings.png`, локально), instrumentation ниже |

После успешного OAuth/личного каталога пользователь попросил пропустить дальнейшую проверку аккаунта. Это не отменяет успех; refresh/expiry/revocation не проверены. Повторный личный вход не запрашивать.

## Дополнительные представительные native проверки

- Поиск действительно вернул карточку и фокус результата: `evidence/android/search-result.xml`.
- Открыта quality panel с Auto/720p60/480p/360p/160p; после выбора UI показывает **720p60**, playback продолжается: `quality-panel.xml`, `quality-selected.xml` в `evidence/android/`. Это конкретное успешное переключение, не проверка всех ABR условий.
- VOD начал воспроизведение **0:01 / 1:49:12**, controls показывали0:29; после seek — **0:58** с фокусом seek-кнопки. Root выполнил Home/возврат, восстановленный player показывает **1:18 / 1:49:12** и «Пауза»: `vod-start.xml`, `vod-controls.xml`, `vod-seek.xml`, `vod-resume-background.xml`, `vod-background-restored.xml`. XML подтверждают состояния, последовательность действий подтверждена native QA root. Это выполненные основные seek/background/resume сценарии.
- Финальный beta.3 theater включён при реальном playback: `evidence/android/beta3-theater.xml` содержит «Кинотеатр: вкл»/«Пауза»; root визуально проверил каталог с thumbnails/focus и settings preview (`beta3-retained-settings.png`). Overlay live проверен ранее.

## Автоматические проверки и точный build

Последние JVM XML **2026-11-08 13:51:32 UTC**: **42 tests, 0 failures, 0 errors**. Наборы: Auth 10, Chat 8, History 4, Parsing 5, PlaybackPolicy 4, UpdateOperationGate 2, UpdatePolicy 9. XML (`app/build/test-results/testDebugUnitTest`, локально), полная сборка beta.2 (`evidence/build-beta2.log`, локально). Они проверяют локальную логику, не всю внешнюю интеграцию.

Последний diagnostic Android run: **5/5 PASS, 60.562 с**, instrumentation-beta2.log (`evidence/instrumentation-beta2.log`, локально). Реальный D-pad, [TvNavigationTest.kt](../app/src/androidTest/java/com/oprawa/twitch20/TvNavigationTest.kt):

1. Каталог/категории/настройки/Back.
2. Draft Cancel и explicit Save с повторным открытием.
3. Platform long OK открывает канал, Back восстанавливает карточку/строку.
4. Вложенные обновления сохраняют draft до отмены общих настроек.
5. Первый реальный live → player settings → один Back закрывает панель, сохраняет HUD/воспроизведение.

Это **diagnostic debug**, не сам подписанный candidate. Старые failures focus helper и результаты 2/4 tests — история, не latest. Финальная сборка beta.2 (`evidence/build-beta2-final.log`, локально) после добавления decoder logging успешна, но она **не является повтором 42 unit и 5 instrumentation**. Signed native smoke проверен отдельно root.

## Нагрузка и производительность

Подробные измерения и ограничения: [PERFORMANCE.md](PERFORMANCE.md).

- Alpha.2 live+chat наблюдался **9 мин 55,610 с**. PSS **85912 → 92517 → 94340 KB**; Activities 1, Views 9; OpenSSL sockets 8 → 6 → 3. Это не доказательство отсутствия утечки.
- Stable-live Perfetto **19,400894 с**: main doFrame p95 **5,225 мс**, максимум **9,102 мс**; без FrameTimeline/jank% или video FPS заявления.
- Scroll Perfetto **19,9190812 с**, только 14 delivery events / 7 timestamps в конце записи. Отдельный gfxinfo за более широкое окно: **131 кадр, 0 janky, p95 13 мс**. Не все двадцать shell-команд попали в trace; input timing не input-to-photon.
- В alpha.2 dropped callback не журналировался. В beta.2 за 4 мин 22,952 с зафиксированы rendered=14382, dropped=9, skipped_output=50; late-drop доля 0,063% без skipped. В начале Auto/160p, последующий ABR не записан. Terminal log (`evidence/beta2-decoder-counters.log`, локально); не смешивать с alpha.2 памятью.

## Исправленные проблемы

- P1: TLS cancel/evict при release на UI → NetworkOnMainThreadException. Весь HTTP cleanup вынесен в клиентский executor с shutdown; StrictMode сохранён.
- P2: первый Back только отдавал фокус корневому Row. Общий handleBack и перехват KEYCODE_BACK до Compose focus navigation; подтверждён beta.2 release и новым тестом.
- Retry заново разрешает signed URL; IRC RECONNECT в зрительском тексте не является server command; TLS upgrade timeout ограничен.
- Clock не перекрывает top-right overlay chat; platform long OK и возврат к карточке исправлены. Updater операции защищены от stale результатов/cancellation races.
- OkHttp **4.12.1** выбран после реального SDK 37 metadata failure версии 5.5.1.

## Реальный OTA и сохранение данных

**PASS alpha.2/code2 → beta.2/code4 на primary5560**, через собственный updater и Android PackageInstaller:

- Проверка версии → загрузка; отмена при ограниченной сети; повторная проверка/загрузка после восстановления сети; SHA/certificate/package/version validation; системное разрешение unknown sources; системная установка. Отмена (`evidence/android/ota-cancelled.xml`, локально), готовность (`evidence/android/ota-downloaded.xml`, локально), permission (`evidence/android/ota-system-permission.xml`, локально), installer (`evidence/android/ota-system-installer.xml`, локально), установлено (`evidence/android/ota-installed.xml`, локально).
- Android завершил процесс при первом изменении REQUEST_INSTALL_PACKAGES (14:01:12 UTC); после повторного запуска app check/download/installer путь завершился. Это системное изменение permission, а не повторение TLS crash. В beta.3 пояснён способ продолжения.
- Root выгрузил реально установленный base APK в `.local/installed-beta2.apk`: SHA-256 `c2ff1871db96b00d6a273c6068a46def6b8657e7cc08ebfb55f0c557d7e5079f`, ровно публичный артефакт. Dumpsys: versionCode4, installer `com.google.android.packageinstaller`.
- Настройка текста **18sp** сохранилась: settings-retained (`evidence/android/ota-settings-retained.xml`, локально).
- Аккаунт `oprawa` и личный каталог сохранились: following-retained (`evidence/android/ota-following-retained.xml`, локально).
- История VOD сохранилась: запись возобновилась **1:28 / 1:49:12** и реально продолжилась до **1:46**: resumed (`evidence/android/ota-vod-resumed.xml`, локально), playing (`evidence/android/ota-vod-retained-playing.xml`, локально). Это подтверждает VOD playback и сохранение позиции через OTA, но не каждый seek/quality/background сценарий.

**PASS beta.2/code4 → beta.3/code5 через тот же app/system installer:** проверенная загрузка (`evidence/android/beta3-ready.xml`, локально), installer обновляет существующее приложение (`evidence/android/beta3-installer.xml`, локально), App installed / Open (`evidence/android/beta3-installing.xml`, локально). Одно OK на **Open** запустило каталог: open-from-installer (`evidence/android/beta3-open-from-installer.xml`, локально); аккаунт `oprawa` сохранился. retained-settings (`evidence/android/beta3-retained-settings.xml`, локально) показывает **18sp** и **0.1.0-beta.3**.

Root выгрузил installed base APK; три независимо доступных файла `.local/installed-beta3.apk`, `.local/public-beta3.apk`, `dist/v0.1.0-beta.3/Twitch20-0.1.0-beta.3.apk` локально сверены: **2 717 565 байт**, одинаковый SHA-256 `9b5fc5fc7b836a60b1bb0971cedd0cf3e5e491d298138a72dfb8e3ee8c1cf844`. Dumpsys: **versionCode5, minSdk23, targetSdk36**, installer `com.google.android.packageinstaller`. **OTA 2→4→5 выполнена.** Позиция VOD отдельно проверена на 2→4; нового history/resume теста после 4→5 не заявляем.

Повторный полный debug test run после двух небольших beta.3 изменений не проводился; прежние 42 unit/5 Android PASS относятся к своему прогону. Финальная beta.3 имеет отдельные successful build, native installer/Open/retention проверки.

## Публикация и оставшееся

Alpha.2, beta.2 и beta.3 опубликованы. Оба перехода 2→4 и 4→5 подтверждены через приложение/системный installer, installed hash и сохранённые данные в указанном объёме.

Ограничения дополнительного покрытия: физический ARM TV/аппаратный decoder/1080p60 и другие API; многочасовое видео, длительная busy-chat/reconnect нагрузка,20+ смен каналов; auth expiry/refresh/revoke (дальнейшая личная проверка пропущена по просьбе пользователя). Основные VOD seek/Home/resume/quality/search/theater выполнены в представительных сценариях; exhaustive matrix не заявляется. [Сетевые пробы](research-twitch.md) не подменяют эти Android-проверки. Общий каталог ограничен 30 live, категория — 40, VOD/clips — 30; бесконечная пагинация не реализована. Доставлен beta-объём с указанными ограничениями; stable/универсальная совместимость не заявлены.




Финальная локальная сверка: `evidence/final-verification.json` — три одинаковых beta.3 APK hash и 42 tests/0 failures/errors/skipped. `evidence/beta3-signature-verification.log` — apksigner exit0, v1/v2 true, один signer с указанным сертификатом. Это проверка итоговых файлов, не новый запуск полного unit suite.
