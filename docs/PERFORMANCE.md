# Performance evidence — 2026-11-08

## Beta.5 — текущий кандидат

Exact signed/minified code7: SHA-256 `cb26d9eb713e246725ca70d9e0ebd050a0a3fb30a948a69ae80e45434bd21f10`, 2 733 968 байт. Primary API30/x86/SwiftShader,4vCPU/2GB; вторичный AVD включён, общий Windows host. Во время этого прогона Gradle/R8 не выполнялся. Это не физический TV benchmark.

**22 разных канала открылись с первой попытки, все22 возврата сохранили карточку.** Выборка `evidence/beta5-final-cycles/` и `beta5-final-cycles-summary.json`; одна секунда после первого кадра, одинаковое окно2s после закрытия перед meminfo, без принудительного GC. Auto и реальные меняющиеся потоки; это не сравнение одного фиксированного encode.

| Метрика текущего APK | Значение |
|---|---:|
| Request→first frame min / median / p95 / max | 2314 / 3068 / 3962 / 4307 ms |
| PSS после возврата, первый / последний | 64 056 / 85 946 KB |
| PSS после возврата, min / max | 64 056 / 92 699 KB |

Таймер включает разрешение источника и подготовку до первого кадра, но не физическую задержку пульта/дисплея. p95 — nearest-rank. Кэш прогревается, PSS меняется немонотонно; это ограниченная выборка, не доказательство отсутствия всех утечек и не причинное сравнение с прошлым кандидатом.

Длительный480p прогон KSPKSP на текущем APK **FAIL**: renderer44.327s,1317 rendered/0 dropped/54 skipped, runtime4003. `SoftAVCDec Fatal Error:0xffffffff`/ACodec0x80001019 предшествует Java codec exception. PSS105518→99151KB. Источники `evidence/beta5-final-soak480/` и `beta5-final-soak480-codec.log`. Это не30minPASS и не исправление двух предыдущих720p60 failures. Причина сочетания потока/decoder/Media3 пока не установлена.

Отдельный сравнительный прогон AliBumayeTV/480p на этом APK **PASS**: окно renderer1811.490s (30мин11.490с),54306 rendered/31 dropped/73 skipped, max consecutive dropped3; одна rebuffer450ms,0 player errors. НачальноеAuto-окно14.215s/805rendered/3dropped отдельно от ручного480p. После завершения пульт вернул выбранную карточку. `evidence/beta5-final-soak480-comparator/` и `beta5-final-soak480-comparator-summary.json`.

31 memory sample за1800.1s: PSS first95885/last96781/min95133/max105712KB. В начале/середине/конце наблюдались2/5/5 реальных сообщений,12 разных хешей; это не30min busy-chat stress. Периодические UiAutomation снимки выключены; null в sampler означает отсутствие такого измерения. Ручные панели: текст18→draft20→Cancel18, overlay→drafttheater→Cancel; player не перезапускался. Финальный кадр содержит Twitch «Commercial break in progress», не ошибку приложения; непрерывный decode включает этот участок трансляции. Во время прогона выполнялись beta.6 Gradle/R8 и QA второгоAVD на общемhost, поэтому это не изолированныйbenchmark. Эти результаты относятся к beta.5/cb26…, не переименованы в beta.6/459ff94… .

## Независимая диагностика decoder4003

Минимальный Media3/OkHttp/SurfaceView на5562, без PlaybackSession/MediaSession/ContentFrame/чата/панелей, также воспроизвёл KSPKSP4003 в фиксированном AVC852×480/30fps с первогоprepare. Отprepare162.089s, окноdecoder160.712s,4796rendered/61dropped/7skipped; softwareOMX.google.h264.decoder иSoftAVCDecFatal0xffffffff/ACodec0x80001019. Диагностический10minтест завершилсяFAIL1/1 раньше срока, cleanupвыполнен. Исходники `tools/diagnostics/MinimalDecoderComparisonTest.kt`, evidence `beta6-minimal-comparator-{numeric,instrumentation,codec}.log`. Оболочка приложения не необходима для ошибки; причина сочетанияисточника/Media3/platform остаётсянеустановленной. Это отдельный процесс наобщемhost с параллельнымlive и не benchmark APK.

