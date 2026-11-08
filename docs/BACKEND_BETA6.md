# Внутренняя часть — beta.6

2026-11-08. **Beta.6/code8 опубликован, OTA7→8 проверен с настройками и VOD history.** Дизайн отложен пользователем. Основные изменения auth/catalog/chat/playback и их границы перечислены в [отчёте beta.5](BACKEND_FINAL.md); этот выпуск добавляет исправление повторного запуска плеера.

## Причина и исправление

После настоящего OTA6→7 дополнительная проверка воспроизвела crash: запись на паузе → Home → явный запуск MainActivity → открытие нового видео. Стандартный launch mode создавал второй Activity; оба PlaybackSession использовали пустой MediaSession ID. Android завершал процесс с `Session ID must be unique. ID=`. Сохранённые настройки и позиция записи при OTA не терялись.

MainActivity теперь использует singleTask, чтобы обычный повторный запуск возвращал существующий экран. Каждый PlaybackSession получает уникальный ID на весь срок жизни владельца; release освобождает прежние ресурсы. Источники, буферы, выбор качества и decoder configuration не изменены. Обоснование: [Android launch modes](https://developer.android.com/guide/topics/manifest/activity-element#lmode), [Media3 session identity](https://developer.android.com/media/media3/session/control-playback#unique-session-id).

Regression `MediaSessionOwnershipTest` воспроизвёл ошибку на настоящем Android: **1 test / 1 failure**, `evidence/beta6-session-red7.log`. Ранний `beta6-session-red.log` непригоден: установка debug была отклонена из-за version downgrade; он не считается RED.

## Точный APK и проверки

- Package `com.oprawa.twitch20`, versionCode **8**, `0.1.0-beta.6`, signed/minified release, **2 733 993 байта**.
- SHA-256 **459ff94be9cb8d356620c3da0e0020d17ca66c2b519c80c22c18b5e5c668ea6c**.
- Постоянный сертификат **f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7**; `evidence/beta6-candidate.json`, `beta6-signature.log`, `beta6-build.log`.
- **121 JVM PASS**, без skipped/failures. **9 Android PASS суммарно на двух AVD:**8/59.072s на guest `Twitch20Release`/5562 (пять D-pad scenarios, два Media3 intent и regression двух владельцев);1/22.29s на primary5560 — каталог Helix за пределами30 эфиров и возврат к карточке с сохранённым аккаунтом. `beta6-instrumentation-secondary.log`, `beta6-instrumentation-primary-auth.log`. Это разделённый запуск suite, не девять тестов на одном AVD. Диагностический decoder comparator не включён в9PASS.
- Exact release на5562: paused VOD→Home→explicit launch сохранил тот же Activity и позицию143s; playing VOD→Home→launch сохранил Activity, намерение воспроизведения и движение позиции. `tools/qa_activity_relaunch.py`, `evidence/beta6-release-relaunch.log` — **PASS обоих сценариев**.
- Exact release clip1080p60→720p60: позиция0:04 и пауза сохранены (`beta6-clip-quality.log`, `beta6-clip-720-paused.log`). Resume продолжил видео; настоящий конец0:29/0:29 наблюдён, повторное открытие того же `WWW ANAR` началось с0:00 (`beta6-clip-ended`, `beta6-clip-reopened-initial`). В первом helper неверно ожидался заголовок «Записи» вместо «Клипы · buster»; весь helper не считается PASS, повторное открытие проверено отдельно по настоящему экрану.

## Доставка

Beta.5/code7 уже опубликован, OTA6→7 прошёл через меню и системный installer, public/feed/installed APK совпали с cb26…; настройки theatre/16/65%/clock/animations и VOD1:23 сохранились, запись затем играла до1:42. `evidence/beta5-ota-installed.json`, `beta5-ota-settings-retained`, `beta5-ota-history-retained`. Это отдельный предыдущий переход.

Опубликован [beta.6](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.6); [APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.6/Twitch20-0.1.0-beta.6.apk), update.json иSHA256SUMS побайтно совпали со staged артефактами (`beta6-public-assets.json`). Обычный rawURL тестовогоfeed отдаётcode8/459ff94… (`beta6-public-feed.json`). Source/tag [d7180982a719bda440b745bbca1d917bfc6329e7](https://github.com/oprawa228-hub/twitch-tv-2.0/commit/d7180982a719bda440b745bbca1d917bfc6329e7); итоговые документы добавляются следующим commit. Прежние APK/теги не заменены.

**OTA7→8 PASS** на5562/API30x86: меню приложения → check → download → SHA/certificate check → системныйINSTALL → OPEN. Installer=`com.google.android.packageinstaller`, code8, installedSHA459ff94…,2 733 993байта (`beta6-ota-installed.json`). Предыдущая release7/cb26… была восстановлена после временных debug-проверок без удаления данных; её точный hash проверен до OTA (`beta6-ota-before.json`). Это не ручная установка8, выданная за OTA.

После обновления совпали все шесть наблюдаемых settings: чатshown,theatre,text16,background65%,clock/animationson. Та же записьbuster «СВОЯ ИГРА…» открылась с **2:59**, как перед обновлением; `beta6-ota-settings-verified.json`, `beta6-ota-history-before/after`. Primary5560 вручную обновлён до exactrelease8 с сохранениемoprawa (`beta6-primary-account-retained`); этот отдельный результат — install compatibility, не accountOTA7→8.

После настоящего installerOPEN дополнительно повторён исходный проблемный путь: pausedVOD→Home→explicitstart иplayingVOD→Home→explicitstart. Оба**PASS**, тотжеActivity; pausedposition224s неизменна, послеResume позиция движется (`evidence/beta6-ota-relaunch.log`). Тестовые плееры затем закрыты с возвратом в каталог.

## Отдельное ограничение декодирования

Три прогона KSPKSP на software AVC API30/x86 получили4003: предварительный beta.5 —720p60/319.161s и1425.246s; опубликованный beta.5 —480p/44.327s. Это не crash идентификатора сессии. UUID/singleTask не объявляются исправлением codec failure.

**Независимый минимальный Media3 comparator воспроизвёл4003** на secondary5562 через162.089s отprepare /160.712s окнаrenderer:4796rendered/61dropped/7skipped; `OMX.google.h264.decoder`, AVC852×480/30fps. SoftAVCDecFatal0xffffffff/ACodec0x80001019 предшествует Media3 error. Обычный ExoPlayer+OkHttp+SurfaceView, без PlaybackSession/MediaSession/ContentFrame, чата и панелей, фиксированный480p с первогоprepare. Счётчики — отдельный диагностический процесс, не benchmark приложения и неPASS: тест прервался1/1 раньше запланированных600s. `tools/diagnostics/MinimalDecoderComparisonTest.kt`, `evidence/beta6-minimal-comparator-{numeric,instrumentation,codec}.log`.

Это показывает, что оболочка и управление сессиями Twitch2.0 не необходимы для воспроизведения ошибки; точная причина в сочетании потока, Media3 и программного codec ещё не выделена. Нельзя утверждать, что виновата только ОС или что физическийTV будет вести себя так же. Параллельный другой эфир beta.5 и общий Windowshost могли влиять на нагрузку.

Другой канал AliBumayeTV в обычном beta.5/cb26… прошёл **30мин11.490с**:54306rendered/31dropped,1rebuffer450ms,0errors, действующие чат/панели и возврат к карточке. PSS95885→96781KB, диапазон95133…105712KB. Полные границы, включая параллельные нагрузки и финальную рекламную заставкуTwitch, — [PERFORMANCE.md](PERFORMANCE.md). Это результат beta.5; короткие native beta.6 проверки и диагностический standalone процесс описаны отдельно.

Физический ARM TV и аппаратное декодирование не проверены. Реальные token expiry/revoke и закрытый контент остаются границами проверки; повторный личный вход не запрашивался.
