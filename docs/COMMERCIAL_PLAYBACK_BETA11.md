# Playback beta.11: фактическое качество и коммерческое окно

Дата: 2026-11-09. Владелец Android-сборки и эмулятора — root. Сведения beta.10 сохранены в `COMMERCIAL_PLAYBACK_BETA10.md`; удачный повтор не отменяет неудачные запуски.

## Текущее состояние

- Исправлена ошибочная метка160p: отображается фактический renderer format; native regression прошёл.
- В реальном native запуске локализован full→short HLS при прежнемsequence: растущие ответы ошибочно доходили до стандартного Stuck раньше прежнего полного окна. Production обёртка ограниченно ждёт подтверждённого роста без нового source; положительный и отрицательный native сценарии прошли. Обычная реклама не удаляется и не считается ошибкой сама по себе.
- UI и MediaSession PLAY используют единую live-edge политику; повторный PLAY не перематывает. Native и отдельный реальный media-key сценарий прошли.
- Отдельный4003 на старом API30 OMX воспроизводится также без приложения в минимальном comparator. Автоматический decoder reprepare удалён после реальных41–45с ожидания; DEBUG disableAdaptive не предотвратил fatal и не включён в production. На отдельном Android TV36 с реально созданным C2 goldfish тот же канал прошёл примерно7мин49с наблюдения, смену720p30→720p60 и оба пути pause/resume без decoder error, restart или повторной вставки после паузы. Старый OMX этим не исправлен; физический TV не проверен.
- Позднее повторный release-запуск TV36 выявил неверный размер нового кадра и оставшиеся старые пиксели после1080→720. DEBUG контроль с пересозданием именно goldfish подтвердил полный кадр без старых пикселей; подготовлен узкий production workaround, финальная release-проверка ещё ожидается. Отсутствие exception само по себе не считалось успешным выводом.

Разделы ниже сохраняют ход исследования. Указанные в ранних разделах ожидания и промежуточные гипотезы не заменяют этот итог и последующие результаты.

## План и границы

1. Устранить подтверждённое неверное отображение качества: отделить список выбранных ABR-кандидатов от реально поступающего в видеодекодер формата.
2. В одном native запуске сопоставить байты фактического HLS ответа, результат штатного parser, primary snapshot и ошибку. Отдельный host запрос этого не доказывает.
3. Воспроизвести Riot без паузы, затем через обычные UI pause/play и повторный PLAY. Сравнить источник зависания с beta.10. Только по новым данным менять конкретный слой; не менять Client ID, User-Agent или usher-параметры повторно без основания.
4. Проверить исправление на native fixture и реальном эфире; отдельно записать неопределённости и физический TV.

## Подтверждённый дефект «160p»

`publishTracks` перебирал все `isTrackSelected` и записывал последнюю метку в `activeQuality`. В адаптивной группе одновременно выбранными могут быть несколько допустимых дорожек: последний элемент списка не является текущим качеством декодирования. Меню и ручная настройка остаются самостоятельными состояниями.

Теперь фактическая метка обновляется из `player.videoFormat`. Событие `onVideoInputFormatChanged` инициирует обновление и пишет только числовые width/height/fps. Периодическая публикация состояния также читает этот формат, поэтому ABR-переход не зависит от нового `onTracksChanged`. Неизвестный формат не заменяется предположением по последнему элементу меню. Основание: [ExoPlayer.getVideoFormat](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java) описывает формат текущего видео; [AnalyticsListener](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/AnalyticsListener.java) предоставляет изменение входного формата видеорендерера.

Native regression `adaptiveQualityLabelUsesRendererInputInsteadOfLastSelectedVariant` специально объявляет ABR варианты360p/160p, но содержит локальный90p TS. Он должен подтвердить несколько selected candidates, фактические90p в renderer и метку90p при сохранении автоматического режима. Это тест рассогласования manifest/decoder, не реального Twitch ABR.

## Диагностика одного ответа

`HlsPayloadObservation` оборачивает **тот же InputStream**, который читает штатный Media3 parser. Дополнительных HTTP запросов, изменения контента или буферизации полного playlist нет. Сохраняются только числовые агрегаты, ограниченная строка tag на время разбора и непубликуемый digest в RAM. Строки media URL не сохраняются; сырые tags, URI, токены и digest не выводятся.

