# Коммерческая заглушка Twitch: расследование beta.10

Дата: 2026-11-09. Проверены текущий исходный код Twitch 2.0 и read-only исходники `D:/Project/twitch-tv-by-oprawa-handoff`. Сопоставление исходников не заменяет воспроизведение на конкретном канале и не доказывает идентичность установленного старого APK его текущему checkout.

## Что установлено

| Участок | Twitch 1.0, найденный путь | Twitch 2.0 | Вывод |
|---|---|---|---|
| Получение live token | `upstream/app/specific/PlayHLS.js`: активный persisted query с `platform=web`, `playerType=site` | `TwitchRepository.resolveLive`: `platform=web`, `playerBackend=mediaplayer`, `playerType=site` | Совет переключить тип клиента на mobile/embed не обоснован этим сравнением. Закомментированный старый ios query не является активной реализацией. |
| Авторизация source resolver | `Main.js::HttpGetSetUserHeader`: `Play_Headers` получает OAuth пользователя при наличии его токена | GQL получает только публичный web Client ID; официальный OAuth используется отдельно для Helix | Старый аккаунт мог влиять на выдачу рекламы, например через подписку. Наличие официального входа в 2.0 само по себе не делает текущий resolver авторизованным. Нельзя без проверки совместимости отправлять OAuth нашего Client ID от имени чужого web Client ID. |
| Live master | `PlayHLS_GetPlayListUrl`: `play_session_id`, случайный `p`, `player_backend`, `reassignments_supported` и другие параметры | URI master с подписью/token, source/audio/codecs/framerate/player | Различие реальное, но его влияние на конкретную заглушку не измерено. Недокументированные параметры не добавлялись наугад. |
| Прокси | Переменная `use_proxy=false`; UI `proxy_settings` и вызов `Settings_proxy_set_start` закомментированы в `Settings.js` | Прямой usher | В старом дереве есть код сторонних прокси, но найденный startup не включает их. Утверждать, что они обеспечивали отсутствие рекламы, нельзя. |
| Native путь | `Play.js::Play_loadData` → `PlayHLS_GetPlayListAsync` → `Play_loadDataSuccessEnd` → Android bridge → `Tools.buildMediaSource` | Repository → `PlaybackSession` → стандартный Media3 | Native HUD старого приложения не означает отдельный native resolver: Java принимает уже полученный playlist. Java token resolver поиском не найден. `tools/package_assets.py` упаковывает именно `upstream/app` в Android assets. |
| Старый HLS fork | `media/libraries/exoplayer_hls`: custom live-start/latency; `Tools` передаёт master bytes через `DefaultHttpDataSource` | Media3 1.11.1 со stream-declared target offset | В просмотренных parser/chunk-source/tracker нет фильтра Twitch commercial/slate. `HlsMediaSource` выбирает live-start по сегментам и корректирует target duration; это настройка latency, не подтверждённое удаление рекламы. |
| Пауза/PLAY | `PlayerActivity.setPlaybackRequested`: live-edge seek только при переходе `!playWhenReady → play`; source не запрашивается заново при обычной паузе | `resume()` раньше делал live-edge seek на каждый PLAY | Подтверждённый лишний seek в 2.0 исправлен. Это могло повторно проигрывать часть уже полученного потока; это не доказательство причины пользовательской заглушки. |

## Исправления

- `PlaybackSession.resume()` стал идемпотентным: повторные команды PLAY во время воспроизведения не перематывают live и не заменяют source. Настоящий переход из паузы сохраняет возврат к live edge, как в старом приложении. Replay завершённой записи не блокируется.
- Восстановление после `ERROR_CODE_BEHIND_LIVE_WINDOW` сохраняет пользовательскую паузу. `seekToDefaultPosition()` и `prepare()` исправляют истёкшее окно, а принудительный `play()` удалён: обновление HLS может обнаружить выход за окно даже во время паузы.
- Resolver, аккаунт и настройки рекламной выдачи не менялись. Автоматический бесконечный retry на новую source-сессию не добавлялся.

