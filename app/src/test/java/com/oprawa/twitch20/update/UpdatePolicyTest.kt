package com.oprawa.twitch20.update

import org.junit.Assert.assertThrows
import org.junit.Test

class UpdatePolicyTest {
    private val cert = "c".repeat(64)
    private val valid = UpdateRelease("com.oprawa.twitch20", 2, "0.1.0-alpha.2", 23,
        "https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v0.1.0-alpha.2/app.apk",
        "a".repeat(64), cert)

    @Test fun rawFallbackAllowsOnlyTwoExplicitChannelManifests() {
        UpdatePolicy.validateRawManifestUrl(UpdatePolicy.rawManifestUrl(true))
        UpdatePolicy.validateRawManifestUrl(UpdatePolicy.rawManifestUrl(false))
    }

    @Test fun rawFallbackRejectsOtherRepositoriesBranchesFilesQueriesAndApk() {
        val url = UpdatePolicy.rawManifestUrl(true)
        listOf(url.replace("twitch-tv-2.0", "twitch-tv-by-oprawa"), url.replace("/main/", "/other/"),
            url.replace("test.json", "app.apk"), url + "?alternate=true", url + "#fragment",
            url.replace("https:", "http:"), url.replace("raw.githubusercontent.com", "user@raw.githubusercontent.com"),
            url.replace("/updates/", "/updates/../updates/"))
            .forEach { candidate -> assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.validateRawManifestUrl(candidate)
            } }
        assertThrows(IllegalArgumentException::class.java) {
            UpdatePolicy.validateRelease(valid.copy(apkUrl = url.replace("test.json", "app.apk")), 1, 30, cert)
        }
    }

    @Test fun acceptsNewCompatibleSignedRelease() { UpdatePolicy.validateRelease(valid, 1, 30, cert) }
    @Test fun acceptsExactDownloadedArchive() {
        UpdatePolicy.validateArchive(valid, valid.packageName, 2, 23, cert, cert)
    }
    @Test fun rejectsOtherRepositoryAndMutableAndCredentialUrls() {
        listOf(
            valid.apkUrl.replace("twitch-tv-2.0", "twitch-tv-by-oprawa"),
            valid.apkUrl.replace("github.com", "github.com.evil.example"),
            valid.apkUrl.replace("https:", "http:"),
            "https://github.com/oprawa228-hub/twitch-tv-2.0/releases/latest/download/app.apk",
            "https://user@github.com/oprawa228-hub/twitch-tv-2.0/releases/download/v2/app.apk",
            valid.apkUrl + "?redirect=other",
            valid.apkUrl.replace("/v0.1.0-alpha.2/", "/%2e%2e/"),
            valid.apkUrl.replace("/v0.1.0-alpha.2/", "/../"),
        ).forEach { url -> assertThrows(IllegalArgumentException::class.java) {
            UpdatePolicy.validateRelease(valid.copy(apkUrl = url), 1, 30, cert)
        } }
    }
    @Test fun rejectsWrongPackageOlderVersionAndUnsupportedSdk() {
        listOf(valid.copy(packageName = "com.oprawa.twitchtv.test"),
            valid.copy(versionCode = 1), valid.copy(versionCode = 0), valid.copy(minSdk = 31))
            .forEach { release -> assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.validateRelease(release, 1, 30, cert)
            } }
    }
    @Test fun rejectsMalformedDigestAndChangedCertificate() {
        listOf(valid.copy(sha256 = "abc"), valid.copy(sha256 = "z".repeat(64)),
            valid.copy(certificateSha256 = "d".repeat(64)), valid.copy(certificateSha256 = ""))
            .forEach { release -> assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.validateRelease(release, 1, 30, cert)
            } }
    }
    @Test fun rejectsArchiveMetadataMismatchOrSignerChange() {
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateArchive(valid, "other", 2, 23, cert, cert) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateArchive(valid, valid.packageName, 3, 23, cert, cert) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateArchive(valid, valid.packageName, 2, 25, cert, cert) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateArchive(valid, valid.packageName, 2, 23, "d".repeat(64), cert) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateArchive(valid, valid.packageName, 2, 23, cert, "d".repeat(64)) }
    }
    @Test fun manifestMustAlsoBeImmutableSameRepositoryAsset() {
        UpdatePolicy.validateAssetUrl(valid.apkUrl.replace("app.apk", "update.json"), ".json")
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validateAssetUrl("https://example.com/update.json", ".json") }
    }
}