## Beta.5 — предварительный кандидат, не финальный APK

2026-11-08, exact signed/minified code7, SHA-256 `68a655937e417d763c3b18c57ddb9a4d1baaf30628217160804fafffe01aa0fb`, 2 733 965 байт. Primary `Twitch20Test`/5560: API30x86,4vCPU/2GB,SwiftShader; тот же общий Windows host, второй AVD включён и использован для подготовки OTA. Во время этой выборки Gradle/R8 не запускался. Это не физический TV/аппаратный decoder benchmark.

### Повторные открытия

23 разных выбранных канала, 24 попытки: 22 старта с первого раза, один runtime decoder4003 до первого кадра, восстановленный ручным Retry. Все 23 завершились возвратом к выбранной карточке. Обычный live Auto, без искусственного GC; память измерена через две секунды после возврата. Источники: `evidence/beta5-cycles/`, `beta5-cycles-continued/`, `beta5-cycle6-retry-numeric.log`, `beta5-cycles-summary.json`.

| Метрика | Факт |
|---|---:|
| Успешные первые старты в TTFF-выборке | 22 |
| Request→first frame min / median / p95 / max | 2067 / 2793 / 4194 / 8559 ms |
| Отдельный Retry request / prepare→frame | 2172 / 1931 ms |
| PSS после возврата, первый / последний | 73 200 / 92 716 KB |
| PSS после возврата, min / max | 73 200 / 94 088 KB |

Request timer начинается перед разрешением источника в Viewing, поэтому включает resolver+prepare; это всё ещё не аппаратный input-to-photon. p95 — nearest-rank среди22 успешных первых попыток. Неуспешный старт не скрыт в статистике и не заменён успешнымRetry. Разные внешние потоки и Auto не образуют сравнение одного качества. PSS менялся немонотонно; рост относительно первой выборки не доказывает утечку, а возврат к меньшим значениям не доказывает её отсутствие. Кэши каталога также прогреваются.

Обработанная ошибка на шестом канале: software `OMX.google.h264.decoder`, rendered0, code4003; UI error→Retry→firstframe→Back. После восстановления заметны dropped frames; исправление логики восстановления не является доказательством плавности этого источника на эмуляторе.

### Непрерывный live

Первый прогон720p60 прерван runtime4003 через319.161s(renderer):19075rendered/0dropped/236skipped. SoftAVCDec FatalError0xffffffff; причина не установлена. Отдельно упалUiAutomator shell с alreadyregistered. Приложение и codecservices живы; ни OOM, ни причинная связь с диагностикой не доказаны. СнимкиPSS до ошибки99340…107254KB. Повторный720p60 прогон без периодическогоUiAutomation также прервался runtime4003: renderer1425.246s,83631rendered/0dropped/220skipped, одна rebuffer-пауза1190ms. Первый имел rebuffer1328ms. Ни один не засчитывается за успешный30-минутныйпрогон. В текущий APK добавлен доступный выбор качества из ошибки и сохранение ручного выбора при Retry; это не объявляет системныйdecoder исправленным.

## Историческая выборка alpha.2 / beta.2

## Что измерено

На одном 19,401-секундном фрагменте стабильного live с реальным IRC чатом не найден длинный main-thread `doFrame`: p95 5,225 мс, максимум 9,102 мс. Это ограниченное положительное наблюдение, **не доказательство отсутствия jank, не FPS видео и не результат физического TV**.

Анализ выполнен по workflow `android-profiler`: проверка целостности → scheduler/system triage → main/RenderThread slices и состояния → ограничения доказательств. Метки: **[SQL]** — измерено в trace; **[LOG]** — отдельный файл/наблюдение оператора; **[INFERRED]** — интерпретация; **[GAP]** — данных нет.

