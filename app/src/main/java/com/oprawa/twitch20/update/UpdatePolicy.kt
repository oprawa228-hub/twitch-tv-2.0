package com.oprawa.twitch20.update

import java.net.URI

data class UpdateRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val notes: String? = null,
)

object UpdatePolicy {
    const val PACKAGE = "com.oprawa.twitch20"
    const val REPOSITORY = "oprawa228-hub/twitch-tv-2.0"
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    fun rawManifestUrl(includePrerelease: Boolean): String =
        "https://raw.githubusercontent.com/$REPOSITORY/main/updates/${if (includePrerelease) "test" else "stable"}.json"

    fun validateRawManifestUrl(url: String) {
        require(url == rawManifestUrl(true) || url == rawManifestUrl(false)) {
            "Резервный канал доступен только по закреплённому адресу проекта"
        }
    }
    private val digest = Regex("[0-9a-fA-F]{64}")

    fun validateAssetUrl(url: String, extension: String) {
        require(extension == ".apk" || extension == ".json") { "Неподдерживаемый тип файла" }
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.host == "github.com" &&
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Обновления доступны только по защищённым ссылкам GitHub"
        }
        val prefix = "/$REPOSITORY/releases/download/"
        val path = uri.rawPath ?: ""
        require(path.startsWith(prefix) && uri.normalize().rawPath == path && '%' !in path && '\\' !in path) {
            "Ссылка обновления не принадлежит этому проекту"
        }
        val parts = path.removePrefix(prefix).split('/')
        require(parts.size == 2 && parts.all { it.isNotEmpty() && it != "." && it != ".." &&
            it.matches(Regex("[a-zA-Z0-9._+-]+")) } && parts.last().endsWith(extension)) {
            "Нужен файл конкретного выпуска"
        }
    }
    fun validateRelease(release: UpdateRelease, installedVersion: Long, sdk: Int, installedCertificate: String) {
        require(release.packageName == PACKAGE) { "Это обновление другого приложения" }
        require(release.versionCode > installedVersion && release.versionCode > 0) { "Уже установлена эта или более новая версия" }
        require(release.versionName.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9.+_-]{0,63}"))) { "Неизвестная версия выпуска" }
        require(release.minSdk > 0 && release.minSdk <= sdk) { "Обновление требует более новую версию Android" }
        validateAssetUrl(release.apkUrl, ".apk")
        require(digest.matches(release.sha256)) { "Неверная контрольная сумма выпуска" }
        require(digest.matches(release.certificateSha256) && digest.matches(installedCertificate) &&
            release.certificateSha256.equals(installedCertificate, ignoreCase = true)) { "Подпись выпуска не совпала с приложением" }
    }
    fun validateArchive(release: UpdateRelease, packageName: String, versionCode: Long,
        minSdk: Int, certificate: String, installedCertificate: String) {
        require(packageName == PACKAGE && packageName == release.packageName) { "APK принадлежит другому приложению" }
        require(versionCode == release.versionCode) { "Версия APK не совпала с описанием" }
        require(minSdk == release.minSdk && minSdk > 0) { "Совместимость APK не совпала с описанием" }
        require(digest.matches(certificate) && digest.matches(installedCertificate) &&
            certificate.equals(release.certificateSha256, ignoreCase = true) &&
            certificate.equals(installedCertificate, ignoreCase = true)) { "Подпись APK не совпала с установленной версией" }
    }
}
