# Выполненный план Twitch 2.0

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
