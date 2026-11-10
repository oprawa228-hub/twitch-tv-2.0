package com.oprawa.twitch20.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

data class UpdateState(
    val status: String = "idle",
    val message: String = "Проверьте, доступна ли новая версия",
    val progress: Float = 0f,
    val available: UpdateRelease? = null,
    val currentNotes: String? = null,
    val progressKnown: Boolean = false,
    val latestPublished: UpdateRelease? = null,
)

/** No credentials; only the new project's public release channel is reachable. */
class UpdateManager(context: Context) {
    private companion object {
        // Cancellation handlers can run on Main. Socket close/cancel must never run there.
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun cleanup(action: () -> Unit) { cleanupScope.launch { runCatching(action) } }
    }
    private val app = context.applicationContext
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val operationGate = UpdateOperationGate()
    private var operationToken = 0L
    @Volatile private var closed = false
    @Volatile private var job: Job? = null
    @Volatile private var call: Call? = null
    private var verifiedFile: File? = null
    private val folder get() = File(app.cacheDir, "updates").also { it.mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.MINUTES).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            val hosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "raw.githubusercontent.com")
            if (!url.isHttps || url.host !in hosts || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty())
                throw IOException("Небезопасный адрес загрузки")
            if (url.host == "raw.githubusercontent.com") {
                if (chain.call().request().url.host != "raw.githubusercontent.com")
                    throw IOException("Перенаправление APK на raw запрещено")
                try { UpdatePolicy.validateRawManifestUrl(url.toString()) }
                catch (_: IllegalArgumentException) { throw IOException("Недопустимый адрес описания обновления") }
            }
            chain.proceed(chain.request())
        }.build()

    suspend fun check(includePrerelease: Boolean = true) {
        cancel()
        execute("checking", "Проверяем GitHub…") {
            verifiedFile = null
            val installed = installedInfo()
            val currentVersion = version(installed)
            val installedCert = certificate(installed)
            val feed = "https://api.github.com/repos/${UpdatePolicy.REPOSITORY}/releases"
            val releases = try {
                if (includePrerelease) {
                    val array = JSONArray(readText("$feed?per_page=30", 2 * 1024 * 1024)!!)
                    (0 until array.length()).map { array.getJSONObject(it) }
                } else {
                    readText("$feed/latest", 1024 * 1024, missingIsEmpty = true)?.let { listOf(JSONObject(it)) } ?: emptyList()
                }
            } catch (failure: HttpFailure) {
                if (failure.status != 403 && failure.status != 429) throw failure
                checkRawChannel(includePrerelease, currentVersion, installedCert)
                return@execute
            }
            var latestPublished: UpdateRelease? = null
            var currentNotes: String? = null
            var manifestFailures = 0
            for (release in releases) {
                currentCoroutineContext().ensureActive()
                if (release.optBoolean("draft") || (!includePrerelease && release.optBoolean("prerelease"))) continue
                val assets = release.optJSONArray("assets") ?: continue
                val manifests = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name") == "update.json" }
                if (manifests.size != 1) continue
                try {
                    val url = manifests.single().getString("browser_download_url")
                    UpdatePolicy.validateAssetUrl(url, ".json")
                    val manifest = parseRelease(JSONObject(readText(url, 64 * 1024)!!))
                    // APK and manifest must be assets of this exact release, not just the repository.
                    val prefix = url.substringBeforeLast('/') + "/"
                    require(manifest.apkUrl.startsWith(prefix)) { "APK относится к другому выпуску" }
                    val publishedAsset = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .singleOrNull { it.optString("browser_download_url") == manifest.apkUrl }
                    require(publishedAsset != null && publishedAsset.optLong("size") in 1..UpdatePolicy.MAX_APK_BYTES) {
                        "Файл APK отсутствует или слишком велик"
                    }
                    val notes = release.opt("body")?.let { it as? String }?.trim()?.takeIf { it.isNotBlank() }
                    try {
                        UpdatePolicy.validateRelease(manifest, 0, Build.VERSION.SDK_INT, installedCert)
                    } catch (invalid: IllegalArgumentException) {
                        if (manifest.versionCode < currentVersion) continue
                        throw invalid
                    }
                    val published = manifest.copy(notes = notes)
                    if (latestPublished == null || published.versionCode > latestPublished.versionCode) {
                        latestPublished = published
                    }
                    if (published.versionCode == currentVersion && published.versionName == installed.versionName) {
                        if (currentNotes == null) currentNotes = notes
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { manifestFailures++ }
            }
            currentCoroutineContext().ensureActive()
            val newest = latestPublished?.takeIf { it.versionCode > currentVersion }
            publish(when {
                newest != null -> UpdateState("available", "Доступна версия ${newest.versionName}", available = newest,
                    latestPublished = newest)
                manifestFailures > 0 -> UpdateState("error", "Не удалось проверить файлы выпуска. Повторите позже.",
                    latestPublished = latestPublished)
                else -> UpdateState("current", if (includePrerelease) "Новых версий пока нет" else "Новых стабильных версий пока нет",
                    currentNotes = currentNotes, latestPublished = latestPublished)
            })
        }
    }

    suspend fun download() {
        val release = mutable.value.available ?: return
        cancel()
        execute("downloading", "Скачиваем ${release.versionName}…", release) {
            val installed = installedInfo()
            UpdatePolicy.validateRelease(release, version(installed), Build.VERSION.SDK_INT, certificate(installed))
            val part = File(folder, "update-${release.versionCode}.part")
            val target = File(folder, "update-${release.versionCode}.apk")
            verifiedFile = null
            try {
                request(release.apkUrl).use { response ->
                    require(response.isSuccessful) { "GitHub не отдал файл обновления" }
                    val body = response.body ?: throw IOException("Пустой файл обновления")
                    val expected = body.contentLength()
                    require(expected <= UpdatePolicy.MAX_APK_BYTES) { "Файл обновления слишком велик" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var lastProgress = -1
                    body.byteStream().use { source ->
                        part.outputStream().use { destination ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= UpdatePolicy.MAX_APK_BYTES) { "Файл обновления слишком велик" }
                                destination.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                val progress = if (expected > 0) (total.toDouble() / expected).toFloat().coerceIn(0f, 1f) else 0f
                                val percent = (progress * 100).toInt()
                                if (percent != lastProgress) {
                                    publish(UpdateState("downloading", "Скачиваем ${release.versionName}…", progress, release,
                                        progressKnown = expected > 0))
                                    lastProgress = percent
                                }
                            }
                            destination.fd.sync()
                        }
                    }
                    require(total > 0 && (expected < 0 || total == expected)) { "Загрузка прервалась" }
                    require(hex(digest.digest()).equals(release.sha256, ignoreCase = true)) { "Контрольная сумма APK не совпала" }
                }
                currentCoroutineContext().ensureActive()
                verifyArchive(part, release)
                currentCoroutineContext().ensureActive()
                if (target.exists()) check(target.delete()) { "Не удалось заменить прежнюю загрузку" }
                check(part.renameTo(target)) { "Не удалось сохранить проверенный APK" }
                verifiedFile = target
                removeOlderDownloads(target)
                publish(UpdateState("ready", "Файл проверен. Подтвердите установку в Android.", 1f, release))
            } finally {
                part.delete()
            }
        }
    }

    fun cancel() {
        var active: Pair<Call?, Job?> = null to null
        operationGate.invalidate {
            if (mutable.value.status in setOf("checking", "downloading"))
                mutable.value = mutable.value.copy(status = "cancelled", message = "Операция отменена", progress = 0f)
            active = call to job
        }
        cleanup { active.first?.cancel() }
        active.second?.cancel()
    }

    fun close() {
        closed = true
        cancel()
        verifiedFile = null
        cleanup {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /** Called by the Activity; Android owns the final install confirmation. */
    fun install(activity: Activity) {
        if (closed) return
        val release = mutable.value.available ?: return
        val apk = verifiedFile?.takeIf { it.isFile } ?: run {
            mutable.value = mutable.value.copy(status = "error", message = "Сначала скачайте обновление")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
                mutable.value = mutable.value.copy(status = "permission",
                    message = "Разрешите обновления из Twitch 2.0. Если Android закроет приложение, откройте его снова и повторите проверку и загрузку.")
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
                return
            }
            // App-private cache plus digest/package checks above prevents another app replacing the APK.
            val current = installedInfo()
            UpdatePolicy.validateRelease(release, version(current), Build.VERSION.SDK_INT, certificate(current))
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            mutable.value = mutable.value.copy(status = "installing", message = "Подтвердите обновление в установщике Android")
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(status = "error", message = "Не удалось открыть установщик. Повторите загрузку или установку.")
        }
    }

    private suspend fun execute(status: String, message: String, release: UpdateRelease? = null, action: suspend () -> Unit) {
        if (closed) return
        // Reserve identity before either dispatch or mutex suspension. Cancel invalidates queued work too.
        val token = operationGate.issue()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val runningJob = currentCoroutineContext()[Job]
                var registered = false
                operationGate.admit(token) {
                    if (!closed) {
                        operationToken = token
                        job = runningJob
                        mutable.value = UpdateState(status, message, available = release)
                        registered = true
                    }
                }
                if (!registered) return@withLock
                try { action() }
                catch (cancelled: CancellationException) {
                    publish(mutable.value.copy(status = "cancelled", message = "Операция отменена"))
                    throw cancelled
                } catch (_: Exception) {
                    publish(UpdateState("error", "Не удалось получить проверенное обновление. Проверьте сеть и повторите.", available = release))
                } finally {
                    val finished = call
                    cleanup { finished?.cancel() }
                    call = null
                    job = null
                }
            }
        }
    }

    private fun publish(value: UpdateState) {
        operationGate.admit(operationToken) { mutable.value = value }
    }

    /** REST limits are shared by IP; the fallback remains pinned to our two repository files. */
    private suspend fun checkRawChannel(includePrerelease: Boolean, currentVersion: Long, installedCert: String) {
        val url = UpdatePolicy.rawManifestUrl(includePrerelease)
        UpdatePolicy.validateRawManifestUrl(url)
        val json = readText(url, 64 * 1024, missingIsEmpty = true)
        val release = json?.let { parseRelease(JSONObject(it)) }
        if (release != null) {
            // Validate all metadata even when the channel points to the currently installed version.
            // Actual upgrade/downgrade eligibility is compared below and again before download.
            UpdatePolicy.validateRelease(release, 0, Build.VERSION.SDK_INT, installedCert)
        }
        currentCoroutineContext().ensureActive()
        publish(if (release != null && release.versionCode > currentVersion)
            UpdateState("available", "Доступна версия ${release.versionName}", available = release, latestPublished = release)
        else UpdateState("current", if (includePrerelease) "Новых версий пока нет" else "Новых стабильных версий пока нет",
            latestPublished = release))
    }

    private class HttpFailure(val status: Int) : IOException("Сервис обновления недоступен")

    private suspend fun request(url: String): Response = suspendCancellableCoroutine { continuation ->
        val pending = client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Twitch20-AndroidTV").build())
        call = pending
        continuation.invokeOnCancellation { cleanup { pending.cancel() } }
        pending.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> cleanup { value.close() } }
            }
        })
    }

    private suspend fun readText(url: String, limit: Int, missingIsEmpty: Boolean = false): String? =
        request(url).use { response ->
            if (missingIsEmpty && response.code == 404) return@use null
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val body = response.body ?: throw IOException("Пустой ответ")
            check(body.contentLength() <= limit) { "Слишком большой ответ" }
            body.byteStream().use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= limit) { "Слишком большой ответ" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }

    private fun parseRelease(json: JSONObject) = UpdateRelease(
        json.getString("packageName"), json.getLong("versionCode"), json.getString("versionName"),
        json.getInt("minSdk"), json.getString("apkUrl"), json.getString("sha256"), json.getString("certificateSha256"),
    )

    /** Called only after the new archive verifies and only from execute's IO dispatcher. */
    private fun removeOlderDownloads(keep: File) {
        val root = folder.canonicalFile
        val keepPath = keep.canonicalFile
        require(keepPath.parentFile == root) { "Недопустимый каталог обновления" }
        root.listFiles()?.forEach { candidate ->
            if (candidate.name.matches(Regex("update-[0-9]+\\.(apk|part)")) && candidate.isFile &&
                candidate.canonicalFile.parentFile == root && candidate.canonicalFile != keepPath) {
                check(candidate.delete()) { "Не удалось очистить прежнее обновление" }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo() = app.packageManager.getPackageInfo(app.packageName, signingFlags())

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, release: UpdateRelease) {
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
            ?: throw IllegalArgumentException("Android не распознал APK")
        val minSdk = if (Build.VERSION.SDK_INT >= 24) info.applicationInfo?.minSdkVersion
            ?: throw IllegalArgumentException("Неизвестная совместимость APK") else release.minSdk
        // API 23 does not expose archive minSdkVersion; the manifest is checked and Android checks installation.
        UpdatePolicy.validateArchive(release, info.packageName, version(info), minSdk,
            certificate(info), certificate(installedInfo()))
    }

    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signingFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun certificate(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        require(signatures != null && signatures.size == 1) { "Неподдерживаемая подпись APK" }
        return hex(MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray()))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
