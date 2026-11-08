# Платформа Twitch 2.0: выбор и проверяемые гипотезы

Проверено 2026-11-08. Исследование независимое от реализации старого клиента. Статус: выбран кандидат стека, опубликованные артефакты и требования совместимости проверены; компиляция, APK и измерения принадлежат следующему этапу и этим документом не подтверждаются.

## Решение

Kotlin, один Activity, Compose for TV, Media3 ExoPlayer с HLS, SurfaceView через `media3-ui-compose`, OkHttp, Coil, DataStore. UI и декодер не связаны с механизмом получения Twitch URL. Отдельный владелец playback session переживает открытие чата и панелей. Приложение не требует собственного сервера для декодирования и UI; необходимость сервера для API/авторизации определяется отдельным исследованием Twitch.

Начальный диапазон — Android TV API 23+, compile/target API 36. Это реальный минимум выбранных AAR, не устаревшее обещание API 21 из общих статей Compose TV. APK без собственных `.so` не ограничен ARM: ARMv7/ARM64 для телевизоров и x86_64 для эмулятора. Аппаратные кодеки предоставляет устройство. Поддержка API не доказывает производительность старого ТВ или наличие конкретного аппаратного декодера.

## Сравнение вариантов UI

| Вариант | Пульт, дизайн и состояние | Память, видео, сопровождение | Решение |
|---|---|---|---|
| Kotlin + Compose for TV | TV Material даёт фокус и действия пульта; собственная геометрия карточек/панелей; единые state-driven previews | Нужны стабильные ключи, контроль recomposition и release-профилирование; Media3 имеет Compose surface API | Выбран: позволяет одним компонентом рисовать настоящую настройку и её пример |
| Kotlin + Views/RecyclerView | Зрелый D-pad; явные nextFocus и view state; удобен для точечного surface interop | Предсказуемые recycling/lifecycle, но больше ручной синхронизации animated previews, adapters и UI state | Резерв при воспроизводимой проблеме Compose; не переписывать всё заранее |
| Leanback fragments | Готовые TV шаблоны | Ограничивают необычные панели/авторские карточки; новый UI проще строить на Compose | Не использовать как основу нового приложения |
| WebView + HTML/JS, native video bridge | Требует своего spatial navigation, перевода remote keys, двух деревьев состояния | Web runtime и JS bridge; различия WebView на телевизорах; focus/video lifecycle усложняются | Не выбран для основного UI; возможен отдельный официальный web-player fallback, если он реально работает на ТВ |