## Артефакт и условия

- [LOG] Root записал уже запущенный live **720p60 + chat**, без управления пультом во время записи. Версия — установленный signed/minified **0.1.0-alpha.2**; staged APK SHA-256 `f55fd979bdda85ff77aa678df0f37212a82b20ef1a55c7776956c94882e57ed7` ([checksum](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-alpha.2/SHA256SUMS.txt)). Это не подтверждает будущий beta APK.
- [SQL/LOG] Android TV emulator API 30, x86, 4 vCPU; fingerprint `google/sdk_google_atv_x86/generic_x86:11/RTU1.221129.002/9351025:user/dev-keys`. AVD `Twitch20Test`: 2048 MB, 1280×720, GPU `swiftshader_indirect`. Аппаратный декодер, физическая GPU и реальные ARM TV здесь не измерялись.
- [LOG] Запись: `perfetto -t 20s -b 32mb --app com.oprawa.twitch20 sched gfx wm view`; record log (`evidence/perfetto-record.log`, локально). Файл live-chat.perfetto-trace (`evidence/live-chat.perfetto-trace`, локально), **18 096 894 байт**, SHA-256 `9bccef5250495c5cb53292f05901dc228add1c36c6226ecb83f21c93662b4ca3`.
- [SQL] Реальные trace bounds: **2749354743143…2768755637143 ns**, **19,400894 с**. PID **23803**, UPID **2**, main UTID **42**, RenderThread UTID **67**. Названия/тексты IRC, токены и signed URL в результаты анализа не выгружались.
- [LOG] Во время более длинного stress-прогона на том же host выполнялась R8-сборка; примерно в 13:44 UTC был запущен второй эмулятор. Эта общая нагрузка — фактор условий stress/memory, а не установленная причина задержек. Первый trace записан раньше второго эмулятора. Это не изолированный performance lab.

## Качество записи

[SQL] **177 395 scheduler events**, 12 815 app slices. Ненулевых `stats` с severity error/data_loss либо именами overrun/drop/lost не найдено; незавершённых app slices нет. Record log содержит `Unexpected sched_switch or sched_waking format`; scheduler реально импортирован, поэтому CPU ниже рассчитан из сохранённых обычных sched events. Warning зафиксирован, а не проигнорирован; сам по себе он не доказывает потери.

[GAP] FrameTimeline: **0 событий**; CPU-frequency samples: **0**. Нет оснований считать частоту CPU максимальной, исключать thermal throttling или объявлять точный процент missed frames. Запись не включает heap profiling или отдельную метрику GPU allocation. Нулевая `Graphics` строка meminfo не означает отсутствия графической памяти.

## UI slices

[SQL] Percentile — nearest-rank по завершённым slices; длительность wall-clock, включая ожидание, **не CPU time**.

| Поток / slice | N | Среднее, мс | p95, мс | p99, мс | Максимум, мс | Более 16,667 мс |
|---|---:|---:|---:|---:|---:|---:|
| Main `Choreographer#doFrame` | 391 | 1,707 | 5,225 | 6,569 | 9,102 | 0 |
| RenderThread `DrawFrame` | 201 | 4,893 | 8,176 | 11,745 | 15,474 | 0 |

[SQL] Три самых длинных main frames находятся на **18,221531 с / 9,102 мс**, **13,305537 с / 7,339 мс**, **14,287679 с / 6,886 мс** от начала trace. В самом длинном main frame: Running **0,891 мс**, Runnable **0,088 мс**, Sleeping **8,123 мс**.

[INFERRED] Этот максимум преимущественно содержит ожидание, а не непрерывную загрузку main CPU. По текущей записи нет основания объявлять app-main вычислительный bottleneck. Причина конкретного сна и полный путь до presentation не установлены.

