package com.oprawa.twitch20.data

import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Best-effort website field, independent of the subscription snapshot and playback. */
internal object ChannelExtras {
    suspend fun liveMessage(
        login: String,
        query: suspend (String, JSONObject) -> JSONObject,
    ): String = withTimeoutOrNull(1_200) {
        try {
            val data = query(
                "query(\$login:String!) { user(login:\$login) { broadcastSettings { liveUpNotificationInfo { isDefault liveUpNotification } } } }",
                JSONObject().put("login", login),
            )
            parseLiveMessage(data)
        } catch (_: TwitchException) {
            ""
        }
    }.orEmpty()

    fun parseLiveMessage(data: JSONObject): String {
        val info = data.optJSONObject("user")
            ?.optJSONObject("broadcastSettings")
            ?.optJSONObject("liveUpNotificationInfo")
            ?: return ""
        if (info.opt("isDefault") != false) return ""
        return (info.opt("liveUpNotification") as? String)?.trim().orEmpty()
    }
}
