package com.oprawa.twitch20.data

data class Channel(
    val id: String,
    val login: String,
    val name: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val viewers: Int = 0,
    val live: Boolean = true,
    val avatarUrl: String = "",
    val liveStreamId: String = "",
    val description: String = "",
    val bannerUrl: String = "",
    val liveMessage: String = "",
)

data class Category(val id: String, val name: String, val imageUrl: String)

/** Resolved progressive source; URLs may contain short-lived access credentials. */
class VideoVariant(val id: String, val label: String, val url: String) {
    override fun toString(): String = "VideoVariant(id=$id, label=$label, url=redacted)"
}

data class Video(
    val id: String,
    val title: String,
    val imageUrl: String,
    val durationSeconds: Int,
    val kind: String,
    val channelLogin: String,
    val viewers: Int = 0,
)

enum class TwitchError { NETWORK, SERVICE, ACCESS_DENIED, UNAVAILABLE, RATE_LIMITED }

/** Safe for display/logging: never includes server body, token, or signed URL. */
class TwitchException(val reason: TwitchError) : Exception(
    when (reason) {
        TwitchError.NETWORK -> "Не удалось подключиться к Twitch. Проверьте интернет и повторите."
        TwitchError.SERVICE -> "Twitch временно не отвечает или изменил формат данных. Повторите позже."
        TwitchError.ACCESS_DENIED -> "Twitch ограничил доступ к этому видео. Гостевой просмотр недоступен."
        TwitchError.UNAVAILABLE -> "Эфир завершён или видео больше недоступно."
        TwitchError.RATE_LIMITED -> "Twitch просит подождать. Повторите через минуту."
    },
)