[GAP] `doFrame` и `DrawFrame` — разные стадии; их отдельные максимумы нельзя трактовать как полный frame deadline. 391/201 slices не равны числу показанных video frames: SurfaceView/MediaCodec выводит видео независимо от частоты Compose/chat redraw. Нет доказательства «0 dropped frames» или «стабильные 60 FPS». Input latency также **не измерена**: запись стабильного просмотра не содержит сценария ввода; категории `input` нет. Нет измерения click-to-photon.

## CPU и ожидания

[SQL] Сумма Running по app-потокам: **5345,447 мс CPU за 19,400894 с**, или **27,553% одного vCPU** (около **6,888% суммарной ёмкости четырёх vCPU**). Это guest scheduler accounting; он не включает весь host SwiftShader и внешние codec/system процессы.

| App thread | CPU, мс |
|---|---:|
| MediaCodec_loop (UTID 23) | 1533,955 |
| ExoPlayer:Playb | 1316,948 |
| RenderThread | 646,297 |
| Main | 430,199 |
| ExoPlayer:Loade (UTID 182) | 415,506 |
| MediaCodec_loop (UTID 2) | 394,856 |
| CodecLooper | 259,877 |

[SQL] Все non-idle guest-потоки вместе: **17,525860 CPU-секунд**, или **22,584%** четырёх наблюдаемых vCPU. Это не загрузка CPU Windows host.

[SQL] Main Runnable (`R` + `R+`) суммарно **131,255 мс**, максимум отдельного интервала **4,534 мс**; uninterruptible `D` **2,264 мс** суммарно / максимум **1,437 мс**. RenderThread Runnable **72,815 мс**, максимум **3,721 мс**; `D` **4,802 мс** / максимум **2,751 мс**. Длинные Sleeping-интервалы при отсутствии UI работы сами по себе не ANR и не jank. Начальные/конечные состояния потока могут быть неполными относительно общих bounds, поэтому суммы thread-state не используются как полное распределение всех 19,401 с.

## First frame и память

[LOG] В playback-stress.log (`evidence/playback-stress.log`, локально) наблюдаются **530 мс** у ранее открытого clip (13:37:02 UTC) и **1713 мс** у live (13:40:32 UTC). Эти значения относятся к разным типам контента и не объединяются в live-startup выборку. Таймер `PlaybackSession` начинается при `play()` и заканчивается в `onRenderedFirstFrame`; он **не включает получение signed URL** и не является временем от нажатия карточки. Надёжный p95 старта не измерен.

[LOG] Root наблюдал live в том же процессе **23803** с первого кадра **13:40:32.390 UTC** до **13:50:28 UTC** — **9 мин 55,610 с**, то есть примерно 9 мин 56 с, без округления до завершённых десяти минут. В сохранённом журнале после live first-frame нет `player_error_code` и новых first-frame записей. [GAP] В исследованном alpha.2 `onDroppedVideoFrames` обновлял StateFlow, но **не писал Log**: отсутствие строк о drops не доказывает отсутствие callback или пропусков кадров. Значение этого счётчика в конце прогона не сохранено; точные rebuffer count/duration тоже отсутствуют. В beta.2 добавлены числовые drop callbacks и итоговые `DecoderCounters` в `onVideoDisabled`; отдельный фактический beta.2 прогон приведён ниже, он не восстанавливает отсутствующую метрику alpha.2.

| Meminfo | Uptime, мс | PSS, KB | RSS, KB | Native heap alloc, KB | Activities / Views |
|---|---:|---:|---:|---:|---:|
| stress start (`evidence/memory-stress-start.log`, локально), 13:41 UTC | 2732194 | 85912 | 226932 | 39275 | 1 / 9 |
| panel snapshot (`evidence/memory-panel-5min.log`, локально), 13:44 UTC | 2901001 | 92517 | 233724 | 42558 | 1 / 9 |
| stress end (`evidence/memory-stress-end.log`, локально), 13:50 UTC | 3274342 | 94340 | 236172 | 42253 | 1 / 9 |

