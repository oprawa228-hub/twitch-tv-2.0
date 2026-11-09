package com.oprawa.twitch20.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FollowedRecordingsTest {
    @Test fun recentFeedUsesKnownIdsAndBoundedPagesWithoutLosingTheLastChannel() = runTest {
        val requested = mutableListOf<String>()
        val repo = HelixCatalogRepository(get = { endpoint, args ->
            assertEquals("videos", endpoint)
            assertEquals("archive", args["type"])
            assertEquals("3", args["first"])
            val id = args.getValue("user_id"); requested += id
            JSONObject("""{"data":[{"id":"v$id","title":"Record $id","duration":"1h2m3s","published_at":"2026-11-0${id}T00:00:00Z"}],"pagination":{}}""")
        })
        val channels = (1..7).map { Channel("$it", "channel$it", "Name$it", "", "", "", 0, false) }
        val first = repo.recordingsPage(channels)
        assertEquals("6", first.nextCursor)
        assertEquals(6, requested.size)
        assertEquals("v6", first.items.first().id)
        assertEquals(3723, first.items.first().durationSeconds)
        val last = repo.recordingsPage(channels, first.nextCursor)
        assertEquals(listOf("v7"), last.items.map { it.id })
        assertNull(last.nextCursor)
        assertEquals((1..7).map { "$it" }, requested)
    }
    @Test fun emptyFollowListMakesNoRequests() = runTest {
        val repo = HelixCatalogRepository(get = { _, _ -> error("Unexpected request") })
        assertTrue(repo.recordingsPage(emptyList()).items.isEmpty())
    }
}
