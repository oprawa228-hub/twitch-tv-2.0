# Текущее состояние

Работа продолжается по docs/TASK.txt, один план docs/PLAN.md. Полное поручение ещё не выполнено.

Код нового приложения, реальные Twitch интеграции, UI, auth/chat, плеер, настройки и updater созданы. Root интегрирует, собирает, проверяет APK. Другие агенты не управляют эмулятором/публикацией.

- Новая папка: `D:/Project/Twitch TV 2.0`.
- Новый репозиторий: https://github.com/oprawa228-hub/twitch-tv-2.0 (публичный, создан, пока README).
- Новый пакет: `com.oprawa.twitch20`.
- Новый emulator: `Twitch20Test`, serial `emulator-5560`.
- JDK17 установлен в `.tools/java` старого проекта и используется read-only; новый SDK в `.tools/android-sdk` этого проекта. Local paths не нужны другим сборщикам.
- Пользователь зарегистрировал Public Twitch app; публичный ClientID в gradle.properties. Не просить токен/пароль.
- Постоянный signing key `C:/Users/Opraw/.android/signing/twitch20/release.jks`; отдельная локальная копия `D:/Private-Backups/Twitch20-signing`. Пароли не выводить и не публиковать. Это локальная резервная копия, не внешняя/offline.
- GitHub connector умеет commits/trees/blobs, но не создание repo/release assets. Browser Chrome signed-in используется для этих действий; локальный git credential helper не имеет GitHub credential.

Следующее: закончить compact player settings, собрать test+release, установить реальный APK и пройти каталог→эфир→back, auth, VOD/clip/chat/settings. Затем измерения, независимый review, две подписанные публикации с OTA и финальная документация. Не считать первую сборку завершением задания.