- `debug_hls_loading`: начало native manifest load.
- `debug_hls_payload count=N`: размер фактически прочитанных bytes, EOF, raw sequence, число EXTINF, сумма их длительностей, target/start, число parts/dateranges/discontinuities/end-tag, boolean изменения всего payload.
- `debug_hls_parsed count=N`: результат штатного parser для тех же bytes.
- `segment_refs_changed` / `segment_timing_changed`: изменились ли список URI сегментов и их начало/длительность/discontinuity относительно предыдущего parse того же playlist. В лог попадает только boolean, сравниваемые хеши остаются в RAM.
- `debug_hls_primary_payload`: последние полученные bytes для URI текущего primary snapshot; только роль/высота и числовые поля, время с загрузки.
- `debug_hls_primary_match`: совпадает ли список сегментов/таймингов опубликованного snapshot с последним parsed snapshot для того же URI. Одиночное расхождение непосредственно во время обработки callback может быть временным; важна последовательность дальнейших наблюдений.
- `debug_hls_failure_payload`: аналогичная привязка для URI фактической Stuck/Reset ошибки.
- `hls_window`: опубликованный Media3 snapshot и позиции плеера.

`payload_changed` не равен продвижению видео: сервер может менять только служебные tags. `raw_extinf` не включает LL-HLS parts, поэтому сумму raw EXTINF нельзя автоматически приравнивать длительности playlist с parts. `payload_eof=0` означает, что прочитан не полный ответ; это наблюдение, а не новая ошибка плеера. Всё это вызывается только DEBUG-фабрикой; release parser не меняется.

Unit tests проверяют идентичность bytes на выходе, числовые агрегаты/отсутствие секретных строк, одинаковый результат разных размеров чтения и отличение неполного чтения. Результаты запуска дописывает root после сборки.

Проверено по артефактам root: diagnostic build успешен (`evidence/beta11-diagnostic-build.log`); `HlsPayloadObservationTest` —3 tests,0 failures/errors в JUnit XML. Native тест различия ABR candidates и decoder format также прошёл (`evidence/beta11-quality-native.log`,1 test). Реальный Twitch и остальная HLS/lifecycle матрица этим одним native тестом не заменяются.

## Первичные источники проверены повторно

