# Внутренняя часть — beta.6

2026-11-08. Дизайн отложен пользователем. Основные изменения auth/catalog/chat/playback и их границы перечислены в [отчёте beta.5](BACKEND_FINAL.md); этот выпуск добавляет исправление повторного запуска плеера.

## Причина и исправление

После настоящего OTA6→7 дополнительная проверка воспроизвела crash: запись на паузе → Home → явный запуск MainActivity → открытие нового видео. Стандартный launch mode создавал второй Activity; оба PlaybackSession использовали пустой MediaSession ID. Android завершал процесс с `Session ID must be unique. ID=`. Сохранённые настройки и позиция записи при OTA не терялись.

MainActivity теперь использует singleTask, чтобы обычный повторный запуск возвращал существующий экран. Каждый PlaybackSession получает уникальный ID на весь срок жизни владельца; release освобождает прежние ресурсы. Источники, буферы, выбор качества и decoder configuration не изменены. Обоснование: [Android launch modes](https://developer.android.com/guide/topics/manifest/activity-element#lmode), [Media3 session identity](https://developer.android.com/media/media3/session/control-playback#unique-session-id).

Regression `MediaSessionOwnershipTest` воспроизвёл ошибку на настоящем Android: **1 test / 1 failure**, `evidence/beta6-session-red7.log`. Ранний `beta6-session-red.log` непригоден: установка debug была отклонена из-за version downgrade; он не считается RED.

## Точный APK и проверки

- Package `com.oprawa.twitch20`, versionCode **8**, `0.1.0-beta.6`, signed/minified release, **2 733 993 байта**.
- SHA-256 **459ff94be9cb8d356620c3da0e0020d17ca66c2b519c80c22c18b5e5c668ea6c**.
- Постоянный сертификат **f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7**; `evidence/beta6-candidate.json`, `beta6-signature.log`, `beta6-build.log`.
- **121 JVM PASS**, без skipped/failures. **8 Android PASS / 59.072s** на guest `Twitch20Release`/5562: пять native D-pad scenarios, два Media3 intent и новый regression двух владельцев. Авторизованный pagination scenario отдельно ожидает primary5560; гостевой прогон не выдаётся за его проверку.
- Exact release на5562: paused VOD→Home→explicit launch сохранил тот же Activity и позицию143s; playing VOD→Home→launch сохранил Activity, намерение воспроизведения и движение позиции. `tools/qa_activity_relaunch.py`, `evidence/beta6-release-relaunch.log` — **PASS обоих сценариев**.
- Exact release clip1080p60→720p60: позиция0:04 и пауза сохранены (`beta6-clip-quality.log`, `beta6-clip-720-paused.log`). Resume продолжил видео; настоящий конец0:29/0:29 наблюдён, повторное открытие того же `WWW ANAR` началось с0:00 (`beta6-clip-ended`, `beta6-clip-reopened-initial`). В первом helper неверно ожидался заголовок «Записи» вместо «Клипы · buster»; весь helper не считается PASS, повторное открытие проверено отдельно по настоящему экрану.

## Доставка

Beta.5/code7 уже опубликован, OTA6→7 прошёл через меню и системный installer, public/feed/installed APK совпали с cb26…; настройки theatre/16/65%/clock/animations и VOD1:23 сохранились, запись затем играла до1:42. `evidence/beta5-ota-installed.json`, `beta5-ota-settings-retained`, `beta5-ota-history-retained`. Это отдельный предыдущий переход.

Публикация beta.6 и OTA7→8 ожидают завершения targeted проверок. Исходный APK beta.5 не заменяется.

## Отдельное ограничение декодирования

Три прогона KSPKSP на software AVC API30/x86 получили4003: предварительный beta.5 —720p60/319.161s и1425.246s; опубликованный beta.5 —480p/44.327s. Это не crash идентификатора сессии. UUID/singleTask не объявляются исправлением codec failure. Сравнение другого канала на beta.5 и минимального Media3-плеера документируется отдельно; результаты не переименовываются в exact beta.6 soak.

Физический ARM TV и аппаратное декодирование не проверены. Реальные token expiry/revoke и закрытый контент остаются границами проверки; повторный личный вход не запрашивался.
