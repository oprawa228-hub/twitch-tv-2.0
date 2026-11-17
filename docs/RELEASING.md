# Выпуск обновления

Пакет `com.oprawa.twitch20` и постоянный сертификат сохраняются. Сборка использует локальный `signing.properties`, исключённый из Git. Закрытый ключ и резервная копия хранятся вне репозитория. Для обновления используйте существующий ключ.

1. Проверьте изменения, относящиеся к ним тесты и работу release APK на Android TV. Управление общим устройством и выпуском выполняет один ответственный.
2. Сверьте последний публичный выпуск, локальные кандидаты и [манифест тестового канала](../updates/test.json). Задайте новый `releaseName` и `releaseCode` выше опубликованного. Для сохранения текущего способа входа и запуска видео задайте проверенный `legacyTvClientId` при сборке. Подробнее о профиле воспроизведения в [отчёте beta.12](RELEASE_BETA12.md).
3. Выполните `python tools/stage_release.py app/build/outputs/apk/release/app-release.apk --notes <файл-описания>`. Скрипт проверяет пакет и сертификат, создаёт APK, `update.json`, `SHA256SUMS.txt` в `dist/v<versionName>` и запрещает замену отличающегося артефакта той же версии. Укажите тот же текст в описании GitHub Release. Манифест даёт приложению описание при недоступности основного API. Для apksigner нужен JAVA_HOME и локальный Android SDK из README.
4. Опубликуйте проверенные исходники. Создайте релиз и загрузите все три файла из PowerShell по [инструкции для GitHub CLI](#публикация-через-github-cli). Для беты включите prerelease. Уже опубликованный APK не перезаписывайте.
5. Скачайте APK по публичной ссылке и сравните SHA-256. Только после успешной публикации перенесите его `update.json` в `updates/test.json`. Для обычного стабильного выпуска добавьте `updates/stable.json`, опубликуйте обычный release и проверьте GitHub Latest. Сейчас стабильного релиза нет.
6. На предыдущей подписанной версии выберите нужный канал, проверьте, скачайте и подтвердите обновление системным установщиком. Разрешение установки из этого приложения выдаётся один раз; если Android закрыл приложение, откройте его и повторите проверку/загрузку.
7. Сверьте установленный APK с опубликованным, версию, настройки, историю и аккаунт. Запишите устройство, результаты и ограничения в TEST_RESULTS/HANDOFF. `adb install` не заменяет проверку OTA.

GitHub Release API — основной источник; при 403/429 приложение использует закреплённый JSON в этом же репозитории. Метаданные всегда ссылаются на неизменяемый APK конкретного релиза. Личный GitHub-токен приложению не нужен. Секретов CI нет: сборка и подпись выполняются локально.

## Публикация через GitHub CLI

Путь к GitHub CLI указан относительно корня проекта в команде ниже. Публикуйте APK напрямую через CLI. Браузер нужен только для восстановления входа, если авторизация истекла.

Из корня проекта выполните в PowerShell:

```powershell
$gh = (Resolve-Path '.\.local\github-cli-2.104.0\bin\gh.exe').Path
$repo = 'oprawa228-hub/twitch-tv-2.0'
& $gh api "repos/$repo" --jq '{repo: .full_name, push: .permissions.push}'
```

Ожидаемый результат — репозиторий `oprawa228-hub/twitch-tv-2.0` и `push: true`. Если CLI сообщает об отсутствии авторизации, выполните `& $gh auth login --hostname github.com --git-protocol https --web`, подтвердите вход в браузере и повторите проверку.

Для нового тестового релиза задайте три значения в той же сессии. Укажите версию проверенного APK, полный SHA уже опубликованного коммита с его исходниками и путь к тому же файлу описания, который передали в `stage_release.py --notes`. Для нового тега `--target` привязывает выпуск к указанному коммиту. Если тег уже создан, сначала сверьте его коммит с проверенным SHA и используйте `--verify-tag` вместо `--target $sourceSha`.

```powershell
$version = '<versionName кандидата>'
$sourceSha = '<полный SHA проверенного коммита>'
$notesFile = '<путь к файлу описания>'

$tag = "v$version"
$dir = Join-Path 'dist' $tag
$apk = Join-Path $dir "Twitch20-$version.apk"
$manifest = Join-Path $dir 'update.json'
$checksums = Join-Path $dir 'SHA256SUMS.txt'

& $gh release create $tag $apk $manifest $checksums --repo $repo --target $sourceSha --prerelease --title "Twitch 2.0 $version" --notes-file $notesFile
if ($LASTEXITCODE -ne 0) { throw 'Проверьте состояние релиза перед повторной загрузкой.' }
& $gh release view $tag --repo $repo --json tagName,isDraft,isPrerelease,assets,url --jq '{tag: .tagName, draft: .isDraft, prerelease: .isPrerelease, files: [.assets[].name], url: .url}'
```

Проверьте тег, `draft: false`, `prerelease: true` и наличие APK, `update.json`, `SHA256SUMS.txt`. Затем выполните шаги 5–7 выше. Если загрузка прервалась и на GitHub остался черновик, посмотрите его вложения через `release view`, дозагрузите только отсутствующий файл командой `& $gh release upload $tag <путь-к-файлу> --repo $repo` и после сверки опубликуйте черновик командой `& $gh release edit $tag --repo $repo --draft=false`.
