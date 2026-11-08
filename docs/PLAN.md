# План Twitch 2.0

## Текущий этап — завершение внутренней части перед дизайном

Запрос пользователя: закрыть внутреннюю часть первой полноценной версии; визуальную переработку выполнить затем. Базовые требования — TASK.txt§4,6,7,11,12; текущая база beta.4/code6. Технические решения/агенты/публикация делегированы. Используем существующие Kotlin/Compose/Media3/OkHttp, новый сервер не нужен. Сохраняем package/certificate, данные аккаунта и отдельный канал GitHub. Один план в этом файле; root управляет Gradle/эмуляторами/подписью/выпуском.

Подход: исправить подтверждённые дефекты в существующих владельцах состояния, без переписывания архитектуры. Альтернативы — расширение optional-функций и смена стека — не закрывают найденные lifecycle/network дефекты. Достаточность этапа: все обязательные функции доступны, regression suite проходит, точный signed APK выдерживает расширенные реальные сценарии; внешние ограничения и недоступная physical ARM проверка перечислены явно.

- [x] Независимый аудит auth/update/storage, playback/lifecycle и chat/catalog. Выявлены P2 ниже; новых подтверждённых дефектов update signature/provider/backup не найдено.
- [x] Авторизация (`auth/AuthLifecycle.kt`, `TwitchAuth.kt`, auth tests; backend_review): после DCF-save+ошибки/отмены validate validator не должен ждать час. RED тест saved-session появился при уже спящем healthy/empty loop; затем минутный wake + hourly healthy validation + учёт loggedIn=false. Не отзывать реальную сессию/не просить нового входа.
- [x] Воспроизведение (`PlaybackSession.kt`, `PlaybackPolicy.kt`, `PlayerScreen.kt`, playback tests; pagination_review): очистка stale error после Home/prepare; playWhenReady как intent для pause во время buffering; `positionToSave(position,duration,ended)` очищает завершённую историю. Root подключает policy в Viewing.
- [x] Качества клипов (`Models.kt`, `TwitchRepository.kt`, `ClipSources.kt`, parsing tests; root; playback selection — pagination_review): все HTTPS MP4 варианты из настоящего Twitch; ручное переключение сохраняет позицию и pause intent, UI не обещает ABR для MP4. Tests sort/filter/signing/redaction + selection policy, затем реальный clip/качество/перемотка.
- [x] Чат (`ChatClient.kt`, `ChatTimeline.kt`, `ChatPanel.kt`, chat tests; catalog_api): login/user-id отдельно от display-name, корректный CLEARCHAT, реальные USERNOTICE тексты, terminal NOTICE и честные connect/quiet/error статусы. Root включает socket только для live + chatEnabled + foreground, передаёт status. Tests moderation/bounds/dedup/status и native toggle/reconnect.
- [x] Каталог (`HelixCatalogRepository.kt`, `MainActivity.kt`, `CatalogScreen.kt`, data tests; root): signed-in header не зависит от guest GQL; явное обновление первой страницы до exhaustion. Проверить обновление и возврат, отмена/ошибки сохраняют понятный путь.
- [x] Метрики (`PlaybackMetrics.kt` и session callbacks; pagination_review; request timestamp — root): numeric-only resolver/request→first frame, rebuffer count/duration отдельно от startup/seek/Home. Fake-clock regression + никаких URL/токенов/текста чата в логах.
- [x] Интеграция: meaningful RED→GREEN, полный JVM + Android D-pad suite; независимый итоговый review diff. Дополнительные сценарии auth expiry/401/refresh/cancel/logout проверять через фикстуры отдельно от реального аккаунта.
- [ ] Точный release APK: 20+ live open/return с памятью после закрытия, длительный live минимум30мин с чатом/панелями, сеть→retry, pause/foreground, VOD seek/resume/completion, clip quality, settings persistence. Измерять actual durations/decoder counters, не путать emulator иphysicalTV.
- [ ] Выпуск beta.5/code7 с постоянной подписью, публичные hashes/канал, реальный OTA6→7 с настройками и VOD history. README/HANDOFF/TEST_RESULTS/SCOPE/отчёт покрытия обновить по фактическим результатам. Дизайн не объявлять завершённым.

