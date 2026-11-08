# Twitch: доступ, видео, аккаунт и чат

Проверено **2026-11-08** на Windows-хосте, через публичную сеть. Это сетевые проверки, не доказательство декодирования в APK. Старые секреты, аккаунты и исходники клиента не использовались. Временные playback tokens и подписанные URL не сохраняются.

## Решение

Ранний рабочий путь: гостевой каталог → поиск/канал → свежий источник HLS → Media3. Публичный web GraphQL и Usher фактически работают, но **не являются документированным API для сторонних клиентов**. Выделить их в заменяемый `TwitchRepository`/resolver, не связывать с экраном или владельцем плеера. Не делать свой сервер только ради этого пути.

Аккаунт: собственное зарегистрированное приложение Twitch типа **Public**, официальный Device Code Flow и Helix. Это отдельная интеграция; она не заменяет получение источника видео. Собственный public Client ID можно включать в APK; client secret в APK недопустим. Вход выполняет пользователь на странице Twitch по показанному коду/QR. Исследовательский web Client ID нельзя использовать для авторизации от имени нашего приложения.

## Документированная поверхность

| Задача | Официальный способ | Практический вывод |
|---|---|---|
| Каталог, категории, поиск, канал | Helix streams/games/search/users | Нужен app либо user access token, даже для публичных данных |
| Отслеживаемые каналы/эфиры | Helix channels/followed, streams/followed | User token, `user:read:follows`, user_id самого пользователя |
| Записи/клипы | Helix videos/clips | Метаданные и ссылки на Twitch; не общий native media resolver |
| Скачать клип | Helix clips/downloads | Ограничено broadcaster/editor разрешениями; не просмотр произвольного клипа |

