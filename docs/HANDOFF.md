# Передача результата Twitch 2.0

## Текущий этап beta.4

По указанию пользователя продолжается внутренняя логика; дизайн отложен. Пагинация Helix после входа, сохранение карточек/курсорный retry, LRU и восстановление авторизации после offline startup реализованы. Текущий кандидат code6 и все проверки/оставшиеся шаги описаны в [BACKEND_BETA4.md](BACKEND_BETA4.md), единый план — [PLAN.md](PLAN.md). Ниже — история доставленной beta.3; её ограничение первой страницей больше не относится к авторизованным каталогам beta.4.

2026-11-08: beta доставлен. Финальный [v0.1.0-beta.3/code5](https://github.com/oprawa228-hub/twitch-tv-2.0/releases/tag/v0.1.0-beta.3) опубликован; реальные OTA alpha.2/code2 → beta.2/code4 → beta.3/code5 выполнены через приложение и системный installer. [Результаты](TEST_RESULTS.md), [измерения](PERFORMANCE.md), [выполненный план](PLAN.md), [публикация](RELEASING.md).

## Финальный артефакт

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

Общий live каталог ограничен30, категории40, VOD/clips30; бесконечной пагинации нет. Гостевой video использует недокументированный Twitch путь; subscriber-only/Turbo не гарантируются. Public GitHub prereleases не обязательно появляются в `/releases/latest`; updater использует подходящий feed. Для будущих релизов сохранять подпись, повышать versionCode и повторять exact-artifact OTA проверку по RELEASING.md.
