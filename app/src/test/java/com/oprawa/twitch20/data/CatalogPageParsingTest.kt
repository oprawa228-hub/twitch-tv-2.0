package com.oprawa.twitch20.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogPageParsingTest {
    // Dropping the opaque cursor silently limits every catalog to its first page.
    @Test fun preservesCursorOnShortAndEmptyPages() {
        val short = HelixCatalogJson.page(JSONObject("""{"data":[{"id":"a"}],"pagination":{"cursor":"opaque+/=|next"}}""")) { it.getString("id") }
        assertEquals(listOf("a"), short.items)
        assertEquals("opaque+/=|next", short.nextCursor)
        val empty = HelixCatalogJson.page(JSONObject("""{"data":[],"pagination":{"cursor":"next"}}""")) { it.getString("id") }
        assertTrue(empty.items.isEmpty())
        assertEquals("next", empty.nextCursor)
    }

    @Test fun emptyPaginationObjectMeansExhaustion() {
        val page = HelixCatalogJson.page(JSONObject("""{"data":[{"id":"last"}],"pagination":{}}""")) { it.getString("id") }
        assertEquals(listOf("last"), page.items)
        assertNull(page.nextCursor)
    }

    // Missing pagination must not be mistaken for the documented empty object.
    @Test fun malformedPaginationAndNullItemsAreServiceErrors() {
        listOf(
            """{"data":[]}""", """{"data":[],"pagination":null}""",
            """{"data":[],"pagination":[]}""", """{"data":[],"pagination":{"cursor":null}}""",
            """{"data":[],"pagination":{"cursor":42}}""", """{"data":[],"pagination":{"cursor":" "}}""",
            """{"data":null,"pagination":{}}""", """{"data":[null],"pagination":{}}""",
        ).forEach { raw -> assertServiceError { HelixCatalogJson.page(JSONObject(raw)) { it.getString("id") } } }
    }

    // Request parameters and parsed fields are tested together at the HTTP boundary.
    @Test fun streamsContinueWithinTheSelectedCategoryAndKeepBroadcasterIdentity() = runTest {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val repository = HelixCatalogRepository(get = { endpoint, parameters ->
            calls += endpoint to parameters.toMap()
            JSONObject("""{"data":[{"id":"stream-9","user_id":"user-1","user_login":"example","user_name":"Example","title":"Live title","game_name":"Game","thumbnail_url":"https://example.test/{width}x{height}.jpg","viewer_count":52}],"pagination":{"cursor":"next+/="}}""")
        })
        val first = repository.streamsPage("category-1")
        repository.streamsPage("category-1", first.nextCursor)
        assertEquals("streams", calls[0].first)
        assertFalse(calls[0].second.containsKey("after"))
        assertEquals("category-1", calls[1].second["game_id"])
        assertEquals("next+/=", calls[1].second["after"])
        assertEquals("30", calls[1].second["first"])
        assertEquals(Channel("user-1", "example", "Example", "Live title", "Game", "https://example.test/640x360.jpg", 52, true), first.items.single())
    }

    @Test fun globalStreamsOmitCategoryFilter() = runTest {
        val repository = HelixCatalogRepository(get = { endpoint, parameters ->
            assertEquals("streams", endpoint)
            assertFalse(parameters.containsKey("game_id"))
            JSONObject("""{"data":[],"pagination":{}}""")
        })
        assertNull(repository.streamsPage().nextCursor)
    }

    @Test fun categoriesUseTopGamesAndResolveBoxArtPlaceholders() = runTest {
        val repository = HelixCatalogRepository(get = { endpoint, parameters ->
            assertEquals("games/top", endpoint)
            assertEquals("game-cursor", parameters["after"])
            JSONObject("""{"data":[{"id":"g1","name":"Category","box_art_url":"https://example.test/{width}x{height}.jpg"}],"pagination":{}}""")
        })
        assertEquals(Category("g1", "Category", "https://example.test/285x380.jpg"), repository.categoriesPage("game-cursor").items.single())
    }

    @Test fun videosResolveLoginAndRetainAfterSortAndAllTypes() = runTest {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val repository = HelixCatalogRepository(get = { endpoint, parameters ->
            calls += endpoint to parameters.toMap()
            if (endpoint == "users") JSONObject("""{"data":[{"id":"u1","login":"example"}]}""")
            else JSONObject("""{"data":[{"id":"v1","title":"Recording","thumbnail_url":"https://example.test/%{width}x%{height}.jpg","duration":"1h2m3s","view_count":14}],"pagination":{"cursor":"vod-next"}}""")
        })
        val page = repository.videosPage("example", "vod-after")
        assertEquals("users" to mapOf("login" to "example"), calls[0])
        assertEquals("videos", calls[1].first)
        assertEquals("u1", calls[1].second["user_id"])
        assertEquals("vod-after", calls[1].second["after"])
        assertEquals("time", calls[1].second["sort"])
        assertEquals("all", calls[1].second["type"])
        assertEquals(Video("v1", "Recording", "https://example.test/320x180.jpg", 3723, "vod", "example", 14), page.items.single())
        assertEquals("vod-next", page.nextCursor)
    }

    // Moving the time window between requests invalidates the meaning of a clip cursor.
    @Test fun clipsUseOneFixedWeekAcrossPagesAndKeepSlugAsPlaybackId() = runTest {
        val clipsCalls = mutableListOf<Map<String, String>>()
        val repository = HelixCatalogRepository(snapshotTimeMillis = 604_800_000L, get = { endpoint, parameters ->
            if (endpoint == "users") JSONObject("""{"data":[{"id":"u1","login":"example"}]}""")
            else {
                assertEquals("clips", endpoint)
                clipsCalls += parameters.toMap()
                JSONObject("""{"data":[{"id":"ClipSlug","title":"Clip","thumbnail_url":"https://example.test/clip.jpg","duration":12.9,"view_count":23}],"pagination":{"cursor":"clip-next"}}""")
            }
        })
        val first = repository.clipsPage("example")
        repository.clipsPage("example", first.nextCursor)
        assertEquals(Video("ClipSlug", "Clip", "https://example.test/clip.jpg", 12, "clip", "example", 23), first.items.single())
        assertEquals("u1", clipsCalls[0]["broadcaster_id"])
        assertEquals("1970-01-01T00:00:00Z", clipsCalls[0]["started_at"])
        assertEquals("1970-01-08T00:00:00Z", clipsCalls[0]["ended_at"])
        assertEquals(clipsCalls[0]["started_at"], clipsCalls[1]["started_at"])
        assertEquals(clipsCalls[0]["ended_at"], clipsCalls[1]["ended_at"])
        assertFalse(clipsCalls[0].containsKey("after"))
        assertEquals("clip-next", clipsCalls[1]["after"])
    }

    @Test fun unknownLoginDoesNotQueryAnotherUsersVideos() = runTest {
        var calls = 0
        val repository = HelixCatalogRepository(get = { endpoint, _ ->
            calls++
            assertEquals("users", endpoint)
            JSONObject("""{"data":[]}""")
        })
        val failure = runCatching { repository.videosPage("missing") }.exceptionOrNull()
        assertTrue(failure is TwitchException)
        assertEquals(TwitchError.UNAVAILABLE, (failure as TwitchException).reason)
        assertEquals(1, calls)
    }

    @Test fun durationHandlesOmittedUnitsAndRejectsMalformedOrOverflowValues() {
        mapOf("1h2m3s" to 3723, "3m21s" to 201, "2h" to 7200, "45s" to 45, "0s" to 0)
            .forEach { (raw, seconds) -> assertEquals(seconds, HelixCatalogJson.durationSeconds(raw)) }
        listOf("", "1hgarbage", "-2s", "9999999999999999999999h").forEach { raw ->
            assertServiceError { HelixCatalogJson.durationSeconds(raw) }
        }
    }

    // The repository must not turn cancellation/network/auth failure into a successful end.
    @Test fun transportFailureAndCancellationArePropagated() = runTest {
        listOf(IOException("offline"), CancellationException("cancelled"), TwitchException(TwitchError.ACCESS_DENIED))
            .forEach { expected ->
                val repository = HelixCatalogRepository(get = { _, _ -> throw expected })
                assertSame(expected, runCatching { repository.categoriesPage("after") }.exceptionOrNull())
            }
    }

    private fun assertServiceError(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected a safe service error, got $failure", failure is TwitchException)
        assertEquals(TwitchError.SERVICE, (failure as TwitchException).reason)
    }
}