Источник: [Helix reference](https://dev.twitch.tv/docs/api/reference/). В гостевом эксперименте `GET /helix/streams?first=1` без заголовков вернул **401 OAuth token is missing**. Таким образом, «Helix без входа и без сервера» не подтверждается. Для официального гостевого каталога понадобился бы backend с конфиденциальным приложением/app token; пока это лишняя эксплуатационная зависимость.

[Документированный web embed](https://dev.twitch.tv/docs/embed/video-and-clips/) поддерживает live, VOD и clips, требует корректный `parent` и минимальную область 400×300. Это возможный запасной продуктовый путь через web player, но не замена нативному resolver: управление пультом, наложение чата, геометрия панелей и WebView требуют отдельной проверки. Не считать embed проверенным в Android TV.

## Вход и жизненный цикл сессии

[Device Code Flow](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow) предназначен для телевизоров. Public client не хранит secret, получает код через `/oauth2/device`, опрашивает `/oauth2/token` по возвращённому interval и поддерживает refresh без secret. Публичные DCF refresh tokens одноразовые; неактивные истекают через 30 дней. Access token lifetime брать из `expires_in`. Public client ограничен DCF, поэтому его нельзя использовать как confidential client для client-credentials.

Регистрация требует аккаунт владельца, подтверждённую почту и 2FA: [Register app](https://dev.twitch.tv/docs/authentication/register-app/). Во время исходных гостевых проб собственного Client ID не было; позднее root получил публичный ID от пользователя и настроил сборку. Сам ID ещё не подтверждает настоящий вход, follows, refresh или отзыв разрешений. Минимальное личное действие пользователя — подтверждение кода в предусмотренном интерфейсе. Пароль/токен в чат не нужны.

[Token validation](https://dev.twitch.tv/docs/authentication/validate-tokens/): проверять сохранённую сессию при старте и каждый час работы; при отзыве завершать её. [Refresh](https://dev.twitch.tv/docs/authentication/refresh-tokens/): единый владелец обновления исключает параллельное использование старого refresh token. Новую пару сохранять атомарно в защищённом локальном хранилище; при неудаче требовать повторный вход. Общая refresh-страница содержит старую формулировку о единственном authorization-code flow; конкретная DCF-страница и примечание public client явно описывают refresh для DCF.

**Ограничение прав видео:** собственный OAuth гарантирует только разрешённые официальные API. [Upstream Streamlink](https://streamlink.github.io/cli/plugins/twitch.html#authentication) описывает несовместимость сторонних OAuth tokens с private playback auth. Поэтому subscriber-only VOD, преимущества Turbo и иные права аккаунта не подтверждены и не должны обещаться. Не извлекать web cookies и не предлагать пользователю передавать личные токены.

## Реальные гостевые эксперименты

Воспроизведение: `python tools/probe_twitch.py`. Скрипт использует только стандартную библиотеку Python и публичный web Client ID из [исходника Streamlink](https://github.com/streamlink/streamlink/blob/master/src/streamlink/plugins/twitch.py). Не логирует playback tokens, signatures или media URLs; читает максимум 64 KiB одного медиафрагмента. Он не обходит paywall и не проверяет закрытый контент.

| Проверка | Наблюдение первого запуска |
|---|---|
| GQL streams(first:8) | HTTP 200, 8 настоящих текущих эфиров; 406 мс |
| buster live token | 200, value/signature получены; 328 мс |
| live HLS master | 200; 1080p60, 720p60, 480p30, 360p30, 160p30, audio_only; 234 мс |
| live variant playlist | 200, 3 сегмента; 454 мс |
| live segment Range | 206, прочитано 65536 байт; 468 мс |
| user(buster) videos/clips | 200; 3 записи и клип получены |
| VOD 2923060769 | Token/master/variant 200; 6 вариантов, 265 сегментов; Range 206 |
| Clip SmoothAbstemiousCougarAMPEnergyCherry-_ryaolVKAvVvGHya | 1080/720/480/360; подписанный Range 206, MP4 `ftyp` |
| Anonymous IRC TLS | welcome + ROOMSTATE + 1 PRIVMSG, 1182 байта; текст чата не сохранялся |

Дополнительная проверка WSS: один frame с CAP/PASS/NICK/JOIN не дал PRIVMSG за 20 с. Те же команды отдельными text frames дали welcome, ROOMSTATE и PRIVMSG (1162 байта). Android `ChatClient` отправляет команды отдельно. Это конкретный найденный и исправленный риск транспорта; успех TCP IRC не заменяет WSS-проверку.

Дополнительные отдельные запросы: `games(first:6)` дал 6 категорий с boxArtURL и viewersCount; `game(name:"Just Chatting").streams(first:2)` дал реальные эфиры; `streams.pageInfo.hasNextPage` и cursor работают; `previewImageURL`, profileImageURL, VOD publishedAt/lengthSeconds/previewThumbnailURL/viewCount присутствуют. `user(login:"monstercat").stream` вернул null — offline состояние, а не ошибка.

Поиск: первоначальный запрос без `platform` вернул GQL error при HTTP 200. Исправленный `searchFor(userQuery:"monstercat", platform:"web", options:{targets:[{index:CHANNEL}]}) { channels { items { id login displayName stream { id title viewersCount } } } }` дал 15 настоящих результатов, часть offline. Это причина всегда проверять массив `errors`, а не только HTTP status.

Проверка окончательных Kotlin-запросов выявила отдельный лимит: корневой `streams(first:40)` возвращает HTTP 200 + GraphQL error «first 1..30». Исправлено на 30 и перепроверено; `games(first:40)`, `game.streams(first:40)`, `user.videos(first:30)` и `user.clips(first:30)` приняты без ошибок.

Все времена — одиночные сетевые наблюдения без статистической силы и без Android декодирования. Live/VOD здесь могут использовать fMP4 (первый байт не TS sync), поэтому нельзя навязывать MPEG-TS. [Upstream Twitch playback notes](https://streamlink.github.io/cli/plugins/twitch.html#higher-quality-streams) описывают fMP4 и современные кодеки. Первый APK запрашивает совместимый AVC/H.264; HEVC/AV1 выбирать только после проверки аппаратных возможностей устройства.

### Resolver protocol, проверенный экспериментом

1. POST `https://gql.twitch.tv/gql`, JSON `{query,variables}`, публичный web `Client-ID`.
2. Live: `streamPlaybackAccessToken(channelName:login,params:{platform:"web",playerBackend:"mediaplayer",playerType:"site"}) { value signature }`.
3. VOD: `videoPlaybackAccessToken(id:id,params:...) { value signature }`.
4. URL live: `https://usher.ttvnw.net/api/channel/hls/{login}.m3u8`; VOD: `https://usher.ttvnw.net/vod/{id}.m3u8`. Query: token/value, sig/signature, allow_source, allow_audio_only, supported_codecs=avc1. Все значения URL-encode.
5. Clips: `clip(slug:slug) { videoQualities { quality frameRate sourceURL } playbackAccessToken(params:{platform:"web",playerType:"site"}) { value signature } }`; добавить sig/token к выбранному MP4.

Новый token получать при запуске/восстановлении, не хранить signed URL как историю. В истории только channel login / video ID и позиция. На 401/403 один ограниченный повтор разрешения; отсутствие права, offline, challenge и недоступность сети должны иметь понятные разные исходы. Не превращать любой 403 в бесконечное обновление OAuth.

## Чат и события

[Официальный IRC](https://dev.twitch.tv/docs/chat/irc/) поддерживает TLS и WebSocket, capabilities tags/commands и OAuth `chat:read`; EventSub рекомендуется Twitch для новой функциональности. В исследовании guest `justinfan` + фиктивный PASS успешно получил данные; именно анонимный режим здесь **эмпирический, недокументированный контракт**. Для раннего read-only чата он подходит с ограниченным буфером, reconnect/backoff, PING/PONG и закрытием старой сессии при смене канала.

Для авторизованного чтения [EventSub channel.chat.message](https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/#channelchatmessage) требует `user:read:chat`; user-token WebSocket вариант не требует broadcaster bot grant. [WebSocket lifecycle](https://dev.twitch.tv/docs/eventsub/handling-websocket-events/) позволяет обойтись без сервера: один socket, подписка после welcome, обработка keepalive/reconnect/revocation, дедупликация message IDs. Уведомления о начале эфира вне активного приложения отложить: постоянный TV socket конфликтует с жизненным циклом и энергопотреблением.

Для первой версии: guest live/chat включать как ограниченную интеграцию; preview видео на карточке отложить до нагрузки основной сессии; seek thumbnails отложить до проверки отдельного недокументированного источника; сторонние эмоты отложить до ограниченных кэшей и лицензий. Это не блокирует базовые Twitch эмоты из tags.

## Внешние риски и границы проверки

- [Client-integrity, Streamlink issue #5370](https://github.com/streamlink/streamlink/issues/5370): Twitch уже менял требования и ломал guest source resolution. Нынешний успех не гарантирует устойчивость завтра. При challenge показывать отказ внешнего сервиса; не строить собственный обход anti-bot.
- [Streamlink license](https://github.com/streamlink/streamlink/blob/master/LICENSE): BSD-2-Clause; использован как первичный источник протокола, Python-библиотека и целый клиент не копируются/не встраиваются.
- Непроверены: собственный DCF, scopes, follows, refresh, revoke, private/subscriber content, регионы, длительное воспроизведение, реклама/дисконтиниуити, Android hardware decode, physical TV. Их нельзя отмечать выполненными по результатам этого файла.
- Следующий шаг: real Media3 APK с одним live и VOD, первый кадр/seek/quality, затем жизненный цикл и совместная нагрузка чата. Root владеет сборкой и эмулятором.

## Реализация после проб

`TwitchRepository` реализует проверенные гостевые запросы, ограничивает JSON-ответ 2 MiB и отменяет OkHttp call при отмене coroutine. `TwitchAuth` использует только официальный DCF/Helix, отдельно от guest resolver; одноразовые refresh tokens обновляются под mutex и атомарно сохраняются перед дальнейшим запросом. Ошибки не включают response bodies или credentials. `TokenVault` использует [AndroidKeyStore](https://developer.android.com/privacy-and-security/keystore) AES-GCM и [noBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()), манифест дополнительно запрещает backup. `ChatClient` ограничивает сообщения 150×500 символов, имеет отменяемый reconnect/backoff и поколение соединения против старых callbacks. `PlaybackHistory` хранит только ID и позиции последних 100 VOD/клипов; signed URL отбрасываются.

Парсерные тесты и политика истории переданы root для общей сборки. Здесь не запускались конкурирующие Gradle/эмуляторные задачи. Проверки Android и личной сессии фиксируются главным агентом отдельно.

После получения собственного публичного Client ID: `/oauth2/device` с `user:read:follows` вернул HTTP 200, оба кода, `expires_in=1800`, `interval=5`, host подтверждения `www.twitch.tv`. Отдельный неавторизованный код опрошен после 5 с через `/oauth2/token` без secret: HTTP 400 `authorization_pending`, access token отсутствует. Коды и тела ответов не сохранены. Это доказывает регистрацию/начало DCF и форму pending-ответа, но не успешный вход или refresh; их проверяет root в APK с пользователем.

Позднее root сообщил об успешном личном DCF в настоящем APK и загрузке отслеживаемых эфиров. Следом реализован `followedChannels()`: официальный `channels/followed` для полного списка с offline-каналами и `streams/followed` для текущего live-состояния, страницы по 100. Дубликаты ID исключаются, циклический cursor прекращает запрос; предел 2000 каналов/40 страниц возвращает явную ошибку, не обрезанный «полный» список. Offline-карточки сохраняют ID/login/name и используют нейтральные инициалы вместо выдуманной картинки. Эта новая объединённая загрузка передана root для отдельной APK-проверки; refresh/revoke не объявляются проверенными по одному успешному входу.