### Дополнение по фактическому soak

720p60 на software AVC эмулятора дважды прервалось runtime4003:319.161s и1425.246s. Второй случай повторился без периодическогоUIAutomation, поэтому нельзя списывать decoderfailure на инструмент. Непрерывный30minPASS пока отсутствует. Новый объём стабилизации:

- [x] Session/policy/tests (pagination_review): ручной выбор качества изError запускает восстановление и сохраняет pause/position; freshresolverRetry SAMEmedia сохраняет семантический выбор без устаревших trackindexes; newmedia/Auto/unavailablequality имеют явное поведение. RED→GREEN.
- [x] RootUI: доступное «Качество» на экране ошибки, управляемое D-pad; выбор480p после реальной сетевой ошибки и Retry с сохранением выбора. Native pause-buffering helper исправлен: live «К эфиру», а не VOD «Смотреть»; прежний timingassert не считается PASS. Current121JVM/8Android84.861с; настоящийerror→Quality360→video и Retry→360PASS. Дополнительно actualMedia3 RED2/1→GREEN закрывает background source completion intent.
- [ ] Новый exact candidate/code7 (старыйещёнезагруженвdist/Release), полный JVM, targetednative и длительный480p прогон. Старые720p failures остаются известным ограничением softwaredecoder; не объявлять их исправленными сменой качества.

Review focus: logout во время refresh; отмена после сохранения DCF; paused buffering→Home; завершение короткого clip; скрытый/переподключающийся чат; смена MP4 источника без сброса позиции; перезагрузка каталога с удалённой выбранной карточкой; отказ внешнего GQL при здоровом Helix.

## Завершённый этап — внутренняя часть beta.4

2026-11-08: пользователь проверил приложение и сообщил, что основные функции работают; визуальную доработку отложил. Продолжаем каталог/сеть, без изменения визуального оформления. Технические решения и выполнение делегированы ранее.

Подход: курсорные страницы из изолированного TwitchRepository; отдельный CatalogPager хранит список, cursor и ошибку. Подгрузка при приближении к концу сетки; ошибка следующей страницы не стирает карточки и допускает явный повтор. Ограниченный кэш последних разделов сохраняет список/позицию при возврате. Полная предварительная загрузка отвергнута из-за задержки, памяти и лишних запросов; новый сервер не нужен.

- [x] Реальные guest cursor probes: все пять продолжений блокируются IntegrityCheckFailed. Решение — официальный Helix после входа, guest ограничение явно показано. CatalogPage<T> и HelixCatalogRepository проверены 11 тестами; TwitchRepository не изменён. См. research-pagination.md.
- [x] Root: CatalogPager<T> со StateFlow, ID-dedup, одним запросом, поколениями/cancel, retry, пределом памяти и ограниченным пропуском пустых страниц. 9 тестов прошли после наблюдённого RED.
- [x] Root: подключены страницы и LRU из трёх каталогов по 1000 элементов. После review исправлены metadata канала при refresh и восстановление фокуса через revision/key(pager). Android-проверки ниже.
- [x] backend_review нашёл и исправил две доказанные auth ошибки: offline startup теперь повторяет validate через 60с; refresh→save переживает отмену экрана с пределом 30с и guard от logout. 10 новых auth/Helix access тестов прошли. Новых подтверждённых ошибок чата не найдено.
- [x] 72 JVM PASS; 6 Android D-pad PASS/79.751с, включая переход за первую страницу и возврат после канала. Отдельный native refresh PASS. Точный release: offline startup→авторизация через59с, сохранение первой страницы при потере сети→retry→продолжение. Подписанный beta.4/code6 опубликован; реальный OTA5→6 на5562 сохранил настройки; public/feed/installed hash совпадают. Подробности и ограничения в BACKEND_BETA4.md.

Контроль review: смена раздела во время загрузки; одинаковые ID между страницами; невалидный/зацикленный cursor; отсутствие сети после первой страницы; фокус после добавления карточек. Дизайн остаётся отдельным будущим этапом, beta.3 не является финальным визуальным решением.

## Доставленная beta.3

