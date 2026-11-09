# Передача результата Twitch 2.0

## Доставка beta.11 подтверждена

- [Скачать APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.11/Twitch20-0.1.0-beta.11.apk), [страница выпуска](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.11). Размер 2 821 901 байт, code13, SHA-256 `7140b4d528963a6ca17fe716453e6dcf5cc0e31a70b25d8ec063ce9898280ff1`.
- Source/tag `02c75b4dc13ed513222aea3d9e92646ccf0339da`, дерево `87f1ede398c6fc6dd04406e0ea42438bcfc11be5`; feed commit `14593ff74fff185ca9be8b5502c2b841a49e62be`. Три публичных файла побайтно совпали с проверенным кандидатом. Обычный URL `updates/test.json` вернул code13 и тот же SHA.
- Штатное OTA **12→13** на `Twitch20Release/emulator-5562`: поиск, скачивание, проверка файла, системный INSTALL/OPEN. Установленный base.apk побайтно совпал с публичным. Это OTA, а не adb-install.
- Сохранены THEATER, оба размера16 (теперь «Компактный»), средний фон overlay/.65, положение справа сверху, часы и анимации. Профиль overlay остался выключен. Запись Caedrel продолжилась с прежних3:26:07; первый наблюдаемый кадр/шкала3:26:12. Этот AVD гостевой; авторизованный аккаунт отдельно сохранён на `Twitch20Test`.
- Финальный signed Riot на Android TV16: более9 минут, в том числе начальная вставка1080p→720p, полный кадр, UI-пауза8с, повтор PLAY, ручные720p60. Нет player_error, source recovery и повторного первоначального запроса. Кнопка и подпись показали720p60. Это проверка двух конкретных декодеров эмуляторов; старый OMX4003 на API30 остаётся открытым, физический ARM TV не проверен.
- 192 JVM PASS; 69 различных Android-сценариев PASS по отдельным прогонам. Последние целевые серии: navigation16/16 и playback18/18. Подробности первоначальных падений и исправлений сохранены ниже и в специализированных отчётах.

Локальные доказательства: `evidence/beta11-public-verification.json`, `beta11-installed-verification.json`, `beta11-signed-final-riot.log`, `beta11-final-playback-native.log`, `beta11-navigation-confirmed.log`; `evidence/android/beta11-ota-*.xml/png`, `beta11-signed-final-resumed.png`, `beta11-signed-final-quality-720p60.png`. Эти raw-файлы исключены из Git.

## Проверка кандидата beta.11 / code13

Подписанный APK: `Twitch20-0.1.0-beta.11.apk`, 2821901 байт. SHA-256 `7140b4d528963a6ca17fe716453e6dcf5cc0e31a70b25d8ec063ce9898280ff1`. Сертификат `f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7` — прежний, пакет `com.oprawa.twitch20`.

Исправлены повторный старт источника при растущем откате рекламного HLS-плейлиста, продолжение live после паузы и неверные160p. Кнопка качества квадратная. Отдельный поиск, онлайн-подписки в rail, уведомления внутри приложения, четыре именованных размера чата и контекстные предпросмотры. Сохранение прокрутки при раскрытии rail исправлено по реальному падению теста. [Обоснование и проверки](DESIGN_BETA11.md).

192 JVM PASS. 69 различных native сценариев подтверждены общим прогоном и повтором16 navigation-сценариев после исправлений. Последний отдельный playback-прогон18/18 PASS, включая production Goldfish; точный release описан в отчёте. Debug native-тесты не выдаются за тесты самого minified APK.

Android TV API30/x86 и API36/x86_64, три изолированных AVD. Начальная реклама Twitch может присутствовать; отдельный старый OMX fatal4003 не исправлен. Для Goldfish выявлен и проверен отдельный обход неверного размера кадра при смене разрешения, без повторного запроса HLS. Физический ARM TV пока не проверен.

Публикация и штатное OTA12→13 подтверждены в разделе доставки выше.

## Предыдущая доставка — beta.9/code11

[Подписанный APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.9/Twitch20-0.1.0-beta.9.apk),2 772 743байта,SHA-256 `ded0ab870f9adcb8871afc46731a93292d319a6bc75ad39674868f00c18addce`, прежний сертификат. Source/tag `27dc2a9435a38db43a777286b63dbb162d125ebe`, test feed `47a5fa6d386af37d5a0928937c941bb441b17ab4`.