- Текущая [release ветка Media3](https://github.com/androidx/media/blob/release/RELEASENOTES.md) на момент проверки начинает release notes с1.11.1; эта версия уже установлена. Обновление библиотеки само по себе не является найденным исправлением.
- [DefaultHlsPlaylistTracker](https://github.com/androidx/media/blob/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/DefaultHlsPlaylistTracker.java) продолжает применять критерий неподвижного окна и таймаут по target duration. Прежде чем менять его, нужны конкретные входные ответы сбойного native запуска.
- [HlsMediaPlaylist.isNewerThan](https://github.com/androidx/media/blob/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsMediaPlaylist.java) сравнивает sequence, количество сегментов/parts и появление ENDLIST. Изменения URI/длительности при прежних sequence/count не делают snapshot новым. Это конкретный механизм возможного рассогласования, пока не доказанный для сбойного Twitch ответа; именно для него добавлены boolean сравнения выше.
- [Streamlink Twitch plugin](https://github.com/streamlink/streamlink/blob/master/src/streamlink/plugins/twitch.py) отдельно распознаёт сегменты рекламы и в своём режиме фильтрации ожидает контент, продолжая работу с playlist. Это иной pipeline; его нельзя перенести в Media3 заменой одного параметра или счётчика. Режим ожидания Streamlink не доказывает, что сервер выдаёт обычное видео во время вставки. Его логирование сырых dataranges в приложение не переносится.

## Исходная неопределённость перед новыми запусками

Причина frozen native snapshot beta.10 не установлена. Однократное восстановление уже не помогло в одном реальном запуске; ещё один такой же restart не считается решением. Новая диагностика должна различить замерший HTTP ответ, изменение URI при прежних sequence/count, расхождение parser и primary, а также отсутствие запланированных reload. Никаких новых заявлений об устранении коммерческого окна до такой проверки нет.

## Первый native запуск beta.11: HLS работает, затем отдельная ошибка decoder

`evidence/beta11-riot-payload.log`, emulator clock00:10:36–00:14:53:

- HTTP bytes, parsed и primary совпадают. Во время вставки sequence0 остаётся закреплённым, но окно растёт с3 до32 сегментов (6→62.267 секунды). Reload идут каждые5 секунд.
- В00:11:42 появляется обычное окно sequence18/16 сегментов/32.032 секунды, commercial_metadata0; далее sequence движется до63. Перезапуска source и PlaylistStuckException в этом запуске нет.
- Renderer сначала480p30, затем1080p60, хотя загружаемый playlist обозначен как720p. В00:11:07 renderer переходит на720p60. Следовательно разрешение вставки также может отличаться от метки master; вывод actual video format обоснован не только fixture.
- В00:13:13.759 возникает **error4003, ExoPlaybackException→MediaCodecVideoDecoderException→IllegalStateException**. Только после этой fatal decoder ошибки прекращаются loading, а payload age растёт до100 секунд. Это не доказательство замёрзшего HTTP ответа: до ошибки источник нормально обновлялся. Природа decoder ошибки и её зависимость от эмулятора требуют отдельной проверки.
- `segment_refs_match`/`segment_timing_match` остаются1; гипотеза проигнорированного обновления при прежних sequence/count здесь не подтверждена.

Первый диагностический счётчик давал суммарно32031мс вместо parsed32032мс из-за накопленной погрешности преобразования decimal EXTINF через Double. Счётчик исправлен на decimal arithmetic и дополнен regression16×2.002с=32.032с. Это только точность диагностики; bytes/parser не менялись. Новый четвёртый unit test должен быть включён в следующий root run.

Подтверждено последующим root run: JUnit XML `HlsPayloadObservationTest` от2026-11-09T00:46:24Z содержит4 tests,0 failures,0 errors, включая decimal regression.

## Повторный запуск: исходный Stuck наконец локализован

`evidence/beta11-riot-repeat.log` и `evidence/beta11-riot-retry3.log` содержат один и тот же native запуск с повторной попыткой после decoder ошибки:

| Время эмулятора | Тот же720p media playlist, sequence0 |
|---|---|
| 00:17:30.999 | Первое HTTP тело и parser сразу содержат16 сегментов /30.235 секунды |
| 00:17:36.001 | Следующее HTTP тело содержит только5 сегментов /10 секунд |
| 00:17:38.500–00:17:56.032 | HTTP окно растёт6→15 сегментов /12→30 секунд, оставаясь меньше первого snapshot |
| 00:17:58.543 | Снова16 сегментов /30.235 секунды, всё ещё не новее первого snapshot |
| 00:18:00.975 | При исчерпании декодируемого буфера возникает PlaylistStuckException именно для720p |
| 00:18:01.984 | Единственный automatic restart начинает новый источник |
| 00:18:02.690→00:18:07.620 | Повторяется тот же откат16 сегментов/30.235с →5/10с |

Это объясняет прежнее кажущееся противоречие: master сначала загрузил1080p окно3/6с, выбранный720p уже дал полный30-секундный буфер, затем720p HTTP ответы откатились к растущему окну при том же sequence. Стандартный tracker сохраняет более полный snapshot, но не получает **более нового** snapshot до окончания вставки. Primary1080p остаётся старым, buffered около30с; это не ошибка подсчёта EXTINF и не остановка HTTP загрузки. Коэффициент Stuck3.5×target5с уже истёк, когда потребовался следующий chunk. Перезапуск в этот момент повторяет начальное состояние источника.

Подготовлен **контролируемый DEBUG эксперимент**, выключенный по умолчанию: `playback-diagnostics` → boolean `observe_stuck_60s=true` до создания PlaybackSession. Он задаёт tracker coefficient12 (при target5с —60с) и Buffering watchdog90с, не меняя tokens/parser/segments. В логе должен появиться `debug_stuck_observation enabled=1 coefficient=12 buffering_timeout_ms=90000`. Release и default DEBUG по-прежнему используют стандартные сроки. После наблюдения ключ удаляется. Это ещё не окончательная production policy.

Два дополнительных native сценария воспроизводят full32с → меньшие5/…/18 сегментов при том жеsequence: стандартный tracker должен ошибиться, controlled grace должен дождаться роста без нового master/source. Отдельный MediaController regression проверяет pause→PLAY и повторный PLAY через настоящую MediaSession: её ForwardingPlayer теперь вызывает тот же `PlaybackSession.resume/pause`, что и UI. Так ранее обходившие владельца media keys используют защиту от START0 и не создают отдельную логику возобновления.

Все три сценария прошли у root: `evidence/beta11-regression-native.log`,3/3,74.013с. Таким образом, на детерминированном HLS воспроизведены и ошибка стандартного tracker, и её отсутствие при достаточном времени наблюдения без restart.

Реальный controlled запуск `evidence/beta11-riot-grace.log` вначале показал обычный рост, поэтому сам по себе ещё не подтверждает обработку full→short на Twitch. При этом root отдельно проверил MEDIA_PAUSE8с→MEDIA_PLAY→повторный PLAY: один `live_edge_seek19491→28028`, затем `playing=1` и движение sequence, без возврата к0. Это подтверждает устранение обхода общей resume policy на настоящем внешнем media-key пути.

## Узкое production исправление, подготовлено к проверке

`LiveHlsSourceFactory` оставляет штатные parser, tracker, сегменты и scheduling. Небольшая обёртка отслеживает численные свойства каждого live media playlist отдельно и временно откладывает **только PlaylistStuckException**, если одновременно выполнено следующее:

1. Источник live, содержит Twitch commercial DATERANGE, START0, без ENDLIST.
2. Полный snapshot длиннее стандартного3.5×target окна ожидания.
3. При том же URI и sequence пришёл snapshot с меньшими count **и** duration.
4. После отката наблюдается новый рост count **и** duration; последний рост не старше2×target (ограничено5–15с).
5. С начала отката прошло менее60с. Повторный откат не продлевает срок; при выходе за прежний максимум обычный tracker принимает snapshot и grace заканчивается.

Во время grace не запускается новый source, не меняется token, playback position или play/pause. Перехватываются как выбрасывание Stuck, так и notification, которое иначе могло бы исключить rendition из выбора; другие ошибки передаются без изменений. При остановке роста, истечении срока, sequence/reset, ENDLIST и обычном неподвижном потоке действуют стандартная ошибка и существующий однократный recovery. Наличие рекламного маркера само по себе grace не даёт.

DEBUG opt-in общий timeout остаётся отдельным экспериментом и отключает эту production обёртку, чтобы результаты не смешивались. Для проверки production root должен удалить `observe_stuck_60s` и создать новый PlaybackSession. Добавлены6 unit сценариев условий/сроков и два native сценария production policy: отмеченный full→short с ростом должен продолжиться без restart; такой же источник, который прекращает расти, должен завершиться ошибкой/recovery. На момент подготовки этого раздела новые проверки ещё ожидают запуска root.

Обновление по артефактам root:6/6 `LivePlaylistProgressGuardTest` прошли; оба production native сценария имеют `INSTRUMENTATION_STATUS_CODE:0` в `evidence/beta11-integration-native.log`. Общий файл содержит также отдельные UI тесты и4 их ошибки; утверждение о полном12/12 прогоне неверно. Подтверждена именно работоспособность двух HLS сценариев, включая продолжение штатных reload во время grace.

## Отдельный decoder4003 и возврат из фона

Root получил повторяемый fatal decoder4003 в трёх реальных запусках после перехода вставка1080p→эфир720p. Последнее низкоуровневое наблюдение: `SoftAVCDec: Fatal Error : 0xffffffff` (`evidence/beta11-softavc-errors.log`). Оно не доказывает конкретный дефект повторного использования codec или недостаток памяти. [AOSP SoftAVCDec](https://android.googlesource.com/platform/frameworks/av/+/646a18fef28d19ba5beb6a2e1c00ac4c2663a10b/media/libstagefright/codecs/avcdec/SoftAVCDec.cpp) использует UnsupportedSetting для нескольких разных отказов; угадывать blacklist кодека по этому коду нельзя.

Подготовлена одна ограниченная попытка восстановления только для live video renderer `ERROR_CODE_DECODING_FAILED`, при foreground и валидном динамическом HLS окне. Позиция выбирается не раньше текущей и безопасного tail; неизвестный/просроченный snapshot не вызывает автоматическую попытку. Вызываются seek к этой позиции и `prepare()` существующего MediaItem, сохраняется текущее play/pause. Resolver и `setMediaItem` не вызываются. Отдельный decoder budget сбрасывается лишь явным play/retry, а не READY; повторный отказ остаётся понятной терминальной ошибкой.

Ограничение принципиально: [ExoPlayerImplInternal](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImplInternal.java) после fatal renderer error уже останавливает/освобождает media pipeline до listener callback. Поэтому `prepare()` может заново загрузить master и tracker. Сохранение MediaItem не означает сохранение сетевого состояния или гарантированное отсутствие новой вставки. Проверка на реальном decoder должна подтвердить практический результат; только тест инъекции этого не доказывает.

Добавлены два native теста с настоящим PlayerMessage/renderer exception: reprepare сохраняет MediaItem и паузу/воспроизведение, второй error не создаёт новую попытку. Это намеренно инъекция4003, а не искусственная имитация конкретного SoftAVC failure.

`onForeground()` также больше не делает unconditional `seekToDefaultPosition()` для live. Возобновление использует ту же проверку current snapshot и перемотку только вперёд; возвращение на паузе оставляет позицию и паузу. Native regression проверяет START0 и оба намерения пользователя. Подготовка полностью нового источника/отложенного source recovery остаётся отдельным путём; его поведение не объявляется исправленным только этой правкой foreground.

Root запустил оба decoder injection теста и foreground regression: все3 имеют status0 в `evidence/beta11-recovery-native.log`. В той же общей серии один отдельный notification UI тест упал; успешными считаются именно перечисленные playback сценарии. Реальная повторная проверка codec recovery на Twitch продолжается отдельно.

Первый реальный запуск с bounded decoder recovery (`evidence/beta11-riot-production.log`) подтвердил и восстановление, и его цену: на40-й секунде4003, одна попытка с target38.243с, новая загрузка master/начального6-секундного окна. Позиция оказалась впереди нового окна; загрузка ждала около41с, пока оно доросло. Затем игра продолжилась до181-й секунды без второго fatal. Это не объявляется приемлемой профилактикой ошибки: длительная буферизация — установленное ограничение fallback.

Новый лог также подтвердил `reuse_result=2` при1080→720 перед fatal. Подготовлен DEBUG-only контроль `playback-diagnostics/recreate_software_avc=true`: [MediaCodecSelector](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java) сохраняет список/порядок декодеров и для конкретного `OMX.google.h264.decoder` использует [MediaCodecInfo.newInstance(forceDisableAdaptive=true)](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecInfo.java). При смене разрешения штатный renderer должен пересоздать этот codec, сохраняя source/tracker. Остальные кодеки не затронуты. Non-seamless ABR остаётся поддерживаемым путём Media3; это не принудительный выбор одного качества.

Маркер контроля — `debug_software_avc_recreate enabled=1`. Обычный DEBUG/release пока не включает эту политику. Native fixture180p→90p содержит два реальных TS разрешения; проверяет REUSE_NO, продолжение воспроизведения и единственный master load. Дополнительный180p сегмент получен из собственного `qa-seek.mp4`, хранится только в debug assets. Результаты контролируемых real runs и решение о production workaround ещё ожидаются.

Native контроль прошёл: `evidence/beta11-codec-notifications-native.log`,8/8 включая один playback сценарий смены разрешения и семь отдельных notification тестов. Однако **первый реальный запуск опроверг достаточность этого обхода** (`evidence/beta11-software-probe-1.log`): в00:38:41 переход1080→720 действительно получил `reuse_result=0 reuse_discard=512`, но в00:39:03 снова возник4003. Codec был пересоздан, fatal всё равно произошёл через22с. Следовательно disableAdaptive не включается в production как исправление; гипотеза о seamless reuse как необходимой причине не подтверждается. Bounded fallback снова ожидал роста свежего окна к сохранённой позиции48.253с. Успех короткого fixture нельзя переносить на этот реальный поток.

Read-only native inventory (`evidence/beta11-codec-inventory.log`) обнаружил только `OMX.google.h264.decoder` как в Media3, так и в `MediaCodecList.ALL_CODECS`. XML-описание goldfish не означает фактическую доступность этого codec приложению. Уже сохранённый минимальный comparator beta.6 без PlaybackSession/MediaSession/чатов/HUD также получал тот же SoftAVCDec fatal в фиксированном480p30 (`docs/PERFORMANCE.md`, `evidence/beta6-minimal-comparator-codec.log`); смена разрешения,60fps и UI не являются необходимыми условиями ошибки.

Для разграничения старого OMX и платформы предложен отдельный, обратимый **эксперимент среды эмулятора**, а не настройка APK: root зафиксировал `debug.stagefright.ccodec=0`. [AOSP Codec2InfoBuilder](https://android.googlesource.com/platform/hardware/google/av/+/891b697%5E%21/) описывает0 как отключение Codec2,2 как включение `c2.android.*` с первым приоритетом. Если временное2 и новый inventory обнаружат другой декодер, тот же поток можно сравнить с ним; после контроля исходное0 восстанавливается. Наличие работающего media.swcodec само по себе ещё не подтверждает доступный AVC codec или исправление ошибки.

**Итог решения по decoder fallback:** автоматический `prepare()` после4003 удалён вместе с `LiveDecoderRecovery`, его helper и двумя тестами восстановительного пути. Реальная цена41–45с буферизации и новая загрузка вставки перевесили пользу единичного восстановления; тест инъекции подтверждал только механику. В default APK ошибка сразу остаётся доступной для ручного изменения качества/повтора. Новый native regression `fatalDecoderErrorDoesNotSilentlyRestartTheLiveSource` проверяет error/IDLE и единственную загрузку master после настоящего renderer exception. Это не считается исправлением платформенного decoder. Production HLS grace, правильное фактическое качество, MediaSession resume и foreground/live-edge сохраняются. Диагностический disableAdaptive остаётся только выключенным DEBUG opt-in, с зафиксированным отрицательным результатом.

Новый regression после удаления fallback прошёл в `evidence/beta11-full-native.log` (individual status0). Это подтверждает отсутствие скрытого автоматического подключения при4003; итог всей серии записывается отдельно после её завершения.

В той же серии завершены все16 `HlsSourceRecoveryTest` со status0: фактическое качество, START0 и растущее окно, повторный PLAY/MediaController, foreground и сохранение паузы, source recovery/budget/manual quality, стандартная ошибка full→short, controlled grace и два production guard исхода, terminal decoder без скрытого restart, диагностический codec transition. Это16 playback результатов, не утверждение об успехе всей общей UI серии: позднее в ней зарегистрирована отдельная ошибка `TvNavigationTest`.

## Финальная проверка кандидата на отдельном Android TV36

Root создал отдельный эмулятор `Twitch20Modern`/5564 с официальным образом Android TV API36. Обычный production playback-код кандидата проверялся в DEBUG APK без экспериментальных флагов; это гостевой Twitch. На старом primary восстановлено исходное `debug.stagefright.ccodec=0`, экспериментальные preferences удалены. Временное изменение property не открыло C2 в старом runtime inventory и не стало настройкой приложения.

`evidence/beta11-tv36-inventory.log` показывает первым доступным `c2.goldfish.h264.decoder`, затем `c2.android.avc.decoder`; OMX представлен alias в общем platform inventory. `evidence/beta11-tv36-decoder.log` отдельно подтверждает **создание C2 goldfish при реальном воспроизведении**, а не только наличие имени в списке. Объявленный platform-флаг hardware не делает этот эмулятор проверкой физического телевизора.

В `evidence/beta11-tv36-riot.log` за00:48:43–00:56:32:

- Одна исходная загрузка master; request-to-first-frame1835мс, из них resolver224мс.
- Сначала1080p30 вставка, затем720p30; в00:51:33 ручной выбор переводит renderer на720p60 без новой загрузки master. До последней записи это примерно4мин59с выбранного720p60, включая паузы.
- Сессия наблюдалась примерно7мин49с. Это не7мин49с непрерывного декодирования: root выполнял длительную паузу порядка75с и отдельную UI паузу8с.
- После внешнего MEDIA_PAUSE→MEDIA_PLAY в00:54:28 выполняется ровно один `live_edge_seek -46991→28028`; отрицательная исходная позиция отражает ушедшее live-окно. Повторный PLAY не создаёт нового seek.
- Отдельный D-pad UI путь через «Пауза»→«К эфиру» подтверждён `evidence/beta11-tv36-ui-pause.log`: после8с паузы в00:56:07 один seek12664→28028, кнопка снова «Пауза». Последующий MEDIA_PLAY также не перематывает.
- В сохранённом интервале нет `player_error`, source/decoder recovery, повторного master или возврата рекламного окна после этих возобновлений; source sequence продолжает двигаться. Начальная вставка была, её удаление не заявляется.

В этом real запуске не возник именно full→short откат, поэтому отсутствие Stuck здесь не заменяет положительный/отрицательный native regression production grace. Результат подтверждает работу кандидата на другой платформе и отделяет старый OMX отказ от общей UI/MediaSession логики; он **не доказывает исправление старого ROM, всех каналов или длительную стабильность на физическом TV**. Начальные30fps и последующие60fps также не выдаются за строгий A/B одного rendition на всём интервале.

Общая серия67 native тестов на старом primary завершилась59 PASS/8 FAIL; все16 playback тестов прошли. Восемь отказов относятся к отдельным UI fixture/geometry сценариям и исправляются их владельцами. Их повторный результат должен учитываться отдельно при подготовке релиза.

## Дополнительный дефект вывода в финальном release-кандидате

Повторный real Riot на TV36 обнаружил отдельный визуальный отказ: после1080p вставки→720p игры весь новый кадр занимал левый верхний прямоугольник примерно853×480 на экране1280×720, а за его границами сохранялись старые пиксели вставки (`evidence/android/beta11-tv36-ui-resumed-confirmed.png`). Ошибок плеера и повторной загрузки source при этом не было. Соотношение2/3 по обеим осям совпадает с720/1080; это согласуется с неверным обновлением выходного буфера/crop при смене разрешения, но ещё не доказывает конкретную ошибку реализации.

В `evidence/beta11-final-release-riot.log` переход произошёл в01:08:40.588 с `reuse_result=2`. ContentFrame остаётся в той же позиции Compose, fillMaxSize/Fit, оба формата16:9. Ручной выбор720p60 и изменение размеров при открытии quality panel не устранили старые пиксели. Поэтому QA этого кандидата по правильному выводу видео не считается успешным только на основании отсутствия exception.

Подготовлен отдельный контролируемый DEBUG эксперимент: существующий выключенный флаг `recreate_software_avc` теперь охватывает также точное имя `c2.goldfish.h264.decoder`. Он сохраняет порядок и остальные codecs, но заставляет штатный Media3 пересоздавать этот decoder при смене размера. Неудача прежнего OMX эксперимента остаётся в силе; это другая проверяемая гипотеза о goldfish output sizing. Native resolution-transition fixture проверяет REUSE_NO и один master; на остальных codecs диагностический тест пропускается. Для решения о production workaround обязательны реальный переход Riot и визуальная проверка полного кадра, а не только callback.

Контроль завершён: `evidence/beta11-goldfish-probe-native.log` содержит1 PASS. В реальном Riot (`evidence/beta11-goldfish-real-probe.log`)1080→720 в01:13:44.582 дал REUSE_NO/512; root и playback-владелец просмотрели `evidence/android/beta11-goldfish-probe-frame.png`: игровой кадр занимает весь1280×720, прежних пикселей вставки по краям нет. В системных скалярах исходного неуспешного запуска (`evidence/beta11-goldfish-size.log`) после1920×1080 отсутствовало обновление output size к720 при сменившемся input; это согласуется с наблюдением, но точный внутренний defect goldfish не выдаётся за установленный upstream bug.

После положительного визуального контроля в default selector добавлено отключение seamless adaptive reuse **только для `c2.goldfish.h264.decoder` с MIMEvideo/avc**. `AvcResolutionCompatibility` не меняет порядок выбора, capabilities, hardware/software/vendor/secure flags и возвращает исходный объект для всех других codecs. OMX остаётся исключительно неуспешным DEBUG экспериментом. Source/tracker не перезапускаются, адаптивный выбор качества возможен через штатное non-seamless переключение. Это совместимость с конкретным эмуляторным decoder, а не универсальное изменение всех телевизоров.

Новые native проверки: `goldfishResolutionChangeRecreatesDecoderWithProductionPolicy` принудительно отключает экспериментальный preference, проверяет180→90, REUSE_NO и один master; `productionCodecSelectorKeepsOrderAndOtherDecoderCapabilities` сверяет реальный список codec и сохранение остальных объектов/полей. Их запуск и повторная визуальная проверка подписанного release поручены root. До этого production patch остаётся подготовленным кандидатом, а не окончательным release PASS.

## Итог приёмки root

Root выполнил последний playback-прогон18/18 PASS на TV16 за213,865с, включая production Goldfish без диагностического флага. Точный signed APK7140b4d5… прошёл более9мин реального Riot с полным кадром, паузой, повторным PLAY и ручными720p60; source recovery/error отсутствуют. Публикация и OTA12→13 завершены. Подробности доставки: [DESIGN_BETA11.md](DESIGN_BETA11.md).
