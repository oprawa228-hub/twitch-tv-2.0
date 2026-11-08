# Навыки — проверено 2026-11-09

## Дизайн: исследование и дополнение набора

Дополнительный поиск при старте реализации: изучен [reanimate](https://github.com/NiazMorshed2007/reanimate/blob/main/skills/reanimate/SKILL.md), найден [mimic](https://github.com/sumanrox/mimic). Reanimate предназначен для покадрового точного повторения сайтов/роликов; его браузерный набор не устанавливался, так как пользователь уточнил, что копия не нужна. Для существующих SVG доступны исходные слои/тайминги, а конечный результат проверяется нативной записью. Установлен `choreograph` из того же зафиксированного commit Emil Kowalski: согласование нескольких движений, причин и реакций. Применяется как узкий motion-источник, не как новый визуальный стиль. Новая установка доступна со следующего пользовательского сообщения.

`product-design-and-ux` применяется в ограниченной роли: сценарии, черновик/сохранение/отмена, восстановление, отделение пользовательских предпочтений от доказанных результатов. Refero остаётся основным визуальным методом. Ни один навык не является гарантией удобства: изменения проверяются в APK.

Пользователь попросил подобрать дизайнерские навыки, включая поиск в интернете. Проверены реальные исходники и официальные репозитории; количество звёзд/описания агрегаторов не использовались как доказательство качества.

| Навык / источник | Решение и роль |
|---|---|
| Локальный refero-design | Основной визуальный метод: выбранный ориентир Twitch 1.0.2, согласованные правила, сравнение настоящего Android-рендера. Live Refero MCP недоступен; существующие кадры — основной материал |
| Локальные frontend-design и [Impeccable](https://github.com/pbakaus/impeccable) | Изучены для сравнения подходов к композиции, типографике и полировке. Уже установлены; не дублировать установку и не запускать параллельный полный процесс вместо Refero |
| [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | Рассмотрен как широкий набор визуальных направлений и рекомендаций. Не устанавливается для этого этапа: направление уже выбрано, нужен точный перенос характера и TV-проверка, а не дополнительный генератор стилей |
| [animate](https://github.com/emilkowalski/skills/tree/5206a950b715e0cabe7043ea0b2304332b91494e/skills/animate) | Установлен: постановка движения, единые параметры, прерываемость, осмысленный статический вариант |
| [review-animations](https://github.com/emilkowalski/skills/tree/5206a950b715e0cabe7043ea0b2304332b91494e/skills/review-animations) | Установлен для специализированной проверки движения после реализации; содержит disable-model-invocation, не считать автоматически запущенным review |
| android-tv-settings-preview / android-tv-remote-qa | Применяются для соответствия примера реальному результату, фокуса/черновика/сохранения и native D-pad / MP4 |
| android-profiler / jetpack-compose-performance | Подключать для измерения сцены и диагностики подтверждённых задержек; общие веб-утверждения о GPU не являются результатом измерения Compose |
| android-tv-apk-release | Проверка и доставка точного подписанного APK после визуального этапа |

Новые навыки установлены через системный skill-installer из `emilkowalski/skills`, commit `5206a950b715e0cabe7043ea0b2304332b91494e`, MIT. Перед установкой прочитаны SKILL.md обоих навыков; локальные RECIPES.md / STANDARDS.md установлены вместе с ними. Исходники исследованы сейчас; обнаружение новых навыков приложением ожидается со следующего сообщения пользователя. Это подготовка к реализации, а не заявление о выполненном animation review.

| Установленный файл | SHA-256 |
|---|---|
| `C:/Users/Opraw/.codex/skills/animate/SKILL.md` | `39138996033E0DBE53E73217514116EAECA774BAE84A0687C80D445A4E0CC3A6` |
| `C:/Users/Opraw/.codex/skills/review-animations/SKILL.md` | `5F9FB98B69CACE7107AF8B393A4196FD42FA42D98BCE0E5AFC1F866FF3B8A41C` |

Область применения ограничена: у новых навыков веб-ориентированные CSS/WAAPI/Motion, hover и keyboard-правила. В Android TV использовать существующий Compose, немедленный D-pad-фокус и системные настройки движения. Пользовательский запрос живых игровых примеров имеет приоритет над ограничением delight только первым запуском. Не устанавливать веб-библиотеки по рецептам и не переносить запрет движения при клавиатурном вводе на весь телевизионный интерфейс. Общая эстетика остаётся у Refero; авторские сцены — у выбранного источника 1.0.2.

Платформенные первоисточники: [Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv), [Compose for TV](https://developer.android.com/training/tv/playback/compose), [Animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide). Они дополняют навыки требованиями к реальному TV-продукту.

## История применения до визуального этапа

Установленные навыки использованы по текущим файлам; новые пакеты не потребовались. SHA-256 фиксирует точную локальную ревизию. Правила задач и разрешения пользователя имеют приоритет.

Beta.7: android-tv-settings-preview (`C:/Users/Opraw/.codex/skills/android-tv-settings-preview/SKILL.md`, SHA-256 `32D58D4E10118A813BC571BAF57AB4859FC2958CC1E631104DE2161F949D18E2`) — разделение примера/активного режима, общий редактор и native проверка профилей. Refero — сохранённые пользовательские ориентиры и реальный рендер каталога; systematic-debugging/TDD — RED миграции; remote-qa — быстрые кнопки/Back/save/restart. Новые навыки не устанавливались.

|Навык|Источник/путь|SHA-256|
|---|---|---|
|refero-design|C:\Users\Opraw\.codex\skills\refero-design\SKILL.md|4A4A7A0B72A2FA0A454903DEBE6E872ADA79A724CB452FE2113D266C2F0C6978|
|android-tv-apk-release|C:\Users\Opraw\.codex\skills\android-tv-apk-release\SKILL.md|4A715BB17B842E56C0AEA0C6EA197D583AC4095CA2C6A148A8488180F887EF31|
|android-tv-remote-qa|C:\Users\Opraw\.codex\skills\android-tv-remote-qa\SKILL.md|1FDC624C3AE1C8537CC52BC6383FAD4E5FB2781EA787367DF884DC911BA2F842|
|media3-video-playback|C:\Users\Opraw\.codex\skills\media3-video-playback\SKILL.md|5DA24DBE8816DAC412A991F5C42C54DE977120C08701D93C816D31E5629BF022|
|media3-hls-dash-adaptive-streaming|C:\Users\Opraw\.codex\skills\media3-hls-dash-adaptive-streaming\SKILL.md|11F120F98C79CFCD75E1F98B81281B9FACB614E6350255E7A398E633874CC3E4|
|dispatching-parallel-agents|C:\Users\Opraw\.codex\skills\dispatching-parallel-agents\SKILL.md|1968923066F3B707EB01D1992CDF4C42284C3855F70253B9CD5000FF45FCA13C|
|android-cli|C:\Users\Opraw\.codex\skills\android-cli\SKILL.md|83C4CA628DF8A264746762AB189A34CE2219B528F37D24956BE6A714646A6322|
|android-profiler|C:\Users\Opraw\.codex\skills\android-profiler\SKILL.md|D3083B151DE83707950AC4EDD68E84A11D153BD83D334E5315A44D45CE13B6BE|
|verification-before-completion|C:\Users\Opraw\.codex\plugins\cache\openai-curated-remote\superpowers\7.0.1\skills\verification-before-completion\SKILL.md|2BEFE7FC55BCADAA3D97DD9E8EFEB633D2561C0EBE74C5A8B17C4D9E7E4520B3|

Superpowers7.0.1 (openai-curated-remote): using-superpowers, brainstorming, writing-plans, executing-plans, test-driven-development, systematic-debugging. Использованы для выбора подхода, плана и проверки. Подтверждения технических решений не запрашиваются: пользователь явно делегировал их и поручил продолжать самостоятельно.
Refero: supplied current screenshots replace absent live Refero tools; no new branding. Media3 skill version1.10.1 superseded by researched1.11.1. Android CLI skill read for SDK tooling; installed command-line SDK already available.
Компьютер: computer-use skill26.1103.32239, UI through cua_repl for GitHub repo creation; connected GitHub tools used where capabilities exist.

android-profiler применён для записи Perfetto и разделения измерений CPU, кадров UI, декодера и памяти; результаты в PERFORMANCE.md. verification-before-completion применён перед передачей: сверены тесты, публичный APK, установленный APK и фактический путь OTA.

media3-analytics-telemetry v1.1 (Shunnek Labs, Apache-2.0), `C:/Users/Opraw/.codex/skills/media3-analytics-telemetry/SKILL.md`, SHA-256 `25029D7F394FD96EBB281AD69FF5608381C6863E8C267934E5C96D7E8A30F114`: beta.5 request/prepare→first frame, разделение startup/rebuffer/seek/foreground/quality, числовые decoder counters без URL/токенов. Версия Media31.11.1 сохраняется по проверенному исследованию проекта; устаревший pin1.10.1 из навыка не применяется.
