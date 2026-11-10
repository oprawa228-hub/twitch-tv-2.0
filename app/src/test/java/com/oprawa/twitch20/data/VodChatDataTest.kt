package com.oprawa.twitch20.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodChatDataTest {
    @Test fun replayUsesPlaybackTimeAndKeepsPagingAcrossDeletedMessages() {
        val page = VodChatData.parse(JSONObject("""
            {"edges":[
                {"node":{"id":"future","contentOffsetSeconds":14,"commenter":{"displayName":"Viewer"},
                    "message":{"fragments":[{"text":"Later"}]}}},
                {"node":{"id":"current","contentOffsetSeconds":10.5,"commenter":{"displayName":"Viewer"},
                    "message":{"fragments":[{"text":"Hello "},{"text":"there"}]}}},
                {"node":{"id":"deleted","contentOffsetSeconds":19,"commenter":null}}
            ],"pageInfo":{"hasNextPage":true}}
        """.trimIndent()), 10)
        assertEquals(19000L, page.throughMs)
        assertTrue(page.hasMore)
        assertTrue(replayMessagesAt(page.messages, 10499).isEmpty())
        assertEquals(listOf("Hello there"), replayMessagesAt(page.messages, 10500).map { it.text })
        assertEquals(listOf("current", "future"), replayMessagesAt(page.messages, 14000).map { it.id })
    }
}
