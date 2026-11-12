package com.oprawa.twitch20.auth

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TwitchAuthTest {
    @Test fun pendingIsNotAFailedLogin() {
        assertEquals(PollDecision.PENDING, AuthProtocol.pollDecision(400, JSONObject("""{"message":"authorization_pending"}""")))
    }
    @Test fun slowDownUsesServerErrorField() {
        assertEquals(PollDecision.SLOW_DOWN, AuthProtocol.pollDecision(400, JSONObject("""{"error":"slow_down"}""")))
    }
    @Test fun deniedAndExpiredCodesStopPolling() {
        assertEquals(PollDecision.DENIED, AuthProtocol.pollDecision(400, JSONObject("""{"error":"access_denied"}""")))
        assertEquals(PollDecision.EXPIRED, AuthProtocol.pollDecision(400, JSONObject("""{"message":"expired_token"}""")))
    }
    @Test fun successWithoutTokenIsNotAuthenticated() {
        assertEquals(PollDecision.FAILED, AuthProtocol.pollDecision(200, JSONObject("""{"access_token":"","refresh_token":"refresh"}""")))
        assertEquals(PollDecision.FAILED, AuthProtocol.pollDecision(200, JSONObject("""{"access_token":null,"refresh_token":null}""")))
    }
    @Test fun tokenAndDeviceObjectsNeverExposeCredentialsInToString() {
        val session = StoredSession("public-id", "secret-access", "secret-refresh", "viewer", "1")
        val device = DeviceAuthorization("ABCD", "https://www.twitch.tv/activate", "secret-device", 600, 5, 0)
        assertFalse(session.toString().contains("secret"))
        assertFalse(device.toString().contains("secret"))
    }

    @Test fun deviceCodeLifetimeUsesItsOriginalDeadline() {
        val device = DeviceAuthorization("ABCD", "https://www.twitch.tv/activate", "fixture", 30, 5, 0,
            issuedAtMillis = 100_000L)
        assertEquals(30_000L, device.remainingMillis(100_000L))
        assertEquals(1_501L, device.remainingMillis(128_499L))
        assertEquals(0L, device.remainingMillis(130_000L))
        assertEquals(0L, device.remainingMillis(150_000L))
    }

    @Test fun offlineFollowRetainsIdentityWithoutInventingAnActiveStream() {
        val entry = JSONObject("""{"broadcaster_id":"42","broadcaster_login":"offline_user","broadcaster_name":"Offline User","followed_at":"2024-01-01T00:00:00Z"}""")
        val channels = FollowedCatalog.merge(listOf(entry), emptyList())
        assertEquals(1, channels.size)
        assertEquals("42", channels.single().id)
        assertEquals("offline_user", channels.single().login)
        assertEquals("Offline User", channels.single().name)
        assertFalse(channels.single().live)
        assertEquals(0, channels.single().viewers)
        assertEquals("", channels.single().imageUrl)
    }

    @Test fun liveFollowUsesStreamPreviewAndSortsAheadOfOfflineChannel() {
        val offline = JSONObject("""{"broadcaster_id":"1","broadcaster_login":"alice","broadcaster_name":"Alice"}""")
        val followed = JSONObject("""{"broadcaster_id":"2","broadcaster_login":"bob","broadcaster_name":"Bob"}""")
        val live = JSONObject("""{"user_id":"2","user_login":"bob","user_name":"Bob","title":"Real stream","game_name":"Chess","viewer_count":23,"thumbnail_url":"https://example.test/{width}x{height}.jpg"}""")
        val channels = FollowedCatalog.merge(listOf(offline, followed), listOf(live))
        assertEquals(listOf("2", "1"), channels.map { it.id })
        assertTrue(channels.first().live)
        assertEquals("Real stream", channels.first().title)
        assertEquals("https://example.test/640x360.jpg", channels.first().imageUrl)
        assertFalse(channels.last().live)
    }

    @Test fun followedPaginationDeduplicatesChannelsAcrossPages() {
        val pages = HelixPageCollector("broadcaster_id")
        assertEquals("next", pages.append(JSONObject("""{"data":[{"broadcaster_id":"1"}],"pagination":{"cursor":"next"}}""")))
        assertNull(pages.append(JSONObject("""{"data":[{"broadcaster_id":"1"},{"broadcaster_id":"2"}],"pagination":{}}""")))
        assertEquals(listOf("1", "2"), pages.items.map { it.getString("broadcaster_id") })
    }

    @Test fun repeatedCursorStopsInsteadOfLoopingForever() {
        val pages = HelixPageCollector("broadcaster_id")
        val page = JSONObject("""{"data":[{"broadcaster_id":"1"}],"pagination":{"cursor":"repeated"}}""")
        pages.append(page)
        assertTrue(runCatching { pages.append(page) }.exceptionOrNull() is AuthException)
    }

    @Test fun overLimitIsAnExplicitErrorRatherThanASilentlyTruncatedList() {
        val pages = HelixPageCollector("broadcaster_id", maxEntries = 2)
        val page = JSONObject("""{"data":[{"broadcaster_id":"1"},{"broadcaster_id":"2"},{"broadcaster_id":"3"}],"pagination":{}}""")
        assertTrue(runCatching { pages.append(page) }.exceptionOrNull() is AuthException)
    }
}
