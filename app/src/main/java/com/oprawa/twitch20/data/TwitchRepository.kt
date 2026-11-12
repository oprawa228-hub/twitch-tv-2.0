package com.oprawa.twitch20.data

import android.util.Log
import com.oprawa.twitch20.BuildConfig
import com.oprawa.twitch20.auth.StoredSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Isolates Twitch's undocumented public website integration.
 * The standard resolver is anonymous. Account playback is limited to the local test build.
 * Returned media URLs are short-lived capabilities: never persist or log them.
 */
class TwitchRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun streams(categoryId: String? = null): List<Channel> {
        val data = if (categoryId == null) {
            gql("{ streams(first:30) { edges { node { $STREAM_FIELDS } } } }")
        } else {
            gql("query(\$id:ID!) { game(id:\$id) { streams(first:40) { edges { node { $STREAM_FIELDS } } } } }", JSONObject().put("id", categoryId))
                .optJSONObject("game") ?: throw TwitchException(TwitchError.UNAVAILABLE)
        }
        return TwitchJson.nodes(data.optJSONObject("streams")).map(TwitchJson::stream)
    }

    suspend fun categories(): List<Category> =
        TwitchJson.nodes(gql("{ games(first:40) { edges { node { id name boxArtURL(width:285,height:380) } } } }").optJSONObject("games"))
            .map { Category(TwitchJson.required(it, "id"), TwitchJson.required(it, "name"), TwitchJson.text(it, "boxArtURL")) }

    suspend fun search(query: String): List<Channel> {
        if (query.isBlank()) return emptyList()
        val data = gql(
            "query(\$term:String!) { searchFor(userQuery:\$term, platform:\"web\", options:{targets:[{index:CHANNEL}]}) { channels { items { $CHANNEL_FIELDS } } } }",
            JSONObject().put("term", query.trim().take(100)),
        )
        val items = data.optJSONObject("searchFor")?.optJSONObject("channels")?.optJSONArray("items")
            ?: throw TwitchException(TwitchError.SERVICE)
        return (0 until items.length()).map { TwitchJson.channel(items.getJSONObject(it)) }
    }

    fun catalogSearch(query: String): SearchCatalog {
        val term = query.trim().take(100)
        return SearchCatalog { type -> if (term.isBlank()) emptyList() else searchSection(term, type) }
    }

    private suspend fun searchSection(term: String, type: SearchCatalogType): List<CatalogItem> {
        // Twitch's public search returns only the first requested index on some queries.
        val (index, field, selection) = when (type) {
            SearchCatalogType.Channels -> Triple("CHANNEL", "channels", CHANNEL_FIELDS)
            SearchCatalogType.Categories -> Triple("GAME", "games", "id name boxArtURL(width:285,height:380)")
            SearchCatalogType.Recordings -> Triple("VOD", "videos",
                "id title lengthSeconds previewThumbnailURL(width:640,height:360) owner { login } viewCount createdAt")
        }
        val data = gql("""
            query(${'$'}term:String!) {
              searchFor(userQuery:${'$'}term, platform:"web", options:{targets:[{index:$index}]}) {
                $field { items { $selection } }
              }
            }
        """.trimIndent(), JSONObject().put("term", term))
        val items = data.optJSONObject("searchFor")?.optJSONObject(field)?.optJSONArray("items")
            ?: throw TwitchException(TwitchError.SERVICE)
        return (0 until items.length().coerceAtMost(30)).mapNotNull { position ->
            val row = items.optJSONObject(position) ?: return@mapNotNull null
            when (type) {
                SearchCatalogType.Channels -> CatalogItem.Stream(TwitchJson.channel(row))
                SearchCatalogType.Categories -> CatalogItem.Game(Category(TwitchJson.required(row, "id"),
                    TwitchJson.required(row, "name"), TwitchJson.text(row, "boxArtURL")))
                SearchCatalogType.Recordings -> {
                    val login = row.optJSONObject("owner")?.let { TwitchJson.text(it, "login") }.orEmpty()
                    login.takeIf(String::isNotBlank)?.let { CatalogItem.Recording(vod(row, it)) }
                }
            }
        }
    }

    internal suspend fun replayChat(id: String, offset: Int): VodChatPage =
        VodChatData.load(id, offset, ::gql)

    private fun vod(row: JSONObject, login: String) =
        Video(TwitchJson.required(row, "id"), TwitchJson.text(row, "title"), TwitchJson.text(row, "previewThumbnailURL"),
            row.optInt("lengthSeconds", 0).coerceAtLeast(0), "vod", login,
            row.optInt("viewCount", 0).coerceAtLeast(0), TwitchJson.text(row, "createdAt"))

    suspend fun channel(login: String): Channel {
        val data = gql("query(\$login:String!) { user(login:\$login) { $CHANNEL_FIELDS description offlineImageURL } }", JSONObject().put("login", login))
        return TwitchJson.channel(data.optJSONObject("user") ?: throw TwitchException(TwitchError.UNAVAILABLE))
    }

    suspend fun liveMessage(login: String): String = ChannelExtras.liveMessage(login, ::gql)

    suspend fun videos(login: String): List<Video> {
        val user = gql(
            "query(\$login:String!) { user(login:\$login) { videos(first:30,sort:TIME) { edges { node { id title lengthSeconds previewThumbnailURL(width:640,height:360) viewCount createdAt } } } } }",
            JSONObject().put("login", login),
        ).optJSONObject("user") ?: throw TwitchException(TwitchError.UNAVAILABLE)
        return TwitchJson.nodes(user.optJSONObject("videos")).map { vod(it, login) }
    }

    suspend fun clips(login: String): List<Video> {
        val user = gql(
            "query(\$login:String!) { user(login:\$login) { clips(first:30,criteria:{period:LAST_WEEK}) { edges { node { slug title durationSeconds thumbnailURL(width:640,height:360) viewCount } } } } }",
            JSONObject().put("login", login),
        ).optJSONObject("user") ?: throw TwitchException(TwitchError.UNAVAILABLE)
        return TwitchJson.nodes(user.optJSONObject("clips")).map {
            Video(TwitchJson.required(it, "slug"), TwitchJson.text(it, "title"), TwitchJson.text(it, "thumbnailURL"),
                it.optInt("durationSeconds", 0).coerceAtLeast(0), "clip", login, it.optInt("viewCount", 0).coerceAtLeast(0))
        }
    }

    suspend fun resolveLive(login: String): String {
        val data = gql(
            "query(\$login:String!) { streamPlaybackAccessToken(channelName:\$login,params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:\"site\"}) { value signature } }",
            JSONObject().put("login", login),
        )
        return masterUrl(listOf("api", "channel", "hls", "$login.m3u8"), TwitchJson.playbackToken(data, "streamPlaybackAccessToken"))
    }

    internal suspend fun resolveLegacyLive(login: String, session: StoredSession): String = withContext(Dispatchers.IO) {
        val request = LegacyLiveAccess.request(login, session, BuildConfig.LEGACY_TV_CLIENT_ID.isNotEmpty(), BuildConfig.LEGACY_TV_CLIENT_ID)
        val direct = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val url = LegacyLiveAccess.masterUrl(login, direct.newCall(request).awaitBody(), session.userId)
        Log.i("TwitchPlayback", "legacy_source_resolved account_match=true")
        url
    }

    suspend fun resolveVideo(id: String): String {
        val data = gql(
            "query(\$id:ID!) { videoPlaybackAccessToken(id:\$id,params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:\"site\"}) { value signature } }",
            JSONObject().put("id", id),
        )
        return masterUrl(listOf("vod", "$id.m3u8"), TwitchJson.playbackToken(data, "videoPlaybackAccessToken"))
    }

    suspend fun resolveClip(slug: String): String = resolveClipVariants(slug).first().url

    suspend fun resolveClipVariants(slug: String): List<VideoVariant> {
        val data = gql(
            "query(\$slug:ID!) { clip(slug:\$slug) { videoQualities { quality frameRate sourceURL } playbackAccessToken(params:{platform:\"web\",playerType:\"site\"}) { value signature } } }",
            JSONObject().put("slug", slug),
        )
        val clip = data.optJSONObject("clip") ?: throw TwitchException(TwitchError.UNAVAILABLE)
        return ClipSources.parse(clip)
    }

    private fun masterUrl(path: List<String>, token: PlaybackToken): String {
        val builder = "https://usher.ttvnw.net/".toHttpUrl().newBuilder()
        path.forEach(builder::addPathSegment)
        return builder
            .addQueryParameter("sig", token.signature).addQueryParameter("token", token.value)
            .addQueryParameter("allow_source", "true").addQueryParameter("allow_audio_only", "true")
            .addQueryParameter("supported_codecs", "avc1").addQueryParameter("player", "twitchweb")
            .addQueryParameter("playlist_include_framerate", "true")
            .build().toString()
    }

    private suspend fun gql(query: String, variables: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("https://gql.twitch.tv/gql")
            .header("Client-ID", WEB_CLIENT_ID).post(body).build()
        TwitchJson.data(client.newCall(request).awaitBody())
    }

    private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { cancel() })
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(TwitchException(TwitchError.NETWORK))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw TwitchException(when (it.code) {
                            401, 403 -> TwitchError.ACCESS_DENIED
                            404 -> TwitchError.UNAVAILABLE
                            429 -> TwitchError.RATE_LIMITED
                            else -> TwitchError.SERVICE
                        })
                        val source = it.body?.source() ?: throw TwitchException(TwitchError.SERVICE)
                        source.request(MAX_RESPONSE_BYTES + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) throw TwitchException(TwitchError.SERVICE)
                        val value = source.readUtf8()
                        if (!continuation.isCancelled) continuation.resume(value)
                    }
                } catch (error: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(
                        if (error is TwitchException) error else TwitchException(TwitchError.NETWORK),
                    )
                }
            }
        })
    }

    private companion object {
        // Twitch website identifier, public in upstream Streamlink; not our OAuth client.
        const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        const val STREAM_FIELDS = "id title viewersCount previewImageURL(width:640,height:360) broadcaster { id login displayName profileImageURL(width:150) } game { id name }"
        const val CHANNEL_FIELDS = "id login displayName profileImageURL(width:150) stream { id title viewersCount previewImageURL(width:640,height:360) game { id name } }"
    }
}