Это инженерная оценка применимости, а не измеренный рейтинг быстродействия. Google рекомендует TV Material вместо мобильного Material и предупреждает о смешении тем. TV lazy layouts уже deprecated: используем `LazyVerticalGrid`, `LazyRow` из обычного Compose Foundation, не `TvLazy*`. Источники: [Compose TV](https://developer.android.com/training/tv/playback/compose), [списки на TV](https://developer.android.com/training/tv/playback/compose/lists), [рекомендации перехода от Leanback](https://developer.android.com/training/tv/playback/leanback/migrate-to-compose).

## Закреплённый кандидат зависимостей

Все версии ниже фиксированные, без `+`, snapshot, alpha или beta. Более новый stable не автоматически лучше для этой связки. Полная фактическая транзитивная версия и лицензии должны попасть в dependency/notice inventory после первой сборки.

| Компонент | Версия / лицензия | Назначение и основание |
|---|---|---|
| AGP | 8.13.2 / Apache-2.0 | Совместим с имеющимися Gradle 8.13 и JDK 17; R8 поддерживает Kotlin 2.3; API до 36.1. [Release](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| Gradle / JDK | 8.13 / Apache-2.0; JDK 17 / лицензия конкретного дистрибутива | Wrapper фиксирует версию; для runtime не нужен JDK на телевизоре. Лицензию поставщика JDK сохранять отдельно от лицензий APK |
| Kotlin + Compose compiler plugin | 2.3.10 / Apache-2.0 | KGP 2.3.10 поддерживает Gradle 7.6.3–9.0 и AGP 8.2.2–9.0. Compiler plugin той же версии. [Kotlin matrix](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| Compose BOM | 2026.06.01 / Apache-2.0 | UI/Foundation/Runtime/Animation 1.11.4. Последний прочитанный BOM 2026.09.00 ведёт на поколение Compose 1.12, требующее SDK 37/AGP 9; это пока не нужно. [BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [точный POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.06.01/compose-bom-2026.06.01.pom) |
| Compose TV Material | 1.1.0 / Apache-2.0 | Stable, TV focus/card/button. Не добавлять tv-foundation ради deprecated lazy layouts. [Release](https://developer.android.com/jetpack/androidx/releases/tv) |
| Activity Compose / Lifecycle | 1.12.4 / 2.10.0, Apache-2.0 | ComponentActivity, ViewModel, SavedStateHandle, lifecycle-aware collection. На дату проверки более новые stable 1.13.0/2.11.0 существуют; выбран согласованный SDK36 baseline. [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| Media3 | 1.11.1 / Apache-2.0 | `exoplayer`, `exoplayer-hls`, `datasource-okhttp`, `ui-compose`, `session`; один номер для всех модулей. Stable 10.09.2026; 1.12 alpha не брать. [Release](https://developer.android.com/jetpack/androidx/releases/media3) |
| OkHttp | 4.12.1 / Apache-2.0 | HTTP/API, WebSocket, загрузки APK. Свежий security backport 22.10.2026; совместим с SDK36 и прямыми требованиями Coil3.4/Media3 1.11.1 (оба требуют4.12.0). Первоначальный5.5.1 отклонён реальным checkAarMetadata: Android artifact требует SDK37. 5.4.0 поддерживает SDK36, но не содержит этого security backport. [Changelog4.x](https://raw.githubusercontent.com/square/okhttp/master/docs/changelogs/changelog_4x.md), [POM](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.1/okhttp-4.12.1.pom) |
| Coil Compose + network-okhttp | 3.4.0 / Apache-2.0 | Ограниченные memory/disk caches, decode под размер карточки, cancellation при уходе. 3.6.3 существует, но поколение 3.6 использует SDK37/Compose1.12/Kotlin2.4.10; 3.4 остаётся согласованным кандидатом для SDK36. Не включать experimental request deduplication. [Changelog](https://coil-kt.github.io/coil/changelog/) |
| Kotlin coroutines | 1.10.2 / Apache-2.0 | Structured cancellation, StateFlow; совместим по Kotlin metadata. [POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.10.2/kotlinx-coroutines-android-1.10.2.pom) |
| Preferences DataStore | 1.2.1 / Apache-2.0 | Атомарные асинхронные настройки, небольшая ограниченная история; не база для бесконечного каталога/чата. [Release](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Room | Отложен; current stable 2.8.5 / Apache-2.0 | Добавить, когда история требует запросов/индексов/миграций; не вводить KSP ради нескольких preferences. [Release](https://developer.android.com/jetpack/androidx/releases/room) |
| Анимация | Compose Animation из BOM | Focus/size/position/alpha; один renderer для реальной настройки и preview. Lottie не требуется для геометрии, GIF не годится как доказательство поведения |

Минимальная навигация: явный набор маршрутов, небольшой back stack и состояние экрана во ViewModel/SavedStateHandle; IDs и scroll anchor сохраняются, сами View/FocusRequester — нет. Для роста до нескольких независимых back stacks пересмотреть Navigation3. Navigation 2 больше не перспективный выбор: [официальная страница](https://developer.android.com/jetpack/androidx/releases/navigation) помечает maintenance mode. Не вводить Hilt, KMP, собственный DI framework или десятки Gradle-модулей без конкретного потребителя.

### Проверка опубликованных файлов

PowerShell HTTP GET скачал AAR только в память и прочёл `META-INF/com/android/build/gradle/aar-metadata.properties`, затем `AndroidManifest.xml`. Файлы SDK, старого проекта и устройства не менялись.

| AAR | minCompileSdk | minAGP | manifest minSdk |
|---|---:|---:|---:|
| ui-android 1.11.4 | 35 | 8.6.0 | 23 |
| tv-material 1.1.0 | 35 | 8.6.0 | 23 |
| media3-exoplayer 1.11.1 | 36 | 1.0.0 | 23 |
| media3-ui-compose 1.11.1 | 36 | 1.0.0 | не извлекался |
| activity-compose 1.12.4 | 36 | 8.9.1 | не извлекался |
| lifecycle-runtime-android 2.10.0 | 34 | 8.1.1 | не извлекался |
| datastore-preferences-android 1.2.1 | не извлекался | не извлекался | 23 |

Прямые URL имеют форму `https://dl.google.com/dl/android/maven2/{group/path}/{artifact}/{version}/{artifact}-{version}.aar`. POM BOM действительно содержит 1.11.4; POM Coil3.4 содержит Kotlin2.3.10; OkHttp4.12.1 содержит Kotlin1.8.21 и Okio3.6.0 (Gradle разрешит более новый Okio от Coil); DataStore1.2.1 содержит Kotlin2.0.21. Это проверка опубликованной совместимости, не замена `checkAarMetadata`, resolve dependencies, R8 и запуска APK. SDK36 нужно установить в среду нового проекта: имеющиеся у главного агента platforms34/35 недостаточны для Media3 1.11.1.

AGP9.1.1/Gradle9.3.1/JDK17 также официальный совместимый вариант, но для выбранного SDK36 не решает пользовательскую задачу лучше. [AGP9.1 matrix](https://developer.android.com/build/releases/agp-9-1-0-release-notes). Не смешивать AGP9 built-in Kotlin с `org.jetbrains.kotlin.android`; выбранная AGP8 сборка использует обычный Kotlin Android plugin.

## Плеер и lifecycle

Media3 выбран вместо libVLC: нативная интеграция Android MediaCodec, track selection, HLS/live timeline, события аналитики и MediaSession дают нужные части без добавления универсального C/C++ codec runtime. libVLC 3 — поддерживаемая альтернатива с широкими форматами, но JNI/ABI и LGPL-2.1 обязанности добавляют работу; для Twitch HLS это не обоснованная выгода. [libVLC](https://images.videolan.org/vlc/libvlc.html), [Media3 formats](https://developer.android.com/media/media3/exoplayer/supported-formats). Поддержка HLS не означает получения Twitch URL: это отдельный контракт источника. Не добавлять программные FFmpeg/AV1/VP9 decoder extensions заранее.

`ContentFrame`/`PlayerSurface` с SurfaceView и собственными TV controls предпочтительнее `PlayerView` внутри AndroidView: официальная документация отмечает interop-риск растянутой/обрезанной surface на API34. SurfaceView лучше подходит для TV full-resolution output и аппаратной композиции; TextureView — адресный fallback после воспроизводимого дефекта. [Surfaces](https://developer.android.com/media/media3/ui/surface), [Compose UI Media3](https://developer.android.com/media/media3/ui/compose).

Правила проекта: один ExoPlayer на сессию, все команды на main/application looper; создание по смене playback identity, не по recomposition или открытию панели. Отмена предыдущего resolve до принятия нового результата. Отдельные состояния resolving/buffering/playing/ended/error. На уходе с playback экрана освобождать decoder/session; на фоне прекращать видео и чат, сохранять VOD position. При возвращении live — свежий live edge, VOD — сохранённая позиция. Панели не меняют media item и владельца session. Окончательный background policy проверить на APK. [Потоки и release](https://developer.android.com/media/media3/exoplayer/hello-world).

Фокус: стабильный content ID карточки плюс индекс/offset, `focusRestorer` для контейнеров, `FocusRequester` только после появления элемента в composition. Не вызывать requestFocus из тела composable. Если элемент исчез из refreshed catalogue — ближайшая карточка или начало, без невидимого фокуса. Для панели запомнить origin control, Back сначала закрывает панель и возвращает фокус. При Lazy recycling не хранить requester удалённого элемента. [Focus](https://developer.android.com/develop/ui/compose/touch-input/focus/request-focus).

## Данные и нагрузка

Настройки: persisted value отдельно от draft preview; Apply атомарно сохраняет, Back отменяет draft, renderer получает один и тот же тип настроек. Токены хранить отдельно от preferences/history: ключ AES в Android Keystore, ciphertext в приватном хранилище, исключить секреты из backup/logging. [Keystore](https://developer.android.com/privacy-and-security/keystore). Не добавлять дисковый cache HLS по умолчанию: live URLs/подписи истекают, память буфера и дисковое хранение — разные механизмы.

Общие проектные ограничения: bounded chat buffer, batch обновления UI, один видимый animated preview, загрузка изображений в отображаемый размер, отмена поисков/картинок при смене экрана, HTTP calls вне main thread. Не логировать Authorization, OAuth responses и signed playback URLs. API cache учитывает пользователя и expiry; thumbnail cache общий, но ограниченный. Конкретные MB/TTL выбрать по измерению, не выдавать предположение за оптимальный размер.

## Проверки до фиксации архитектуры

1. Сборка debug и minified release; проверить dependency tree/AAR metadata/manifest minSdk, не только Kotlin compile.
2. TV emulator API34: настоящий HLS, D-pad catalogue → player → Back к той же карточке; 20 открытий/закрытий панели без нового prepare/decoder; panel и отдельный чат сохраняют всё видео 16:9.
3. Холодный старт и scrolling измерять Macrobenchmark/Perfetto в profileable release; debug Compose не основание оценки. UI Automator проверяет remote keys и системный installer; Compose UI tests — семантику/focus/draft cancel. [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview). На дату проверки stable [Benchmark1.5.0](https://developer.android.com/jetpack/androidx/releases/benchmark), [UIAutomator2.4.1](https://developer.android.com/jetpack/androidx/releases/test-uiautomator); перед добавлением проверить их AAR metadata, совместимость ещё не проверялась.
4. AnalyticsListener: click-to-first-frame (отдельно resolve/network/decode), dropped frames, buffer durations/counts, quality switch. `dumpsys meminfo` после старта, 20 переключений каналов, долгого просмотра с чатом; число retained players/jobs не растёт. Не отправлять телеметрию наружу автоматически.
5. Network loss/recovery, stale resolve result, token expiry, Home/resume, long press OK и repeat D-pad; реальные account scenarios отдельно от fixtures. Нагрузочный fixture чата допустим только в тестовом режиме.
6. На физическом ARM TV проверить декодер, 1080p60, поверхность, звук и стабильность; x86 emulator не подтверждает эти свойства. Низкий API23/24 и API36 проверять отдельно, минимум API не означает проведённый тест.

Результаты должны сохранять устройство/API/build variant, число повторов, медиану и p95, raw trace/log путь. Критерий выбора после spike: фокус стабилен, панели не перезапускают видео, нет растущего числа session/jobs, performance укладывается в конкретный измеренный бюджет на целевом ТВ. Если нет — локально менять surface/list/rendering, затем пересматривать UI вариант по доказательству.
