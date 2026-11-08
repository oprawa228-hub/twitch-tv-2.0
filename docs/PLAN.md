# План Twitch 2.0

## Текущий этап — внутренняя часть после beta.3

2026-11-08: пользователь проверил приложение и сообщил, что основные функции работают; визуальную доработку отложил. Продолжаем каталог/сеть, без изменения визуального оформления. Технические решения и выполнение делегированы ранее.

Подход: курсорные страницы из изолированного TwitchRepository; отдельный CatalogPager хранит список, cursor и ошибку. Подгрузка при приближении к концу сетки; ошибка следующей страницы не стирает карточки и допускает явный повтор. Ограниченный кэш последних разделов сохраняет список/позицию при возврате. Полная предварительная загрузка отвергнута из-за задержки, памяти и лишних запросов; новый сервер не нужен.

- [x] Реальные guest cursor probes: все пять продолжений блокируются IntegrityCheckFailed. Решение — официальный Helix после входа, guest ограничение явно показано. CatalogPage<T> и HelixCatalogRepository проверены 11 тестами; TwitchRepository не изменён. См. research-pagination.md.
- [x] Root: CatalogPager<T> со StateFlow, ID-dedup, одним запросом, поколениями/cancel, retry, пределом памяти и ограниченным пропуском пустых страниц. 9 тестов прошли после наблюдённого RED.
- [x] Root: подключены страницы и LRU из трёх каталогов по 1000 элементов. После review исправлены metadata канала при refresh и восстановление фокуса через revision/key(pager). Android-проверки ниже.
- [x] backend_review нашёл и исправил две доказанные auth ошибки: offline startup теперь повторяет validate через 60с; refresh→save переживает отмену экрана с пределом 30с и guard от logout. 10 новых auth/Helix access тестов прошли. Новых подтверждённых ошибок чата не найдено.
- [ ] JVM suite, Android D-pad переход за первую страницу и возврат после канала, сетевой retry, release APK; затем публикация новой beta с постоянной подписью и проверка обновления.

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

Будущее расширение покрытия: физический ARM TV/аппаратный decoder/1080p60, многочасовое видео,20+ смен каналов, длительный busy chat, token expiry/refresh/revoke и больше сочетаний quality/seek/lifecycle. Эти ограничения не оставляют уже выполненные основные этапы «в процессе». Общий live каталог ограничен30, категории40, VOD/clips30; бесконечной пагинации нет. Subscriber-only/Turbo не гарантируются.

Raw XML/PNG/log/trace в `evidence/` и installed APK в `.local/` — локальные доказательства, исключённые из Git. Публичные артефакты доступны через GitHub Releases, не по локальным dist-ссылкам.