[LOG] Между первым и вторым снимком **168,807 с**, а не пять минут, несмотря на имя второго файла; между первым и итоговым — **542,148 с**. PSS вырос на **8428 KB (+9,81%)** относительно baseline; после среднего снимка — ещё **1823 KB (+1,97%)**. Native heap allocated на последнем отрезке уменьшился **42558 → 42253 KB**; Activities **1 → 1 → 1**, Views **9 → 9 → 9**, OpenSSL sockets **8 → 6 → 3**. Счётчики этих объектов не растут, но три снимка при разных UI/кэшах и фоновой нагрузке не доказывают ни утечку, ни её отсутствие. OpenSSL sockets не учитывает все сетевые/decoder ресурсы. Память после закрытия плеера и повторных циклов ещё не измерена.

## Повторные открытия и отдельный beta.2 decoder sample

[LOG] На primary alpha.2 успешно выполнено **6 циклов** реального live → decoded → Back: все `decoded`/`returned` true в repeat-playback.json (`evidence/repeat-playback.json`, локально). Meminfo по циклам 1 (`evidence/repeat-play-1.log`, локально), 2 (`evidence/repeat-play-2.log`, локально), 3 (`evidence/repeat-play-3.log`, локально), 4 (`evidence/repeat-play-4.log`, локально), 5 (`evidence/repeat-play-5.log`, локально), 6 (`evidence/repeat-play-6.log`, локально): PSS **89025 / 95254 / 94364 / 95322 / 93914 / 94206 KB**, Activities во всех **1**, OpenSSL sockets **6 / 10 / 10 / 11 / 11 / 11**. После первого цикла PSS колеблется, но нет принудительного GC/одинакового post-release idle интервала и heap dump: это не доказательство отсутствия утечек. Шесть циклов не заменяют 20+ смен каналов.

[LOG] **Отдельное измерение опубликованного beta.2/code4**, SHA-256 `c2ff1871db96b00d6a273c6068a46def6b8657e7cc08ebfb55f0c557d7e5079f`: AVD `Twitch20Release`, serial5562, API30/x86, **2 vCPU / 1,5 GB**, software GPU. Это другой процесс **22885** и другой AVD, его нельзя смешивать с памятью/CPU alpha.2 на 5560. Root наблюдал японский live, в середине открывал настройки и закрывал их одним Back.

[LOG] beta2-decoder-counters.log (`evidence/beta2-decoder-counters.log`, локально): одно renderer-enabled окно **13:55:02.494…13:59:25.446 UTC**, **262952 мс = 4 мин 22,952 с**. First frame **1985 мс** после session.play (не click-to-frame); drop callbacks **6 + 3 = 9**. Terminal `onVideoDisabled` после `ensureUpdated()`:

| Счётчик | Значение |
|---|---:|
| renderedOutputBufferCount | 14382 |
| droppedBufferCount | 9 |
| droppedInputBufferCount | 0 |
| skippedInputBufferCount | 0 |
| skippedOutputBufferCount | 50 |
| maxConsecutiveDroppedBufferCount | 4 |
| droppedToKeyframeCount | 0 |

[LOG/INFERRED] Доля late dropped среди `rendered + dropped`: **9 / (14382 + 9) = 0,06254%**, округлённо **0,063%**. В знаменатель не добавлены 50 intentional skipped outputs; `droppedInput` — подмножество `dropped`, а не дополнительное число. Это именно Media3 decoder/render-output counters данного окна, **не HWUI jank и не display FPS**. Единственный `video_enabled` и terminal `video_disabled` согласуются с отсутствием отключения renderer при открытии/закрытии панели в этом прогоне; не доказывают все возможные quality/lifecycle переходы.

[GAP] На старте UI показывал **Auto / 160p**; поздние ABR изменения не журналировались. Нельзя утверждать постоянное разрешение, 720p60 либо 1080p60 в этом beta sample. Наблюдение длится 4 мин 22,952 с, не пять/десять минут. Alpha.2 baseline длительностью 9 мин 56 с остаётся самостоятельным более длинным наблюдением без decoder counters. Zero drops не заявляются: фактически зарегистрировано девять.

