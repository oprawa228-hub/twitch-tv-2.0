# Проверки Twitch 2.0

Снимок состояния 2026-11-08. Реальные Twitch/Android сценарии, диагностические тесты и сетевые пробы различаются. Полное поручение ещё не завершено.

## Среда и сборки

- Отдельный Android TV `Twitch20Test`, serial `emulator-5560`, API 30/x86, 1280×720/density 213; `.local/avd`. Старый `OprawaTVTest` не изменён.
- Signed minified release `com.oprawa.twitch20`, `0.1.0-alpha.1`/versionCode 1 собран и установлен. [Первая сборка](../evidence/build-first.log), [поздняя успешная сборка release/test APK](../evidence/test-build.log).
- Staged candidate: [Twitch20-0.1.0-alpha.1.apk](../dist/v0.1.0-alpha.1/Twitch20-0.1.0-alpha.1.apk), 2 711 016 байт; [update.json](../dist/v0.1.0-alpha.1/update.json), [SHA256SUMS](../dist/v0.1.0-alpha.1/SHA256SUMS.txt). APK SHA-256 `f0e467ac08cb3fc985d016783b2d0e8af9ff23d008f76b771e9d802ca26f2a88`; certificate SHA-256 `f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7`.
- URL в update.json подготовлен для будущей публикации и не доказывает доступность релиза. После diagnostic debug установки root восстанавливает точный staged release для дальнейшего QA.

## Подтверждённые реальные сценарии

| Проверка | Результат и границы | Доказательство |
|---|---|---|
| Каталог в APK | Настоящие каналы/изображения, фокус карточки; не fixtures | [Первый каталог](../evidence/android/first-catalog.png), [обновлённый XML](../evidence/android/catalog-v2.xml) |
| Live и IRC одновременно | Media3 вывел кадр Twitch и реальные сообщения чата в signed release. Первый кадр содержит Twitch commercial-break slate; он не скрывается и не подменяется | [Live + chat](../evidence/android/live-playing.png), [XML](../evidence/android/live-playing.xml) |
| Первый кадр | Root наблюдал `first_frame_ms=1625`: одно измерение от session.play после resolve URL. Не click TTFF, медиана или p95 | Native QA наблюдение и Media3 AnalyticsListener; screenshot сам время не измеряет |
| Back из просмотра | Первоначальный crash воспроизведён; после cleanup fix root повторил live + chat → Back успешно, вернулся каталог с фокусом | [Retraced crash](../evidence/back-crash-retraced.log), [после исправления](../evidence/android/back-fixed.png), [XML](../evidence/android/back-fixed.xml) |
| Настоящий OAuth | Пользователь завершил DCF, приложение показало `Вы вошли: oprawa` | [Кадр входа](../evidence/android/auth-status.png), [XML](../evidence/android/auth-status.xml) |
| Личный каталог Helix | После входа загружен отслеживаемый live-канал `TyutcheV_V` | [XML личного каталога](../evidence/android/followed-channels.xml); одноимённый PNG снят раньше и ещё показывает загрузку |
| Нативные настройки | Сняты общие настройки с примером и правая панель плеера. Это подтверждает рендер состояний, но не весь lifecycle/Save/Cancel контракт | [Общий пример](../evidence/android/settings-preview.png), [панель](../evidence/android/player-settings.png) |

После успешного входа/личного каталога пользователь попросил пропустить дальнейшую проверку аккаунта. Это не отменяет состоявшийся успех; refresh/expiry/revocation не проверены. Повторный личный вход сейчас не требуется для независимой работы.

## Автоматические проверки

Последние JVM XML: **2026-11-08 13:20:55 UTC** (16:20:55 Москва), **38 тестов, 0 failures, 0 errors**.

| Набор | Число | Результат |
|---|---:|---|
| TwitchAuthTest | 10 | PASS |
| ChatClientTest | 8 | PASS |
| PlaybackHistoryTest | 4 | PASS |
| TwitchParsingTest | 5 | PASS |
| PlaybackPolicyTest | 4 | PASS |
| UpdatePolicyTest | 7 | PASS |

[XML результатов](../app/build/test-results/testDebugUnitTest) проверяют собственные parsing/policy/state правила; они не доказывают всю внешнюю интеграцию или APK.

Последний diagnostic Android instrumentation: **2/2 PASS за 26.628 с**, [instrumentation-debug.log](../evidence/instrumentation-debug.log). Проверены D-pad переходы каталог → категории → каталог → настройки → Back и draft Cancel / explicit Save с повторным открытием. Настоящие клавиши D-pad, [TvNavigationTest.kt](../app/src/androidTest/java/com/oprawa/twitch20/TvNavigationTest.kt).

Перед этим оба instrumentation-теста падали на помощнике определения/достижения фокуса. Helper исправлен; успешный повтор выполнен и является latest. Область — **diagnostic debug**, не тот же staged signed release. Process-restart тест явно не проверяет. [Сборка debug test APK](../evidence/test-build-debug.log) сама не является запуском тестов.

## Исправленные обнаруженные проблемы

- **P1 Back crash:** synchronous ConnectionPool.evictAll закрывал TLS на UI и вызывал NetworkOnMainThreadException. Весь HTTP cleanup, включая cancelAll, перенесён в клиентский executor с shutdown; ExoPlayer/MediaSession release остался main. StrictMode не отключён. Root повторил сценарий успешно.
- Retry теперь повторно получает signed URL через repository вместо повторения истёкшей ссылки.
- IRC server command отделён от текста зрителя: RECONNECT внутри PRIVMSG не должен разрывать соединение; добавлена регрессионная проверка. TLS/upgrade timeout ограничен.
- Часы перемещаются влево при overlay-чате сверху справа. Визуально проверять актуальный APK, не первый кадр до исправления.
- OkHttp 5.5.1 потребовал SDK 37; выбран 4.12.1 с security backport. Исправлены Kotlin compilerOptions DSL, лимит общего Twitch каталога и отдельные IRC login frames.

## Сетевые пробы отдельно от APK

Пробы Twitch каталога, live/VOD/clip URL и IRC: [research-twitch.md](research-twitch.md), [probe_twitch.py](../tools/probe_twitch.py). Они не подтверждают Android-декодирование VOD/clip, subscriber-only источники или весь account lifecycle.

## Пока не подтверждено

- Полные VOD/clip сценарии APK: видео, seek, сохранение/восстановление позиции; все качества и переходы.
- Каждый параметр настроек, long OK, overlay/theater, process-restart и отсутствие перезапуска видео во всех панелях.
- Длительное видео, 20+ смен каналов, busy chat, Home/resume, потеря/возврат сети, expiry, память, frame pacing/p95. Одно значение 1625 мс не заменяет performance report.
- Физический ARM TV, аппаратные декодеры/1080p60 и читаемость с дивана; API 23/24/34/36. Эмулятор API 30 этого не доказывает.
- GitHub APK-релиз, download опубликованного APK через updater и **настоящий переход между двумя подписанными версиями с сохранением данных**. Есть код/политики/staging, OTA-доказательства пока нет.