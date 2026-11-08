package com.oprawa.twitch20.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HelixChannelTest {
    @Test fun liveChannelHeaderUsesOnlyOfficialUsersAndStreams() = runTest {
        val endpoints = mutableListOf<String>()
        val repo = HelixCatalogRepository(get = { endpoint, parameters ->
            endpoints += endpoint
            when (endpoint) {
                "users" -> { assertEquals("example", parameters["login"])
                    JSONObject("""{"data":[{"id":"42","login":"example","display_name":"Example","profile_image_url":"https://example.test/avatar"}]}""") }
                "streams" -> { assertEquals("42", parameters["user_id"])
                    JSONObject("""{"data":[{"title":"Live","game_name":"Game","thumbnail_url":"https://example.test/{width}x{height}","viewer_count":73}]}""") }
                else -> throw AssertionError("Unexpected API: $endpoint")
            }
        })
        val channel = repo.channel("example")
        assertEquals(listOf("users", "streams"), endpoints)
        assertEquals("42", channel.id)
        assertTrue(channel.live)
        assertEquals("Live", channel.title)
        assertEquals("https://example.test/640x360", channel.imageUrl)
    }

    @Test fun offlineHeaderRetainsProfileAndDoesNotInventALiveStream() = runTest {
        val repo = HelixCatalogRepository(get = { endpoint, _ ->
            JSONObject(if (endpoint == "users") """{"data":[{"id":"42","login":"example","display_name":"Example","profile_image_url":"https://example.test/avatar"}]}"""
                else """{"data":[]}""")
        })
        val channel = repo.channel("example")
        assertFalse(channel.live)
        assertEquals("Example", channel.name)
        assertEquals("https://example.test/avatar", channel.imageUrl)
    }
}