## Отдельный каталог: scroll и ввод

[LOG] Root выполнил последовательность 10 Down + 10 Up через Android shell input; весь сценарий занял дольше 20-секундной записи. Не все команды попали в trace. Начальный XML (`evidence/android/scroll-start.xml`, локально) показывает фокус карточки `buster`; конечный XML (`evidence/android/scroll-end.xml`, локально) — фокус rail «Эфиры» после достижения границы сетки. Это реальное движение по каталогу, но не доказательство попадания всех двадцати нажатий в измеренный участок.

[SQL] catalog-scroll.perfetto-trace (`evidence/catalog-scroll.perfetto-trace`, локально): **2 035 799 байт**, SHA-256 `aaaa46cee666d2a2fce0ae6b96976a4a3d7f751e5fe8a8f075f101c3411683cf`. Bounds **3381067333243…3400986414443 ns**, длительность **19,9190812 с**. Категории `sched gfx wm view input` (record log (`evidence/perfetto-scroll-record.log`, локально)); PID 23803, UPID 46, main UTID 84, RenderThread UTID 141. **21 416 scheduler events**, ненулевых ошибок/потерь в проверенных stats нет, FrameTimeline по-прежнему отсутствует.

[SQL] Внутри записи обнаружены **14 app `deliverInputEvent`** с **7 уникальными `eventTimeNano`**. Первая доставка на **16,782945 с**, последняя на **19,855349 с** от начала. [INFERRED] Парные временные метки согласуются с семью DOWN/UP парами shell-ввода; конкретные keyCode/action из этих slice labels не установлены. Таким образом, активный ввод занимает лишь последние примерно три секунды trace; исходную оценку оператора «начал примерно через 10 секунд» нельзя использовать вместо timestamps.

| Scroll UI slices [SQL] | N | Среднее, мс | p95, мс | p99, мс | Максимум, мс |
|---|---:|---:|---:|---:|---:|
| Main `Choreographer#doFrame` | 119 | 2,295 | 6,314 | 7,263 | 11,538 |
| RenderThread `DrawFrame` | 105 | 6,006 | 9,804 | 10,986 | 11,036 |

[SQL] App CPU **1027,690 мс** / **5,159% одного vCPU** за всё окно; main **193,652 мс**, RenderThread **464,504 мс**. Среднее разбавлено длинным участком до ввода и не является нагрузкой непрерывной прокрутки.

| Input timing [SQL], 14 событий | Минимум, мс | Среднее, мс | p95 / максимум, мс |
|---|---:|---:|---:|
| `eventTimeNano` → app delivery | 2,054 | 4,026 | 7,084 |
| InputDispatcher prepare → конец app `ViewPostImeInputStage`, совпадающий event ID | 0,482 | 0,870 | 1,395 |
| App delivery → конец первого следующего `doFrame`, только временное соседство | 5,803 | 12,199 | 17,900 |

[GAP] Это **не input-to-photon**: shell-синтезированные timestamps и два события одного нажатия, малая выборка; ни физический пульт, ни display scanout не измерены. Последняя строка не устанавливает причинную связь: следующий `doFrame` мог обслуживать другую работу. Она не доказывает, что именно в нём визуально отобразилось изменение фокуса. Для первой строки clock-domain сопоставление ограничено этой непрерывной записью без suspend.

[LOG] Независимый gfx-scroll.log (`evidence/gfx-scroll.log`, локально), после сброса gfxinfo: **131 rendered frame**, **0 janky (0,00%)** по классификатору Android HWUI; p50 **8 мс**, p90 **10 мс**, p95 **13 мс**, p99 **15 мс**, missed-vsync/high-input-latency counters **0**. Stats since **3380558968343 ns** до gfxinfo uptime **3408003 ms** дают около **27,444 с** — это шире Perfetto, поэтому 131 не обязано совпадать с 119/105 slices. gfx-scroll-reset.log (`evidence/gfx-scroll-reset.log`, локально) содержит статистику **до сброса** за старое окно и не смешивается с результатом сценария. GPU memory в gfxinfo **11,60 MB** — оценка этого emulator HWUI context, не всей физической графической памяти.

