package com.oprawa.twitch20.data

import com.oprawa.twitch20.auth.StoredSession
import java.util.UUID
import kotlin.random.Random
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Experimental wire protocol. No network, logging, storage or Android UI dependencies. */
internal object LegacyLiveAccess {
    fun request(login: String, session: StoredSession, enabled: Boolean, configuredClientId: String): Request {
        check(enabled && configuredClientId.isNotBlank() && session.clientId == configuredClientId)
        val body = JSONObject("""{
            "operationName":"PlaybackAccessToken",
            "extensions":{"persistedQuery":{"version":1,"sha256Hash":"ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"}},
            "variables":{"isLive":true,"isVod":false,"platform":"web","playerType":"site","vodID":""}
        }""")
        body.getJSONObject("variables").put("login", login)
        return Request.Builder().url("https://gql.twitch.tv/gql")
            .header("Client-ID", session.clientId)
            .header("Authorization", "OAuth ${session.accessToken}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }

    fun masterUrl(login: String, response: String, expectedUserId: String): String {
        val token = TwitchJson.playbackToken(TwitchJson.data(response), "streamPlaybackAccessToken")
        if (expectedUserId.isBlank() || JSONObject(token.value).optString("user_id") != expectedUserId) {
            throw TwitchException(TwitchError.ACCESS_DENIED)
        }
        return "https://usher.ttvnw.net/api/channel/hls/".toHttpUrl().newBuilder()
            .addPathSegment("$login.m3u8")
            .addQueryParameter("sig", token.signature).addQueryParameter("token", token.value)
            .addQueryParameter("player_backend", "mediaplayer")
            .addQueryParameter("reassignments_supported", "true")
            .addQueryParameter("playlist_include_framerate", "true")
            .addQueryParameter("allow_source", "true").addQueryParameter("fast_bread", "false")
            .addQueryParameter("cdm", "wv").addQueryParameter("acmb", "e30=")
            .addQueryParameter("p", Random.nextInt(100_000_000).toString())
            .addQueryParameter("play_session_id", UUID.randomUUID().toString().replace("-", ""))
            .addQueryParameter("player_version", "1.13.0").addQueryParameter("supported_codecs", "avc1")
            .build().toString()
    }
}
