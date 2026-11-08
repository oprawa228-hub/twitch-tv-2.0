package com.oprawa.twitch20.data

import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.roundToInt

internal object ClipSources {
    fun parse(clip: JSONObject): List<VideoVariant> {
        val token = TwitchJson.playbackToken(clip, "playbackAccessToken")
        val rows = clip.optJSONArray("videoQualities") ?: throw TwitchException(TwitchError.UNAVAILABLE)
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val url = TwitchJson.text(row, "sourceURL").toHttpUrlOrNull()
                ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
                ?: return@mapNotNull null
            val height = TwitchJson.text(row, "quality").toIntOrNull()?.coerceAtLeast(0) ?: 0
            val fps = row.optDouble("frameRate", 0.0).takeIf { it.isFinite() && it in 0.0..240.0 }
                ?.roundToInt() ?: 0
            Triple(height, fps, VideoVariant("clip:$height:$fps",
                if (height > 0) "${height}p${if (fps > 30) fps.toString() else ""}" else "Исходное",
                url.newBuilder().setQueryParameter("sig", token.signature)
                    .setQueryParameter("token", token.value).build().toString()))
        }.sortedWith(compareByDescending<Triple<Int, Int, VideoVariant>> { it.first }.thenByDescending { it.second })
            .map { it.third }.distinctBy { it.id }
            .ifEmpty { throw TwitchException(TwitchError.UNAVAILABLE) }
    }
}
