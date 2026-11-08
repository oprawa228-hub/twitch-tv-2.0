package com.oprawa.twitch20.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONException
import org.json.JSONObject

/** Official catalog metadata only. Authentication and refresh belong to the supplied transport. */
class HelixCatalogRepository(
    private val get: suspend (String, Map<String, String>) -> JSONObject,
    snapshotTimeMillis: Long = System.currentTimeMillis(),
) {
    // Keep both ends stable while paging. Create a new repository to refresh the time window.
    private val clipsWindow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).run {
        timeZone = TimeZone.getTimeZone("UTC")
        mapOf(
            "started_at" to format(Date(snapshotTimeMillis - 7L * 24 * 60 * 60 * 1000)),
            "ended_at" to format(Date(snapshotTimeMillis)),
        )
    }

    suspend fun streamsPage(categoryId: String? = null, after: String? = null): CatalogPage<Channel> {
        val parameters = pageParameters(30, after).apply {
            categoryId?.let { put("game_id", it) }
        }
        return HelixCatalogJson.page(get("streams", parameters)) { stream ->
            val login = HelixCatalogJson.required(stream, "user_login")
            Channel(
                id = HelixCatalogJson.required(stream, "user_id"),
                login = login,
                name = TwitchJson.text(stream, "user_name").ifBlank { login },
                title = TwitchJson.text(stream, "title"),
                category = TwitchJson.text(stream, "game_name"),
                imageUrl = thumbnail(TwitchJson.text(stream, "thumbnail_url"), 640, 360),
                viewers = HelixCatalogJson.count(stream, "viewer_count"),
                live = true,
            )
        }
    }

    suspend fun categoriesPage(after: String? = null): CatalogPage<Category> =
        HelixCatalogJson.page(get("games/top", pageParameters(40, after))) { category ->
            Category(
                HelixCatalogJson.required(category, "id"), HelixCatalogJson.required(category, "name"),
                thumbnail(TwitchJson.text(category, "box_art_url"), 285, 380),
            )
        }

    suspend fun videosPage(login: String, after: String? = null): CatalogPage<Video> {
        val parameters = pageParameters(30, after).apply {
            put("user_id", userId(login))
            put("sort", "time")
            put("type", "all")
        }
        return HelixCatalogJson.page(get("videos", parameters)) { video ->
            Video(
                HelixCatalogJson.required(video, "id"), TwitchJson.text(video, "title"),
                thumbnail(TwitchJson.text(video, "thumbnail_url"), 320, 180),
                HelixCatalogJson.durationSeconds(HelixCatalogJson.required(video, "duration")),
                "vod", login, HelixCatalogJson.count(video, "view_count"),
            )
        }
    }

    suspend fun clipsPage(login: String, after: String? = null): CatalogPage<Video> {
        val parameters = pageParameters(30, after).apply {
            put("broadcaster_id", userId(login))
            putAll(clipsWindow)
        }
        return HelixCatalogJson.page(get("clips", parameters)) { clip ->
            val duration = (clip.opt("duration") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }
                ?: throw TwitchException(TwitchError.SERVICE)
            Video(
                HelixCatalogJson.required(clip, "id"), TwitchJson.text(clip, "title"),
                TwitchJson.text(clip, "thumbnail_url"), duration.toInt(),
                "clip", login, HelixCatalogJson.count(clip, "view_count"),
            )
        }
    }

    private suspend fun userId(login: String): String {
        if (login.isBlank()) throw TwitchException(TwitchError.UNAVAILABLE)
        val users = get("users", mapOf("login" to login)).optJSONArray("data")
            ?: throw TwitchException(TwitchError.SERVICE)
        if (users.length() == 0) throw TwitchException(TwitchError.UNAVAILABLE)
        return HelixCatalogJson.required(users.optJSONObject(0) ?: throw TwitchException(TwitchError.SERVICE), "id")
    }

    private fun pageParameters(size: Int, after: String?) = mutableMapOf("first" to size.toString()).apply {
        after?.let { put("after", it) }
    }

    private fun thumbnail(value: String, width: Int, height: Int): String = value
        .replace("%{width}", width.toString()).replace("%{height}", height.toString())
        .replace("{width}", width.toString()).replace("{height}", height.toString())
}

internal object HelixCatalogJson {
    fun <T> page(response: JSONObject, mapper: (JSONObject) -> T): CatalogPage<T> {
        val data = response.optJSONArray("data") ?: throw TwitchException(TwitchError.SERVICE)
        val pagination = response.optJSONObject("pagination") ?: throw TwitchException(TwitchError.SERVICE)
        val cursor = if (pagination.has("cursor")) required(pagination, "cursor") else null
        val items = try {
            (0 until data.length()).map { index ->
                mapper(data.optJSONObject(index) ?: throw TwitchException(TwitchError.SERVICE))
            }
        } catch (_: JSONException) {
            throw TwitchException(TwitchError.SERVICE)
        }
        return CatalogPage(items, cursor)
    }

    fun required(json: JSONObject, key: String): String =
        (json.opt(key) as? String)?.takeIf(String::isNotBlank) ?: throw TwitchException(TwitchError.SERVICE)

    fun count(json: JSONObject, key: String): Int = json.optLong(key, 0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun durationSeconds(value: String): Int {
        val match = DURATION.matchEntire(value.takeIf(String::isNotBlank) ?: throw TwitchException(TwitchError.SERVICE))
            ?: throw TwitchException(TwitchError.SERVICE)
        val parts = (1..3).map { index ->
            val raw = match.groupValues[index]
            if (raw.isEmpty()) 0L else raw.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE }
                ?: throw TwitchException(TwitchError.SERVICE)
        }
        val seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
        if (seconds > Int.MAX_VALUE) throw TwitchException(TwitchError.SERVICE)
        return seconds.toInt()
    }

    private val DURATION = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?")
}
