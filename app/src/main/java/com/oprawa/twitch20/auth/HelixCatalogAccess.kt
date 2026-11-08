package com.oprawa.twitch20.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

internal class HelixReply(val status: Int, val json: JSONObject) {
    override fun toString(): String = "HelixReply(status=$status, body=redacted)"
}

/** Request boundary for authenticated public catalog reads. */
internal object HelixCatalogAccess {
    private val endpoints = setOf("streams", "games/top", "videos", "clips", "users")
    private val queryParameters = setOf("first", "after", "before", "game_id", "language", "user_id",
        "user_login", "type", "period", "sort", "id", "login", "broadcaster_id", "started_at", "ended_at")

    fun url(endpoint: String, parameters: Map<String, String>): HttpUrl {
        if (endpoint !in endpoints || parameters.keys.any { it !in queryParameters }) {
            throw AuthException("Неподдерживаемый запрос каталога Twitch.")
        }
        return "https://api.twitch.tv/helix/$endpoint".toHttpUrl().newBuilder()
            .apply { parameters.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
    }

    suspend fun fetch(
        request: suspend () -> HelixReply,
        refresh: suspend () -> Unit,
        expire: () -> Unit,
    ): JSONObject {
        var response = request()
        if (response.status == 401) {
            refresh()
            response = request()
        }
        if (response.status == 401) {
            expire()
            throw AuthException("Сессия Twitch истекла. Войдите снова.")
        }
        if (response.status != 200) throw AuthException(when (response.status) {
            403 -> "Twitch не разрешил прочитать этот каталог."
            429 -> "Twitch просит подождать. Повторите через минуту."
            else -> "Не удалось получить каталог Twitch. Проверьте интернет и повторите."
        })
        return response.json
    }
}
