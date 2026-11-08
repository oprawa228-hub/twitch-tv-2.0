# Внутренняя часть Twitch 2.0 — beta.5

Срез: 2026-11-08. Объём определяется [TASK.txt](TASK.txt), единый план — [PLAN.md](PLAN.md). Дизайн отложен по просьбе пользователя. Собственный сервер не требуется.

**Исторический отчёт beta.5/code7. Актуальный выпуск — [beta.6/code8](BACKEND_BETA6.md).** OTA6→7 сохранил настройки и VOD1:23. Последующая проверка обнаружила crash при повторном запуске Activity (`Session ID must be unique`); он исправлен и доставлен в beta.6. Ниже результаты beta.5 отделены от предварительного кандидата и последующего выпуска.

## Опубликованный APK beta.5

- `com.oprawa.twitch20`, `0.1.0-beta.5`, versionCode **7**, minSdk23, signed/minified release, **2 733 968 байт**.
- SHA-256: `cb26d9eb713e246725ca70d9e0ebd050a0a3fb30a948a69ae80e45434bd21f10`.
- Сертификат SHA-256: `f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7`, идентичность прежних выпусков сохранена.
- После последних изменений: **121 JVM PASS**, полный **8 Android PASS / 84.861 с**. Логи `evidence/beta5-candidate4-build.log`, `beta5-candidate4-instrumentation.log`; XML JVM в `app/build/test-results/testDebugUnitTest/`.
- Android suite состоит из шести сценариев D-pad и двух проверок настоящего Media3 на сохранение play/pause intent. Последние используют локальный asset URI только для переходов состояния и **не доказывают декодирование или сеть**.
- Подписанный release установлен поверх прежних данных на `Twitch20Test`/5560; аккаунт сохранился. Это ручная install compatibility. Отдельный настоящий OTA6→7 на5562 указан ниже.

Raw XML/PNG/logs в `evidence/` исключены из Git целиком. В публичном отчёте нет токенов, signed media URL или текста чата.

## Реализация и покрытие

| Область | Что закрыто | Доказательства и границы |
|---|---|---|
| Каталог | Helix-пагинация после входа, дедупликация, отмена устаревших запросов, retry без потери карточек, LRU; официальный header канала; явное обновление первой страницы | JVM parser/page/cancel/retry/bounds и header; Android D-pad за пределы30 эфиров и возврат. Native offline-page/retry подтверждён beta.4. Guest ограничен первой страницей Twitch. |
| Авторизация | DCF, отслеживаемые каналы; минутная переоценка непроверенной сессии после late save/cancel; hourly validate здоровой сессии; bounded refresh/save и защита от logout race | JVM production helpers и HTTP-policy fixtures. Реальный вход/follows подтверждены ранее; сохранённый аккаунт виден в текущем release. Настоящие expiry/refresh/revoke не проверялись, нового входа не требовали. |
| Плеер | Live/VOD/clip, корректный pause intent, очистка stale error после foreground, bounded загрузка, release ресурсов, завершённая запись не возобновляется с конца | JVM policies; два Android-теста same-media retry, завершившегося после ON_STOP: играющее видео возобновляется, пауза сохраняется. Native текущего APK перечислены ниже. |
| Качество | Все доступные MP4 варианты; сохранение позиции и паузы. HLS choice восстанавливается по height/fps, clip — по стабильному ID с новым URL. Auto/new media/unavailable variant сбрасывают выбор | 14 новых JVM regressions. Пустые tracks сохраняют выбор и меню восстановления; старый TrackGroup не переносится между manifest. Pending manual не отмечается как Auto. |
| Восстановление | В ошибке доступно «Качество», если варианты уже известны; выбор подготавливает источник заново с live edge/VOD position и прежним pause intent. Retry заново получает источник и сохраняет ручное качество | Независимое ревью интеграции и focus/back. Автоматических бесконечных попыток или скрытого снижения качества нет. Реальные network/error/quality проверки текущего APK ниже. |
| Чат | Login/user ID отдельно от display name, корректный CLEARCHAT, USERNOTICE и terminal NOTICE, дедупликация/лимиты, честные quiet/connecting/error статусы | 13 JVM chat regressions. Соединение только для foreground live и включённого/предпросматриваемого чата; история150 сообщений, UI100. Полный WebSocket lifecycle не подменяется parser-тестами. |
| Настройки | Overlay/theater, размер/фон/положение/часы/анимации; draft preview, Save/Cancel и возврат | Текущие Android D-pad tests; реальные draft on/off и чат проверены на предварительном кандидате. Визуальное оформление не объявляется окончательным. |
| Хранение | Settings/history в SharedPreferences; история100 позиций без URL. OAuth в AtomicFile+Android Keystore AES-GCM; backup отключён | Review и JVM history/auth policies. Сквозного AndroidKeyStore rotate/revoke теста нет. Settings/history подтверждены через OTA6→7; следующий7→8 отдельно описан вBACKEND_BETA6. |
| Обновления | Отдельный GitHub-канал; SHA/package/version/API/certificate, cancel/retry, private FileProvider и системный installer | JVM update policy; предыдущая цепочка OTA2→4→5→6. Переход6→7 завершён; public/feed/installed hashes совпали, settings/VOD1:23 сохранены. |
| Метрики | Request/resolve/prepare→first frame; rebuffer отдельно от startup/seek/foreground/recovery/quality; числовые decoder counters | JVM fake-clock tests, реальные предыдущие и текущие выборки в [PERFORMANCE.md](PERFORMANCE.md). Это не физический input-to-photon или аппаратный TV benchmark. |

## Native-проверка текущего APK