2026-11-08: самостоятельный Android TV beta доставлен — исходники и signed beta.3 опубликованы, реальные OTA 2→4→5 выполнены через приложение и Android installer с сохранением данных. Это результат beta-объёма, а не обещание проверки всех устройств и условий. [Результаты](TEST_RESULTS.md), [измерения](PERFORMANCE.md), [релизы](RELEASING.md), [объём](SCOPE.md).

## Решения

- Новый независимый проект/package `com.oprawa.twitch20`, отдельный [репозиторий](https://github.com/oprawa228-hub/twitch-tv-2.0); старый проект/канал не изменены.
- AGP8.13.2, Gradle8.13, JDK17, Kotlin/Compose2.3.10, SDK36/min23, BOM2026.06.01, TV1.1.0, Media3 1.11.1, **OkHttp4.12.1**, Coil3.4.0. Отказ от OkHttp5.5.1 подтверждён SDK37 metadata failure.
- Один PlaybackSession на просмотр; панели не создают player. Settings/history — SharedPreferences; OAuth — Android Keystore AES-GCM/AtomicFile. Гостевой GQL/Usher недокументирован; DCF/Helix отдельные официальные интеграции.
- Пользователь разрешил самостоятельные технические решения/публикацию. После успешного входа oprawa попросил пропустить дальнейшую проверку аккаунта; повторного входа не требовать.

## Выполнено

- [x] Исследование/визуальные правила/независимая реализация/воспроизводимая сборка/лицензии/рабочие инструкции.
- [x] Каталог, категории, поиск, канал, длинный OK и возврат фокуса/строки — представительные native и D-pad проверки.
- [x] Реальные live+IRC, clip и VOD, выбранное720p60 качество, VOD seek и Home/возврат, история/позиция после OTA.
- [x] Overlay/theater, реальные settings previews, draft Cancel/Save, вложенные настройки, один Back из панели. Основные сценарии подтверждены; это не exhaustive matrix.
- [x] Реальный OAuth oprawa, followed live; аккаунт и18sp сохраняются через OTA.
- [x] TLS UI cleanup crash и двойной Back исправлены/retested; network error2001→restore→Retry→кадр.
- [x] Шесть play/return циклов, live9мин56с, память, stable-live/scroll Perfetto, beta.2 decoder sample4мин22.952с. Ограничения software emulator указаны.
- [x] 42 unit PASS,5 diagnostic Android PASS за60.562с; финальные release build/signature/native smoke проверены отдельно.
- [x] Публикации alpha.2/code2, beta.2/code4, финального [beta.3/code5](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.3).
- [x] Настоящие in-app check/download/cancel/retry/permission/installer; OTA2→4→5. Installed/public/dist beta.3 APK совпадают по SHA/размеру. Системный Open одним OK открывает каталог.
- [x] README/RELEASING/SKILLS и итоговые документы подготовлены; release-tag source commit `13545b8c14942af0529be204d85c91b24a9f3fee`, финальные документы публикуются последующим commit.

## Итог и ограничения beta

Финальный APK: [Twitch20-0.1.0-beta.3.apk](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.3/Twitch20-0.1.0-beta.3.apk), code5, SHA-256 `9b5fc5fc7b836a60b1bb0971cedd0cf3e5e491d298138a72dfb8e3ee8c1cf844`. Позиция VOD1:28→1:46 подтверждена при2→4; нового history check после5 не заявляем. Beta.3 меняет только launcher category и permission guidance; полного повторного42/5debug run после этого не было.

Будущее расширение покрытия: физический ARM TV/аппаратный decoder/1080p60, многочасовое видео,20+ смен каналов, длительный busy chat, token expiry/refresh/revoke и больше сочетаний quality/seek/lifecycle. Эти ограничения не оставляют уже выполненные основные этапы «в процессе». Историческое ограничение beta.3: общий live30, категории40, VOD/clips30. В beta.4+ после входа действует курсорная пагинация; guest остаётся ограниченным первой страницей. Subscriber-only/Turbo не гарантируются.

Raw XML/PNG/log/trace в `evidence/` и installed APK в `.local/` — локальные доказательства, исключённые из Git. Публичные артефакты доступны через GitHub Releases, не по локальным dist-ссылкам.