Иконки вместо текстового HUD; полноценный seek-draft с удержанием/OK/Back и сохранением pause/play; именованные пресеты с временной примеркой; выбранное качество при открытии; отдельный long-OK профилей; измеренные границы чата и фиксированные часы. [Решения/проверки/таблица сценариев](DESIGN_BETA9.md), [полный аудит оставшихся пунктов](UI_UX_CHECKLIST_AUDIT.md), [чеклист пользователя](USER_UI_UX_CHECKLIST.md).

155 JVM PASS;12 navigation/error-focus и7 seek Android PASS раздельными прогонами; точный release прошёл реальные Twitch live/VOD, выбор160p/возврат фокуса, шрифт/отмену,720p/1080p/130%.4K ограничен эмулятором, физический TV не проверялся.14с нативной примерки шрифта: `evidence/android/beta9-font-preview.mp4`. Изображения `beta9-vod-seek-draft.png`, `beta9-font-preview.png`, `beta9-live-theater.png` — настоящий интерфейс; сценка в настройках помечена примером.

Второй AVD Twitch20Release получил обычное OTA10→11, установленный хеш совпал с опубликованным. THEATER, оба прежних размера («Свой размер»), фон65%, TOP_RIGHT, часы и анимации сохранены. VOD Caedrel возобновился с3:26:05. Primary Twitch20Test сохраняет аккаунт oprawa. Следующий код12, имя beta.10; опубликованный beta.9 не перезаписывать.


## Предыдущая доставка — beta.8/code10

[Подписанный APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.8/Twitch20-0.1.0-beta.8.apk), 2 772 750 байт, SHA-256 `f1338899b612b3114d1b0ba2d6fafd51d3c07275c7e2cc38a8651d75b5aa5ec3`, прежний сертификат. Source/tag `dcc2087591b8e88d09d11a5ca5344c81daf0fbe6`, test feed `8707b5e23316d0be83bc80fd366e2cff80dd6e10`. Public/staged/installed на двух AVD совпадают. [Дизайн, обоснование и проверки](DESIGN_BETA8.md), [постановка движения](MOTION_BETA8.md).

Общий стиль трёх экранов, короткие страницы и один черновик, явное различие примера/активного режима, раздельные профили, нативные слои танка/сердечек/персонажа. Исправлены перехват фокуса каталогом, возврат после ошибки плеера, системный long-OK и утечка отпускания в первый переключатель. 143 JVM PASS;24 различных Android-сценария по отдельным прогонам, последний11/11 PASS за133,524с. Exact signed release прошёл paused-live маршрут и остановку анимации; native26с MP4: `evidence/android/beta8-final-motion.mp4`. Без записи за20с:0 jank/0 deadline missed,p95=8мс на SwiftShader; это не60FPS/physicalTV benchmark.

Настоящий OTA9→10 наsecondary:16/65% оба профиля, THEATER, TOP_RIGHT, clock/animations сохранены; VOD3:25:39→3:26:02 после воспроизведения/паузы. Primary oprawa сохранён. Временные системные параметры восстановлены, оба устройства наrelease10 в каталоге; активных проверок/сборок нет.

Открытые границы: физический ARM TV, настоящий4K (эмулятор ограничил размер1080p), полный длительный performance matrix. Старый software decoder4003 не объявлен исправленным. Следующая содержательная проверка — оценка новой сборки на реальномTV; не повторять OAuth и не выдавать прошлые длительные soak за beta.8.

## Предыдущая доставка — beta.7/code9

[Подписанный APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.7/Twitch20-0.1.0-beta.7.apk), 2 750 382 байта, SHA-256 `b7dd452c6de8ab2e015e894fb54dc45b6bb2ac28e4588579e025de005f77ff37`, прежний сертификат. Source/tag `1fcef8e5fc692653b0a81ffc9ac9e42a0a2a6dc3`; тестовый feed code9, commit `b74a049d65877c15453b971e563ee9e4e385faca`. Public/staged/installed совпали. [Отчёт](DESIGN_BETA7.md).

Исправлен пользовательский конфликт: OFF/OVERLAY/THEATER, отдельные размер/фон и единый редактор; изменение неактивного профиля не включает режим. Первичная доработка каталога и читаемости, не окончательный дизайн. 131 JVM и16 различных Android-сценариев по отдельным прогонам PASS. На exact release профили20/35% и14/85% сохранились после завершения процесса; тестовые изменения возвращены к исходным18/65%. OTA8→9 наsecondary мигрировал legacy theater16/65% и сохранил VOD3:25:36→3:25:39. Primary сохраняет аккаунт oprawa.

Следующее: оценка первого визуального прохода, дальнейшая проработка примеров/переходов и остальных экранов по PLAN. Physical ARM TV и прежний decoder4003 остаются границами проверки.

