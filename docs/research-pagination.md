# Пагинация Twitch-каталога

Проверено **2026-11-08, 14:34–14:35 UTC** на Windows-хосте через публичный `https://gql.twitch.tv/gql`. Использован только публичный Client ID сайта из существующего `TwitchRepository`; account tokens, cookies, playback tokens и media URLs не запрашивались. Это проверка сетевого контракта, отдельно от APK, аккаунта, эмулятора и физического TV.

## Решение после проверки

Гостевая GQL-пагинация не включается: ниже зафиксирован реальный отказ всех запросов продолжения. `TwitchRepository` остаётся без изменений, гостевой каталог ограничен первой выдачей. Полноценная постраничная загрузка после входа реализуется отдельным `HelixCatalogRepository`, использующим `TwitchAuth.helixGet`; личные токены никогда не передаются в GQL.

### Официальный контракт

Проверены [Streams](https://dev.twitch.tv/docs/api/reference/#get-streams), [Top Games](https://dev.twitch.tv/docs/api/reference/#get-top-games), [Videos](https://dev.twitch.tv/docs/api/reference/#get-videos), [Clips](https://dev.twitch.tv/docs/api/reference/#get-clips) и [Users](https://dev.twitch.tv/docs/api/reference/#get-users). Все принимают user token без дополнительных scopes для публичных метаданных. `pagination.cursor` передаётся как `after`; пустой объект pagination означает конец. Порядок live-каналов меняется по зрителям, поэтому между страницами возможны повторы/пропуски. Videos поддерживает `sort=time`, `type=all`, duration в h/m/s и thumbnail `%{width}`/`%{height}` с размерами 320×180. Clips использует числовые секунды и RFC3339 `started_at`/`ended_at`; предел одного диапазона — примерно 1000 клипов. Это ограничение API, не обещание полного архива.

### Контракт реализации

`CatalogPage<T>(items: List<T>, nextCursor: String?)`: `null` означает подтверждённое окончание выдачи. Методы `HelixCatalogRepository`:

- `streamsPage(categoryId: String? = null, after: String? = null): CatalogPage<Channel>`
- `categoriesPage(after: String? = null): CatalogPage<Category>`
- `videosPage(login: String, after: String? = null): CatalogPage<Video>`
- `clipsPage(login: String, after: String? = null): CatalogPage<Video>`

Конструктор принимает suspend `get(endpoint, parameters)` без доступа к credentials и `snapshotTimeMillis` (по умолчанию время создания). На каждом запросе videos/clips login разрешается через users→id, без постоянного кэша. Для клипов начало и конец последней недели фиксированы на время жизни экземпляра: смена курсора не сдвигает диапазон. Новый экземпляр нужен для обновления этого временного снимка. Размер страницы: streams/videos/clips 30, categories 40. Поиск и старые List API не меняются.

## Исследование гостевого GQL

Для всех пяти веток схема принимает аргумент `after` типа `Cursor`, `pageInfo { hasNextPage }` и `edges { cursor node { … } }`. `after` передавался через GraphQL variables без преобразования. Проверенные размеры: общий streams 30, game.streams/games 40, videos/clips 30.

## Реальные результаты

Локальный воспроизводимый скрипт `.local/probe-pagination.py` и сводка `.local/pagination-results.json` исключены из Git. Скрипт сохраняет лишь HTTP-статус, счётчики, флаги и ID первого/последнего элемента; изображения и тела ответов не сохраняются. Дополнительные короткие пробы выполнены отдельно.

| Connection | Первая страница: элементы / уникальные | hasNextPage | Вторая страница |
|---|---:|---|---|
| streams(first:30) | 30 / 30 | true | HTTP 200, GQL `IntegrityCheckFailed` |
| game(id:"509658").streams(first:40) | 40 / 40 | true | HTTP 200, GQL `IntegrityCheckFailed` |
| games(first:40) | 40 / 40 | true | HTTP 200, GQL `IntegrityCheckFailed` |
| user(buster).videos(first:30,sort:TIME) | 23 / 23 | false | Не нужна: connection исчерпан |
| user(buster).videos(first:3,sort:TIME) | 3 / 3 | true | HTTP 200, GQL `IntegrityCheckFailed` |
| user(buster).clips(first:30,criteria:{period:LAST_WEEK}) | 28 / 28 | true | HTTP 200, GQL `IntegrityCheckFailed` |

На первых страницах не было null nodes или повторных ID. **Число элементов и дедупликация между двумя успешными реальными страницами не подтверждены**: вторые запросы отклонены Twitch. `after` признан схемой, но фактическая выдача следующей страницы сейчас требует integrity-проверку сервиса. Challenge не обходился. Успешный парсерный/fixture-тест не снимает это ограничение и не доказывает доступность полного гостевого каталога.

Важные наблюдения:

- `game.streams(first:3)` в отдельной пробе вернул 2 элемента и `hasNextPage=true`; clips(first:30) — 28 и true. Нельзя выводить конец из `items.size < requestedSize`.
- У clips все промежуточные edge cursors были null, последний — непустой (`Mw==` для пробы из 3 элементов). Нельзя требовать cursor на каждом edge.
- В VOD с продолжением все edge cursors были одинаковыми: курсор всей страницы повторялся у каждого элемента. Это допустимо внутри одной страницы и не является зацикливанием.
- У исчерпанной VOD-страницы все edge cursors были null и `hasNextPage=false`.
- `user(monstercat).clips(...LAST_WEEK)` дал `edges:[]`, `hasNextPage=false`: подтверждённая пустая конечная страница.

## Ошибки и граница ответственности

Чистый `HelixCatalogJson.page(response, mapper)` требует массив data, объект pagination и непустой строковый cursor, если поле присутствует. Отсутствующий/неверного типа pagination, null cursor, пустой cursor и null item являются безопасной `TwitchError.SERVICE`, а не частичным успехом. Пустой объект pagination даёт `nextCursor=null`; наличие cursor сохраняется даже на короткой/пустой странице. Ошибки транспорта и отмена coroutine проходят к владельцу загрузки.

Сравнение входного/выходного курсоров, обнаружение циклов между страницами, дедупликация элементов, сохранение уже загруженных элементов при ошибке продолжения и защита от устаревшего ответа принадлежат состоянию загрузчика каталога. Репозиторий отдаёт одну страницу и не копит историю курсоров.

Гостевой `TwitchJson.data` продолжает проверять GraphQL errors до чтения connection; отдельная новая GQL page-реализация и её тесты не добавляются, потому что не решают подтверждённый внешний отказ.

## Статус проверки реализации

Подготовлены 11 регрессионных тестов Helix: строгая пагинация, курсоры/фильтры запросов, поля моделей, ID канала, duration/thumbnail, постоянное окно клипов, неизвестный login, транспортные ошибки/отмена. RED/GREEN и Android-проверки выполняет root, единственный владелец Gradle и устройств. Здесь не выполнялись авторизованные Helix-запросы: их фактическую первую/вторую страницу проверяет root в APK с имеющейся сессией. Сетевые GQL-пробы выше не являются результатом Kotlin-тестов или доказательством Helix-интеграции.