Обоснование поведения Media3: [официальная документация live playback](https://developer.android.com/media/media3/exoplayer/live-streaming) описывает `seekToDefaultPosition()` как перемещение к стартовой позиции живого окна; для behind-live-window показывает seek + prepare без принудительного изменения play/pause. Проверено 2026-11-09. Установленную Media3 1.11.1 не понижали до версии из справочного skill.

## Проверки и ограничения

Добавлен instrumented regression `PlaybackLifecycleTest.repeatedLivePlayDoesNotSeekOrReplaceTheSource`: настоящий экземпляр Media3, синхронные команды, подсчёт `DISCONTINUITY_REASON_SEEK`, проверка позиции и неизменности MediaItem. Тест специально не использует внешнюю сеть и не доказывает работоспособность Twitch или завершение рекламы. Существующие lifecycle-тесты отдельно проверяют сохранение paused/playing intent при source resolution после ухода в фон.

На момент передачи изменений root сборка и запуск этих тестов агентом расследования не выполнялись: устройствами и сборкой владеет root. Результаты root следует записать в основной отчёт/PLAN.

Пользователь уточнил: коммерческое окно возникает почти на всех каналах, отдельного примера нет. На primary emulator root затем воспроизвёл саму заглушку на Caedrel: после игры появилась `Commercial break in progress`; спустя примерно две минуты игра вернулась сама без повторного resolve. После последующей паузы/продолжения на игре за примерно 20 секунд наблюдения заглушка не вернулась. Следовательно заглушка подтверждена, её бесконечность и повтор от паузы на этом запуске не подтверждены. Сохранённый вход primary emulator и гостевой secondary emulator — разные сценарии; источник текущего live resolver остаётся guest в обоих. Физический TV не проверен. Отсутствие рекламы не заявляется.

Для проверки root: один и тот же проблемный канал → дождаться фактического видео/заглушки → пауза 5–10 секунд → продолжение → повторный PLAY → отдельный возврат из фона. Зафиксировать длительность заглушки, продвижение playback/playlist, число source resolve/prepare/seek и сохранение паузы при recovery. Сравнить старое приложение при той же сети/аккаунте, отдельно от guest. Не сохранять токены, подписанные URI и необработанные тела playlist в отчёт или Git.

## Безопасная диагностика HLS

Добавлен `HlsPlaybackDiagnostics`: `PlaybackSession` наблюдает уже загруженный Media3 `HlsManifest`, без дополнительных запросов и изменения playlist. В `TwitchPlayback` раз в 5 секунд выводится строка `hls_window`; изменение наличия commercial metadata выводится сразу. Поля:

- `media_sequence`, `sequence_delta` — текущее начало окна и изменение относительно предыдущей строки лога. Отрицательная разница возможна при переключении варианта и сама по себе не означает ошибку.
- `segment_count`, `trailing_part_count`, `window_duration_ms` — размер доступного окна, включая рост LL-HLS parts.
- `playlist_unchanged_ms` — сколько времени наблюдаемые численные характеристики окна не менялись. Это не измерение состояния сервера: на паузе/при остановленной загрузке отсутствие изменений само по себе ожидаемо.
- `end_tag` — поток обозначен завершённым.
- `commercial_metadata` — наличие Twitch ad DATERANGE/атрибутов в playlist. Это **не** `active_ad`: маркер может оставаться после показа; отсутствие маркера также не доказывает отсутствие заглушки в видео.
- `position_ms`, `buffered_ms`, `live_offset_ms`, `default_position_ms`, `playing`, `play_requested` — позиция и намерение пользователя рядом с характеристиками окна. Неизвестные live/default значения обозначаются `-1`. Эти поля добавлены после первой нативной репродукции и использованы в последующих проверках Riot ниже.

Класс не сохраняет исходные tags и не принимает URL, идентификатор аккаунта или название канала. Итоговая строка собирается только из чисел/булевых значений. Наблюдения сбрасываются при новом `play()`. Четыре unit regression проверяют продвижение/остановку окна, рост parts, отсутствие утечки значений tags и сброс. Запуск тестов остаётся за root.

## Можно ли восстановить авторизованный native playback официальным Client ID

Повторно проверена первичная документация 2026-11-09:

1. [Twitch OAuth](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/) документирует Device Code Flow для ТВ и приставок. Это авторизация API от нашего зарегистрированного приложения; существующий вход сохраняется.
2. [Helix Reference](https://dev.twitch.tv/docs/api/reference/#get-streams) предоставляет метаданные эфиров. Публичного контрактного endpoint для выдачи HLS playback token/URI под нашим Client ID в просмотренном справочнике не найдено.
3. [Официальный video embed](https://dev.twitch.tv/docs/embed/video-and-clips/) — документированный способ встроить показ. Он работает как веб-плеер, требует корректного parent/domain; параметра передачи стороннего OAuth access token в опубликованном API embed нет. Из этого нельзя вывести обещание переноса подписок/Turbo из нашего Device Code Flow в native Media3.
4. Дополнительное первичное свидетельство разработчиков клиента: [Streamlink authentication](https://streamlink.github.io/cli/plugins/twitch.html#authentication) описывает прекращение собственного OAuth flow из-за ограничений private playback API: сторонние OAuth tokens не принимались при получении streaming access token, принимались данные входа сайта Twitch. Это документирует опыт Streamlink, а не официальный контракт Twitch.

Вывод: официально поддержанного способа подставить наш Device Code OAuth в текущий native source resolver не найдено. Автоматически смешивать его с website Client ID, импортировать cookies либо заменять существующий TV player веб-плеером в рамках этой правки не стали. Поддерживаемый embed был бы отдельной архитектурной задачей с проверкой D-pad, Android WebView, домена и фактической авторизации; он также не обещает отсутствие рекламы. Для текущего Media3 сохраняем изоляцию источника, собираем безопасные наблюдения реального дефекта и не объявляем лимит интеграции исправленным изменением UI/пауз.

## Реальное сравнение source-профилей

`tools/probe_live_source_profiles.py` читает только guest token/master/media playlists. Не читает аккаунт, не скачивает видео, не печатает URI/token/исключения с их содержимым. Три профиля используют один временный guest token для изоляции различий usher-параметров: текущие параметры; текущие + `play_session_id`/`p`; прежние параметры из `PlayHLS.js` с тем же AVC codec. Это сравнение параметров, не полная эмуляция старого авторизованного клиента.

Первый запуск Caedrel, 125 секунд, host UTC 2026-11-08 23:07:45–23:09:50:

| Время от старта | Все три профиля |
|---|---|
| 0 с | HTTP 200, sequence 10897, 15 сегментов, окно 30 с, commercial ranges 0 |
| 10 с | Sequence 10903, 17 сегментов, окно 34 с, commercial ranges 4; основной DATERANGE duration 30.235 с, pod length 1 |
| 31 с | Sequence 10912, 17 сегментов, окно 32.235 с, commercial ranges 6 |
| 42–52 с | Sequence 10912 закреплён, сегменты 22→27, окно 42.235→52.235 с |
| 62 с | Sequence 10929, окно 32 с, commercial ranges 0 |
| 73–125 с | Обычное окно продолжает двигаться, commercial ranges 0 |

Растущее закреплённое окно наблюдается непосредственно в HTTP ответе, поэтому оно не создано Media3 parser. Прежние параметры и session ID не устранили его. Возврат к обычному окну произошёл без нового token/master. Основной ad DATERANGE duration не равен гарантированному времени видимой заглушки: поэтому на его основе нельзя обещать точный отсчёт окончания рекламы.

Повторный запуск с агрегатами положения ad ranges сохранён в `evidence/beta10-source-profiles.jsonl`; все строки содержат только разрешённые скалярные поля. Нативная репродукция root: `evidence/beta10-live-diagnostic.log`, изображения `evidence/android/beta10-commercial-check.png`, `beta10-commercial-before-tests.png`, `beta10-after-pause-resume.png`.

Старый fork использует другой live edge: обычный сохранённый `Play_LowLatency` по умолчанию 1, переводимый через `[0,2,1]` в два сегмента от конца. При наблюдавшихся 2-секундных сегментах это примерно 4 секунды; текущий upstream fallback при `TARGETDURATION=6` может выбрать 18 секунд, если источник не задаёт START или hold-back. Для Riot источник задаёт START=0, поэтому именно fallback18с к нему **не относится**; ниже разобрана и исправлена отдельная ошибка resume. Условие recovery по одному `commercial_metadata=1` некорректно, поскольку маркер переживает собственный диапазон, а источник продолжает двигаться и сам завершает вставку.

## Второй случай: Riot Games, подтверждённый PlaylistStuckException

Последующая проверка root выявила другой исход: Riot Games начал со slate, через примерно 20–30 секунд остановился с error 2000. В cause chain root безопасно подтвердил `HlsPlaylistTracker.PlaylistStuckException`, не выводя содержащийся в исключении URI. Manifest snapshot оставался sequence 0 / 3 сегмента / 6000 мс при позиции около 30101 мс и нарастающем live offset. Источник не возобновился сам после fatal error. Это уже конкретный дефект восстановления, отличный от конечной вставки Caedrel. Доказательство: `evidence/beta10-riot-diagnostic.log` и `evidence/android/beta10-riot-later.png`.

В старом приложении найден дополнительный существенный путь: `PlayerActivity.PlayerEventListenerCheckCounter` на первую ошибку вызывает `PlayerEventListenerCheckCounterEnd` → `updateResumePosition` → `SimpleReleasePlayer` → `SetupPlayer`. Старый tracker также формирует Stuck/Reset exceptions; отличие состоит в восстановлении владельцем плеера после ошибки, а не в удалении рекламы.

Добавлено ограниченное восстановление:

- Только live и вложенный `PlaylistStuckException` либо `PlaylistResetException`; общего retry на error 2000, ad marker или HTTP 403 нет.
- Одна автоматическая попытка на явный запуск/ручной retry. Переход READY не возобновляет бюджет, поэтому повторяющаяся неисправность не создаёт вечный цикл.
- Через 1 секунду создаётся новый MediaSource/tracker из того же MediaItem/URI. Новый OAuth/playback token не запрашивается.
- Сохраняется текущее play/pause намерение, включая паузу внутри задержки. Возврат из фона завершает отложенное исправление, не теряя паузу; в фоне сеть не запускается. Смена источника/ручной retry отменяет старую задачу. Ручное восстановление через выбор качества также отменяет автоматическую задачу и pending item, не обновляя бюджет.
- Семантические ограничения выбранного качества остаются, старые TrackGroup overrides удаляются и восстанавливаются на новых tracks.
- После исчерпания попытки пользователь получает «Поток Twitch перестал обновляться. Повторите подключение.». Лог содержит код и только имена классов причин, максимум восемь уровней; exception message и URI не выводятся.

[Media3 PlaylistStuckException](https://developer.android.com/reference/androidx/media3/exoplayer/hls/playlist/HlsPlaylistTracker.PlaylistStuckException) и [PlaylistResetException](https://developer.android.com/reference/androidx/media3/exoplayer/hls/playlist/HlsPlaylistTracker.PlaylistResetException) — отдельные диагностируемые причины HLS tracker. Перезапуск после них не является обещанием убрать коммерческие вставки.

Проверки для root: `LiveSourceRecoveryTest` (бюджет, фильтрация условий, безопасность cause), `HlsSourceRecoveryTest` (реальный HLS parser/tracker/decoder; сеть заменена локальным DataSource). Пять нативных сценариев: frozen→restart→fresh; пауза во время delay; постоянное зависание ограничено одним retry; background во время delay сохраняет deferred repair и паузу; ручной выбор качества отменяет отложенный restart. Локальный 6-секундный материал получен из существующего `qa-seek.mp4`, разбит на три 2-секундных TS (~14 КБ суммарно), находится только в debug assets. Результаты сборки/эмулятора записывает root; агент расследования их не запускал.

Read-only HTTP проверка Riot Games (`evidence/beta10-riot-source-profiles.jsonl`) выбрала самую низкую video rendition и сравнила current/session/old usher и старый media User-Agent. Все начинают sequence 0, 3 сегмента, 6 секунд, START offset 0. За 43 секунды HTTP-окно выросло примерно до 46 секунд/24 сегментов; хвост уже вышел за объявленный ad range. Дополнительная проверка: нет VOD/EVENT/ENDLIST/SERVER-CONTROL. Старый User-Agent не устранил вставку. Различие между живым HTTP-окном и замершим native snapshot может зависеть от primary rendition/момента наблюдения; оно пока не локализовано до сетевого запроса. Старые usher-параметры и User-Agent в production не подставлялись без доказанного эффекта.

## Подтверждённый START=0 и повтор начала при resume

В [исходнике Media3 1.11.1 HlsMediaSource](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java) `getLiveWindowDefaultStartPositionUs` сначала использует `playlist.startOffsetUs`; только при его отсутствии вычисляет позицию по target offset. Поэтому изменение `MediaItem.targetOffsetMs` не отменяет явный START=0. Старый локальный fork вычисляет начало предпоследнего полного сегмента, игнорируя START. Это подтверждённая разница поведения: `seekToDefaultPosition()` при возобновлении мог снова выбрать начало закреплённого рекламного окна.

Исправлены **две операции**: `resume()` и явное «К эфиру». Они вычисляют позицию по предпоследнему полному сегменту manifest, принадлежащего текущему Timeline.Window. Требуются dynamic window, отсутствие ENDLIST, совпадающая длительность playlist/timeline и допустимые монотонные начала сегментов. Переход выполняется только вперёд относительно текущей позиции. При пустом, неизвестном, несогласованном или отстающем snapshot перемотки нет; fallback на START=0 не применяется. Повторный PLAY во время уже запрошенного воспроизведения остаётся без seek.

Это не глобальная подмена HLS parser. Первоначальный запуск, подготовка после foreground, ручной retry и одноразовый source recovery пока сохраняют первоначальную политику Media3. Поэтому автоматический source restart всё ещё может начать доступное окно с START=0; исправление не объявляется универсальным устранением повторной рекламы на любом lifecycle-пути.

Добавлены unit-сценарии растущего 120-секундного окна, позиции впереди безопасного tail и невалидного snapshot. Два дополнительных native HLS-теста используют реальный START=0: при source default0 resume идёт к2с и повторный PLAY не двигает его; окно растёт с6до12с, сохраняя source default0, «К эфиру» выбирает8с, а resume с9с не перематывает назад. Теперь `HlsSourceRecoveryTest` содержит семь сценариев. Missing-asset lifecycle regression также уточнён: при неизвестном HLS window обычный resume не должен угадывать live edge и делать seek к0.

## Повторные реальные проверки Riot: разные исходы сохранены

Первый запуск с ограниченным восстановлением **не исправил доступность этого эфира**: после единственного same-source restart снова возник `PlaylistStuckException`. Окно оставалось sequence0 / 3 сегмента / 6 секунд, тогда как декодер показывал около30 секунд буфера. Доказательство: `evidence/beta10-riot-recovery.log`, `evidence/android/beta10-riot-recovery-running.png`. Ограничение retry устраняет бесконечный автоматический цикл, но этот результат нельзя представлять как успешное восстановление реального канала.

Для локализации добавлена только DEBUG-корреляция: роль playlist, индекс/высота rendition, скалярные свойства результата штатного parser, объём/длительность загрузки, HTTP version/gzip/status и сопоставление URI ошибки с primary playlist **в памяти**. URI, тела playlist, token и exception messages в этих строках отсутствуют. Parser возвращает штатный результат без изменения; в release используется прежняя фабрика. Сопоставление с `DefaultMediaSourceFactory` Media3 1.11.1 не выявило различий используемых default HLS retry/DRM/parser-политик, которые объясняли бы удачный запуск.

В следующем запуске DEBUG root наблюдал уже обычный видеоролик рекламы, затем игру примерно через минуту. Native HTTP/2 + gzip на720p действительно загружал растущее окно: 3→6→8→11→13→16 сегментов, затем окно продолжило двигаться и commercial metadata исчезли. Ошибки прежнего запуска здесь не повторились. Поэтому HTTP/2, gzip, рост segment count при неизменном sequence и выбранная720p rendition сами по себе не подтверждены как причина прежнего зависания. Отдельный host probe highest/lowest rendition также показал рост обоих окон; первые три проверенные TS имели длительность около2 секунд каждый, а не предполагаемые10 секунд. Причина различия между двумя native запусками остаётся не локализована; случайный успешный повтор не доказывает её устранение.

Root затем проверил именно интерфейс плеера: пауза около8 секунд → «К эфиру». В `evidence/beta10-riot-correlation.log` зафиксирован `live_edge_seek from_ms=9491 to_ms=28028`, последующее `playing=1`, продвижение sequence58→61 и commercial_metadata0. Изображение: `evidence/android/beta10-riot-ui-resume.png`. Это реальная проверка нового перехода к эфиру без повторной заглушки в наблюдавшемся интервале. Прямые media-key команды через MediaSession не вызывают `PlaybackSession.resume`; короткое отдельное наблюдение без повтора заглушки не заменяет проверку START0 на этом пути.

## Public Client ID и прямое сравнение старого APK

Дополнительно найдено реальное отличие resolver: старый `Play_Headers` использует публичный `AddCode_backup_client_id`, новый — другой публичный web Client ID. Это проверено и в упакованных assets существующего APK. Для изоляции header выполнено guest-only A/B: одинаковый query и usher-параметры, независимые guest playback tokens, одновременные запросы двух каналов; пользовательские OAuth/cookies не читались и не передавались.

В наблюдениях до42 секунд Riot для обоих Client ID совпал: START0, sequence0, окно6→46.235 секунды, 3→24 сегмента, число commercial ranges2→10. Caedrel для обоих шёл без commercial ranges, sequence12076→12097, обычное30-секундное окно. Это ограниченная HTTP-проверка двух эфиров, не проверка декодирования или всех аккаунтов. Она не обосновывает замену Client ID и не подтверждает отсутствие рекламы у старого guest resolver.

Root также установил существующий кандидат `D:/Project/twitch-tv-by-oprawa-handoff/dist/candidates/Twitch-TV-by-Oprawa-1.0.2.apk` рядом с2.0 на том же primary emulator. Фактический package кандидата — `com.oprawa.twitchtv.test`, versionName1.0.2, versionCode412; использован свежий guest профиль. В старом приложении Recommendations→Riot показал **ту же фиолетовую Commercial break in progress** (`evidence/android/beta10-old-riot-open.png`). Примерно через30 секунд без ввода root старое приложение вернулось на гостевую главную Following (`evidence/android/beta10-old-riot-later.png`). В просмотренном AndroidRuntime fatal-сообщения не найдено: называть этот переход подтверждённым crash нельзя.

Этот кандидат не обязательно идентичен ранее установленной пользователем версии/аккаунту. Сравнение доказывает только наблюдавшееся поведение конкретного guest APK на этом эмуляторе. В2.0 каталог был авторизован официальным аккаунтом, но native playback resolver также guest, поэтому сравнение источников не использовало привилегии подписок. Старые OAuth не переносились. Оснований обещать, что копирование старых параметров уберёт заглушку, не получено; production resolver оставлен без таких подмен.