| Проверка | Результат |
|---|---|
| Реальная ошибка сети → выбор360p → видео; fresh-source Retry сохраняет360p | **PASS** на точном release; `beta5-final-quality-recovery.log`, XML/PNG `beta5-final-error-quality360-recovered`, `beta5-final-retry360-selected`. После ON_STOP чат очистился, после подключения снова видны настоящие сообщения. |
| 20+ открытий/возвратов с памятью | **PASS:22 разных канала,22 первых старта/возврата**; выбор карточки сохранён. Request→frame median3068ms/p953962ms/max4307ms; PSS64 056…92 699KB. `beta5-final-cycles/`, `beta5-final-cycles-summary.json`. |
| Длительный480p live с чатом/панелями | KSPKSP: **FAIL**, runtime4003 через44.327s после выбора480p,1317 rendered/0 dropped/54 skipped. Отдельный AliBumayeTV480p: **PASS30мин11.490с**,54306rendered/31dropped,1rebuffer450ms,0errors, возврат к карточке. Chat/start-mid-end2/5/5 rows; drafttext/theater Cancel без перезапуска. Подробности и общийhost/реклама/границы в [PERFORMANCE.md](PERFORMANCE.md). |
| MP4 quality/position/completion | Целевое повторение выполнено на следующем exactrelease beta.6/459ff94…, а не приписано beta.5; см. [отчёт](BACKEND_BETA6.md). |
| Публикация, feed и OTA6→7 с settings/VOD history | **PASS**: три public asset побайтно совпали; feed7; installed cb26…/code7 через Android installer. Theatre/16/65%/clock/animations и VOD1:23 сохранились, запись играла до1:42. Последующий relaunch crash исправляется в [beta.6](BACKEND_BETA6.md). |

## Предварительный кандидат и найденные ограничения

На текущем APK ошибка KSPKSP повторилась и в480p: `SoftAVCDec Fatal Error:0xffffffff`/ACodec0x80001019 предшествует Java codec exception. PSS105518→99151KB не доказывает OOM. Снижение качества не объявляется исправлением. Точная причина сочетания потока, системного decoder и Media3 не установлена; независимое ревью пока не обнаружило подтверждённого дефекта приложения, объясняющего все три случая.

Первый candidate code7 имел SHA `68a655937e417d763c3b18c57ddb9a4d1baaf30628217160804fafffe01aa0fb`, 2 733 965 байт. Его результаты **не переименованы в проверки текущего APK**:

- 107 JVM PASS; шесть Android PASS/81.817с до двух промежуточных исправлений. Native: chat saved-off→draft-on с настоящими сообщениями, Cancel→off, Save→on; clip1080p60→720p60 сохранил0:13/paused, Resume дал0:14; завершённый30s clip заново открылся0:00; real2001→Home→network restore→foreground очистил ошибку.
- 23 разных канала/24 попытки:22 первых старта, один decoder4003 до первого кадра и успешный ручнойRetry; все возвраты сохранили карточку. Request→frame median2793ms/p954194ms/max8559ms среди22 успешных первых стартов; отдельныйRetry2172ms. PSS после возврата73 200…94 088KB. Подробности в PERFORMANCE.
- Первый720p60 soak прервался через319.161s renderer:19075 rendered/0 dropped/236 skipped, rebuffer1328ms. Отдельно упал UiAutomator shell с alreadyregistered; приложение и codec services оставались живы.
- Второй720p60 soak **без периодического UiAutomator** также прервался через1425.246s:83631 rendered/0 dropped/220 skipped, rebuffer1190ms. Это исключает утверждение, что decoder failure был только сбоем наблюдения.
- В обоих случаях software `OMX.google.h264.decoder`/SoftAVCDec сообщил4003 и `Fatal Error:0xffffffff`. Конкретная причина не установлена; ни OOM, ни постоянная нехватка CPU не доказаны. Последующие доработки улучшают доступное восстановление, **не объявляют системный decoder исправленным**.
- Диагностический конфликт исправлен межпроцессной блокировкой dump на каждый serial. Два одновременных запроса на5562 завершились успешно последовательно:1.72/3.33с (`beta5-qa-lock-test.json`).

Значение4003 — runtime decode failure, а не init failure. Поэтому `setEnableDecoderFallback` не добавлялся без доказательств: [PlaybackException](https://developer.android.com/reference/androidx/media3/common/PlaybackException), [DefaultRenderersFactory](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory), [AOSP SoftAVCDec](https://android.googlesource.com/platform/frameworks/av/+/e3c46b0a49d045fa38d35e5cf7ad4becaacbbed6/media/libstagefright/codecs/avcdec/SoftAVCDec.cpp).

## Regression и независимое ревью

Наблюдены runnable RED98/12 и107/6 для auth/chat/data; новые quality tests10/10, затем pending-choice14/4. Android lifecycle RED2/1 воспроизвёл потерю resume intent после фонового завершения Retry (`beta5-lifecycle-red.log`, expectedtrue/actualfalse). После исправления полный121 JVM и8 Android прошли. Ошибка ранней компиляции не выдана за runnable RED.

Независимые review закрыли chat preview connection demand, HLS quality metric marker, pending manual checkmark и background retry intent. Локальные builds/logs не заменяют public APK и настоящий OTA.

## Границы и следующий этап

Проверено на Android TV API30/x86/SwiftShader: primary4vCPU/2GB и secondary2vCPU/1.5GB на одном Windows host. Физический ARM TV, аппаратные декодеры, длительное1080p60 и OEM-варианты ещё не проверены. Реальные expiry/revoke/закрытый контент не подтверждены. Guest GQL/Usher недокументирован и может меняться; guest pagination блокируется IntegrityCheckFailed, после входа используется Helix. Дополнительные эмоты, отправка чата, previews и уведомления имеют границы из [SCOPE.md](SCOPE.md).

Следующий продуктовый этап — дизайн после завершения доставки внутренней части.