## Предыдущая доставка — beta.6/code8

**Функциональная доработка доставлена; дизайн остаётся следующим отдельным этапом.** [APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.6/Twitch20-0.1.0-beta.6.apk),2 733 993байта,SHA-256`459ff94be9cb8d356620c3da0e0020d17ca66c2b519c80c22c18b5e5c668ea6c`. Source/tag`d7180982a719bda440b745bbca1d917bfc6329e7`. Тестовыйfeedcode8, public/staged/installed совпали. Последние121JVM PASS и9Android PASS на двухAVD (8guest/59.072с +1auth/22.29с).

Исправлены auth/chat/catalog/playback recovery и обнаруженный после OTA конфликт MediaSession при повторном запуске Activity. Exactrelease8: paused/playing VOD→Home→explicit launch сохраняют тот жеActivity/intent; clip1080→720 сохраняетpaused0:04, ended0:29→reopen0:00. **Настоящий OTA7→8 сохранил theatre/16/65%/clock/animations и VOD2:59**, installerOPEN работает; послеOTA оба relaunch-сценария повторноPASS. Primary5560 вручную установлен exactrelease8, oprawa сохранён; secondary5562 — exactrelease8 послеOTA. Активных сборок/soak/инструментации нет.

Главное ограничение: KSPKSP decoder4003 повторился и в минимальном Media3+SurfaceView, без PlaybackSession/MediaSession/ContentFrame/чата. Причина сочетания потока/Media3/softwareAVC не установлена; физическийTV не проверен. Другой канал на beta.5/cb26… прошёл30мин11.490с/0errors/1rebuffer450ms и22 отдельных открытия. Не переносить эти длительные измерения на beta.6 и не объявлять decoder исправленным. [Полный текущий отчёт](BACKEND_BETA6.md), [производительность](PERFORMANCE.md), [единый план](PLAN.md).

## Предыдущая доставка beta.5

Signed/minified code7: SHA-256 `cb26d9eb713e246725ca70d9e0ebd050a0a3fb30a948a69ae80e45434bd21f10`, 2 733 968 байт. **121 JVM PASS, 8 Android PASS/84.861с**. Реальный network error→Quality360→video и fresh-source Retry с сохранением360 подтверждены;22 первыхстарта/возврата и отдельный30minPASS. OTA6→7 сохранилsettings/VOD1:23. Последующий launch ownership crash исправлен в beta.6.

Предварительный candidate68a655… дважды получил software decoder4003 на720p60 (319.161s и1425.246s). Его23 открытия/возврата и другие native проверки не выдаются за результаты текущего APK. В текущем добавлены выбор качества из ошибки, сохранение ручного выбора при Retry и play/pause intent при фоновом завершении запроса. Системный decoder не объявляется исправленным. Полная матрица: [BACKEND_FINAL.md](BACKEND_FINAL.md). Дизайн отложен по просьбе пользователя.

## Доставленная beta.4

По указанию пользователя доработана внутренняя логика; дизайн отложен. Пагинация Helix после входа, сохранение карточек/курсорный retry, LRU и восстановление авторизации после offline startup реализованы. **Beta.4/code6 опубликован, канал обновлён, OTA5→6 проверен.** Все проверки и ограничения описаны в [BACKEND_BETA4.md](BACKEND_BETA4.md), единый план — [PLAN.md](PLAN.md). Исходники тега: `b5bc36641b7b4ed2218721e9c06ee562dfd91a8b`. Ниже — история доставленной beta.3; её ограничение первой страницей больше не относится к авторизованным каталогам beta.4.

2026-11-08: beta доставлен. Финальный [v0.1.0-beta.3/code5](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.3) опубликован; реальные OTA alpha.2/code2 → beta.2/code4 → beta.3/code5 выполнены через приложение и системный installer. [Результаты](TEST_RESULTS.md), [измерения](PERFORMANCE.md), [выполненный план](PLAN.md), [публикация](RELEASING.md).

## Исторический артефакт beta.3

[APK](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-beta.3/Twitch20-0.1.0-beta.3.apk), 2 717 565 байт, SHA-256 `9b5fc5fc7b836a60b1bb0971cedd0cf3e5e491d298138a72dfb8e3ee8c1cf844`. Public/downloaded/installed APK одинаковы; versionCode5/min23/target36, installer `com.google.android.packageinstaller`. Apksigner v1/v2 PASS, certificate SHA-256 `f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7`.

