package com.oprawa.twitch20.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class VodStoryboard internal constructor(
    internal val columns: Int,
    internal val rows: Int,
    private val count: Int,
    private val intervalMs: Long,
    private val sheets: List<HttpUrl>,
    private val readSheet: suspend (HttpUrl) -> Bitmap?,
) {
    private val tilesPerSheet = columns * rows

    internal fun tileIndex(positionMs: Long): Int =
        (positionMs.coerceAtLeast(0) / intervalMs).coerceAtMost(count.toLong() - 1).toInt()

    internal fun sheetIndex(tileIndex: Int): Int = tileIndex / tilesPerSheet
    internal fun tileColumn(tileIndex: Int): Int = tileIndex % columns
    internal fun tileRow(tileIndex: Int): Int = tileIndex % tilesPerSheet / columns
    internal suspend fun sheet(sheetIndex: Int): Bitmap? = sheets.getOrNull(sheetIndex)?.let { readSheet(it) }
}

/** Optional guest preview data. Separate from playback resolution, with no persistent sprite cache. */
class VodStoryboardRepository {
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun load(id: String): VodStoryboard? = withContext(Dispatchers.IO) {
        if (!id.matches(Regex("[0-9]{1,20}"))) return@withContext null
        val query = JSONObject().put("query", "{video(id:\"$id\"){seekPreviewsURL}}")
        val request = Request.Builder().url("https://gql.twitch.tv/gql")
            .header("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko")
            .post(query.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            val response = read(request, 64_000)?.decodeToString() ?: return@withContext null
            val url = JSONObject(response).optJSONObject("data")?.optJSONObject("video")
                ?.optString("seekPreviewsURL")?.toHttpUrlOrNull()?.takeIf(::isStoryboardHost)
                ?: return@withContext null
            val manifest = read(Request.Builder().url(url).build(), 256_000)?.decodeToString()
                ?: return@withContext null
            parseManifest(JSONArray(manifest), url, id, ::readSheet)
        } catch (_: JSONException) {
            null
        }
    }

    private suspend fun readSheet(url: HttpUrl): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = read(Request.Builder().url(url).build(), 3_000_000) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 ||
            bounds.outWidth * bounds.outHeight > 3_000_000) return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private suspend fun read(request: Request, maxBytes: Long): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { call.cancel() })
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

                override fun onResponse(call: Call, response: Response) {
                    val bytes = try {
                        response.use {
                            val source = it.body?.source()
                            if (!it.isSuccessful || source == null || source.request(maxBytes + 1)) null
                            else source.readByteArray()
                        }
                    } catch (_: IOException) {
                        null
                    }
                    continuation.resume(bytes)
                }
            })
        }
}

private fun isStoryboardHost(url: HttpUrl): Boolean = url.isHttps &&
    url.username.isEmpty() && url.password.isEmpty() && url.port == 443 &&
    (url.host.endsWith(".cloudfront.net") || url.host.endsWith(".jtvnw.net") || url.host.endsWith(".ttvnw.net"))

internal fun parseManifest(
    manifest: JSONArray,
    url: HttpUrl,
    id: String,
    readSheet: suspend (HttpUrl) -> Bitmap?,
): VodStoryboard? {
    for (index in manifest.length() - 1 downTo 0) {
        val entry = manifest.optJSONObject(index) ?: continue
        val cols = entry.optInt("cols")
        val rows = entry.optInt("rows")
        val interval = entry.optInt("interval")
        val count = entry.optInt("count")
        val images = entry.optJSONArray("images") ?: continue
        if (cols !in 1..20 || rows !in 1..50 || interval !in 1..86_400 ||
            images.length() !in 1..1_000 || count !in 1..(cols * rows * images.length())) continue
        val sheets = (0 until images.length()).mapNotNull {
            val path = images.optString(it)
            if (!path.startsWith("$id-") || !path.matches(Regex("[A-Za-z0-9_-]+\\.jpe?g"))) null
            else url.resolve(path)
        }
        if (sheets.size == images.length()) return VodStoryboard(cols, rows, count, interval * 1_000L, sheets, readSheet)
    }
    return null
}