[INFERRED] В наблюдённом каталоге короткая прокрутка и фокус не показали jank по отдельному HWUI sample; нельзя переносить 0% на весь app, video surfaces, startup, все двадцать событий внутри trace или реальные TV.

Агрегаты slices/CPU воспроизводятся общим perfetto-analysis.sql (`evidence/perfetto-analysis.sql`, локально) на scroll trace; сохранены вывод (`evidence/perfetto-scroll-analysis.log`, локально), отдельный input SQL (`evidence/perfetto-scroll-input.sql`, локально) и input output (`evidence/perfetto-scroll-input.log`, локально).

## Воспроизведение анализа и дальнейшие проверки

Использован официальный [Perfetto Trace Processor](https://perfetto.dev/docs/analysis/trace-processor) **v59.1-1e9c61e98**, Windows x64, полученный через manifest `https://get.perfetto.dev/trace_processor`. SHA-256 бинарника проверен: `3acc5aca1c764541033a82ba3bb0a47524b5c3c0aa194eea8d9a7b0cdb346bdc`. Бинарник расположен в `.tools/perfetto/trace_processor_shell.exe`. Официальный [CLI reference](https://perfetto.dev/docs/reference/trace-processor-cli).

Команда из корня проекта, без устройства и сборки:

```powershell
& '.tools/perfetto/trace_processor_shell.exe' query -f 'evidence/perfetto-analysis.sql' 'evidence/live-chat.perfetto-trace'
& '.tools/perfetto/trace_processor_shell.exe' query -f 'evidence/perfetto-analysis.sql' 'evidence/catalog-scroll.perfetto-trace'
& '.tools/perfetto/trace_processor_shell.exe' query -f 'evidence/perfetto-scroll-input.sql' 'evidence/catalog-scroll.perfetto-trace'
```

Сохранены SQL (`evidence/perfetto-analysis.sql`, локально) и агрегированный вывод (`evidence/perfetto-analysis.log`, локально). Воспроизводимый вывод относится только к этому trace.

Дополнительное измерительное покрытие: более длинная прокрутка целиком внутри trace, frame timing переходов/панелей, многочасовой live и 20+ смен каналов, память после GC при одинаковом сценарии, startup-серия с resolver временем, dropped frames при фиксированных высоких разрешениях и rebuffer duration, физический ARM TV. Основные функциональные Home/resume и VOD seek отдельно проверены native QA; это не новые performance measurements. На API 31+ FrameTimeline нужен для сопоставления actual/expected frame deadlines; на API 30 нельзя подменять его только длительностью одного slice. Эти проверки не объявлены выполненными первым рабочим APK или данными короткими trace.




## Применимость к финальному beta.3

Финальный опубликованный beta.3/code5 имеет SHA-256 `9b5fc5fc7b836a60b1bb0971cedd0cf3e5e491d298138a72dfb8e3ee8c1cf844`. Он добавляет launcher category и permission guidance; плеер относительно измеренного beta.2 не изменён. **Нового performance benchmark beta.3 не было**, поэтому цифры выше сохраняют свои alpha.2/beta.2 подписи. Реальные OTA2→4→5, установленные hashes, Open и сохранение данных отдельно подтверждены в TEST_RESULTS.md; это functional evidence, не новый FPS/CPU benchmark.

Representative VOD seek/Home/возврат, quality720p60, поиск и финальный theater подтверждены native QA/XML в TEST_RESULTS.md. Дальнейшее измерение их frame timing, многочасовой нагрузки и physical ARM остаётся расширением покрытия. Raw traces/SQL/log/PNG/XML находятся только в локальном `evidence/`, исключённом из Git; ссылки на эти файлы не являются публичными артефактами.