Source release-tag commit: [13545b8c14942af0529be204d85c91b24a9f3fee](https://github.com/oprawa228-hub/twitch-tv-2.0/commit/13545b8c14942af0529be204d85c91b24a9f3fee). Финальные документы публикуются последующим commit. Beta.3 добавляет CATEGORY_LAUNCHER для системного Open и пояснение продолжения после выдачи install permission; decoder/player policy после beta.2 не менялась.

## Что подтверждено

- Реальные каталог/категории/поиск/канал/long OK/возврат фокуса, live+IRC, clip и VOD. Представительные quality720p60, VOD seek, Home/возврат и overlay/theater проверены native QA.
- Draft preview/Save/Cancel, вложенные настройки и один Back из player settings; TLS UI cleanup crash и поглощённый фокусом Back исправлены/retested.
- Network loss→error2001→restore→Retry→video; шесть успешных live/play/return циклов.
- Реальный OAuth oprawa и followed-live каталог; далее пользователь попросил пропустить дополнительную проверку аккаунта. Не запрашивать повторный личный вход.
- OTA2→4 сохранила18sp/oprawa/follows/VOD position1:28, затем playback1:46. OTA4→5 сохранила18sp/oprawa, Open одним OK запустил каталог. **Нового VOD history теста после5 не было.**
- Первое разрешение REQUEST_INSTALL_PACKAGES завершило app процесс силами Android; после повторного открытия check/download/install успешно завершился. Beta.3 объясняет этот случай пользователю.
- Последние42 unit PASS и5 diagnostic Android PASS/60.562с; финальные beta.3 successful build/signature/installer/Open/retention проверены отдельно. Полного повторного debug test run после двух beta.3 изменений не было.
- Alpha.2 live9мин55.610с и memory85912→94340KB; stable-live/scroll Perfetto. Beta.2 отдельно на5562:4мин22.952с,14382 rendered/9 dropped/50 skipped,0.063% late-drop доля. Начальный Auto160p, последующий ABR не записан. PERF подробно разделяет эти выборки.

## Среда / контракты

- Проект `D:/Project/Twitch TV 2.0`, пакет `com.oprawa.twitch20`, независимый от старого приложения/канала. AGP8.13.2/Gradle8.13/JDK17/Kotlin2.3.10/SDK36/min23/BOM2026.06.01/TV1.1.0/Media3 1.11.1/**OkHttp4.12.1**/Coil3.4.0. OkHttp5.5.1 требует SDK37; не возвращать без осознанного обновления toolchain.
- Primary `Twitch20Test`/5560: API30 x86,1280×720,4core/2GB/SwiftShader. Secondary `Twitch20Release`/5562: тот же образ,2core/1.5GB. Общий host, во время stress были R8/второй emulator; физического TV нет. Старый AVD не изменён.
- Один PlaybackSession на просмотр, панели не владеют ExoPlayer. HTTP cleanup вне main, StrictMode сохранён. Viewing владеет lifecycle; Retry заново получает signed URL. Back перехватывается до Compose focus traversal, действие на uncancelled UP.
- OAuth AtomicFile/AndroidKeystore AES-GCM; public Client ID допустим в APK, client secret/GitHub credentials не встраивать. Signing key `C:/Users/Opraw/.android/signing/twitch20/release.jks`, локальный backup `D:/Private-Backups/Twitch20-signing`; пароли не выводить. Идентичность подписи сохранить.
- Raw `evidence/`, `.local/` и SDK/cache исключены из Git. Документы называют локальные evidence-пути для воспроизведения на workspace; публичные APK/manifest/checksum доступны через Releases. Root единолично управляет устройствами/build/sign/publish.

## Ограничения и дальнейшее развитие

Это завершённая доставка beta, не обещание проверки всех устройств/условий. Не проверены физический ARM TV/аппаратный decoder/1080p60, многочасовые нагрузки,20+ смен каналов, длительный busy chat, token expiry/refresh/revoke. Представительные VOD seek/Home/resume/quality/search/theater выполнены; расширение комбинаций — последующее покрытие, не незавершённая реализация основных сценариев.

Историческое ограничение beta.3: live30/категории40/VOD-clips30. В beta.4+ авторизованные каталоги имеют курсорную пагинацию; guest остаётся ограниченным первой страницей. Гостевой video использует недокументированный Twitch путь; subscriber-only/Turbo не гарантируются. Public GitHub prereleases не обязательно появляются в `/releases/latest`; updater использует подходящий feed. Для будущих релизов сохранять подпись, повышать versionCode и повторять exact-artifact OTA проверку по RELEASING.md.
