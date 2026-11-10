package com.oprawa.twitch20.data

import com.oprawa.twitch20.chat.ChatMessage
import org.json.JSONObject

internal data class TimedChatMessage(val offsetMs: Long, val message: ChatMessage)
internal data class VodChatPage(val messages: List<TimedChatMessage>, val hasMore: Boolean, val throughMs: Long)

internal fun replayMessagesAt(buffer: List<TimedChatMessage>, positionMs: Long): List<ChatMessage> =
    buffer.asSequence().filter { it.offsetMs <= positionMs }.toList().takeLast(35).map { it.message }

/** Read-only VOD comments. Network/schema changes stay out of the live IRC client. */
internal object VodChatData {
    suspend fun load(id: String, offset: Int,
        gql: suspend (String, JSONObject) -> JSONObject): VodChatPage {
        val data = gql("""
            query(${'$'}id:ID!,${'$'}offset:Int!) {
              video(id:${'$'}id) { comments(contentOffsetSeconds:${'$'}offset) {
                edges { node { id contentOffsetSeconds commenter { id login displayName }
                    message { userColor fragments { text } } } }
                pageInfo { hasNextPage }
              } }
            }
        """.trimIndent(), JSONObject().put("id", id).put("offset", offset))
        val comments = data.optJSONObject("video")?.optJSONObject("comments")
            ?: throw TwitchException(TwitchError.UNAVAILABLE)
        return parse(comments, offset)
    }

    internal fun parse(comments: JSONObject, offset: Int): VodChatPage {
        val edges = comments.optJSONArray("edges") ?: throw TwitchException(TwitchError.SERVICE)
        var through = offset.toLong() * 1000
        val messages = buildList {
            for (i in 0 until edges.length().coerceAtMost(500)) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val seconds = node.optDouble("contentOffsetSeconds")
                if (!seconds.isFinite() || seconds < 0 || seconds > Int.MAX_VALUE) continue
                val time = (seconds * 1000).toLong()
                through = maxOf(through, time)
                val author = node.optJSONObject("commenter") ?: continue
                val msg = node.optJSONObject("message") ?: continue
                val fragments = msg.optJSONArray("fragments") ?: continue
                val body = buildString {
                    for (j in 0 until fragments.length().coerceAtMost(80)) {
                        val fragment = fragments.optJSONObject(j) ?: continue
                        append(TwitchJson.text(fragment, "text").take(500 - length))
                        if (length >= 500) break
                    }
                }
                val messageId = TwitchJson.text(node, "id")
                val name = TwitchJson.text(author, "displayName").take(80)
                if (messageId.isBlank() || name.isBlank() || body.isBlank()) continue
                add(TimedChatMessage(time, ChatMessage(messageId, name, body, TwitchJson.text(msg, "userColor"),
                    TwitchJson.text(author, "login"), TwitchJson.text(author, "id").ifBlank { null })))
            }
        }
        return VodChatPage(messages.sortedBy { it.offsetMs },
            edges.length() > 0 && comments.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true, through)
    }
}