internal data class PlaybackToken(val value: String, val signature: String) {
    override fun toString(): String = "PlaybackToken(redacted)"
}

/** Pure parsing shared by the repository and regression tests. */
internal object TwitchJson {
    fun data(raw: String): JSONObject {
        val response = try { JSONObject(raw) } catch (_: JSONException) { throw TwitchException(TwitchError.SERVICE) }
        val errors = response.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val codes = (0 until errors.length()).map { errors.optJSONObject(it)?.optJSONObject("extensions")?.optString("code").orEmpty() }
            throw TwitchException(if (codes.any { it in setOf("FORBIDDEN", "UNAUTHENTICATED", "ACCESS_DENIED") }) TwitchError.ACCESS_DENIED else TwitchError.SERVICE)
        }
        return response.optJSONObject("data") ?: throw TwitchException(TwitchError.SERVICE)
    }

    fun nodes(connection: JSONObject?): List<JSONObject> {
        val edges: JSONArray = connection?.optJSONArray("edges") ?: throw TwitchException(TwitchError.SERVICE)
        return (0 until edges.length()).map {
            edges.optJSONObject(it)?.optJSONObject("node") ?: throw TwitchException(TwitchError.SERVICE)
        }
    }

    fun playbackToken(data: JSONObject, field: String): PlaybackToken {
        val token = data.optJSONObject(field) ?: throw TwitchException(TwitchError.UNAVAILABLE)
        val value = required(token, "value")
        val decoded = try { JSONObject(value) } catch (_: JSONException) { throw TwitchException(TwitchError.SERVICE) }
        if (decoded.optJSONObject("authorization")?.optBoolean("forbidden", false) == true) {
            throw TwitchException(TwitchError.ACCESS_DENIED)
        }
        return PlaybackToken(value, required(token, "signature"))
    }

    fun stream(stream: JSONObject): Channel {
        val broadcaster = stream.optJSONObject("broadcaster") ?: throw TwitchException(TwitchError.SERVICE)
        return channel(broadcaster, stream)
    }

    fun channel(user: JSONObject, stream: JSONObject? = user.optJSONObject("stream")): Channel {
        val name = text(user, "displayName").ifBlank { required(user, "login") }
        return Channel(
            id = required(user, "id"), login = required(user, "login"), name = name,
            title = if (stream != null) text(stream, "title") else "Не в эфире",
            category = stream?.optJSONObject("game")?.let { text(it, "name") }.orEmpty(),
            imageUrl = stream?.let { text(it, "previewImageURL") }?.takeIf(String::isNotBlank) ?: text(user, "profileImageURL"),
            viewers = stream?.optInt("viewersCount", 0)?.coerceAtLeast(0) ?: 0,
            live = stream != null,
            avatarUrl = text(user, "profileImageURL"),
            liveStreamId = stream?.let { text(it, "id") }.orEmpty(),
            description = text(user, "description"),
            bannerUrl = text(user, "offlineImageURL"),
        )
    }

    fun text(json: JSONObject, key: String): String = if (json.isNull(key)) "" else json.optString(key, "")
    fun required(json: JSONObject, key: String): String = text(json, key).takeIf(String::isNotBlank) ?: throw TwitchException(TwitchError.SERVICE)
}
