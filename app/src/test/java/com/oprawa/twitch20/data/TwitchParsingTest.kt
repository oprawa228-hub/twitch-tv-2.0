package com.oprawa.twitch20.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TwitchParsingTest {
    // Catches treating HTTP 200 with GQL errors as an empty successful catalog.
    @Test fun graphqlErrorsAreNotAnEmptyCatalog() {
        val failure = runCatching {
            TwitchJson.data("""{"errors":[{"message":"service failed token=DO_NOT_EXPOSE","extensions":{"code":"FORBIDDEN"}}],"data":null}""")
        }.exceptionOrNull()
        assertTrue(failure is TwitchException)
        assertEquals(TwitchError.ACCESS_DENIED, (failure as TwitchException).reason)
        assertFalse(failure.message.orEmpty().contains("DO_NOT_EXPOSE"))
    }

    // Catches accepting absent gated/offline media as an empty playback URL.
    @Test fun missingPlaybackTokenHasExplicitUnavailableState() {
        val failure = runCatching {
            TwitchJson.playbackToken(JSONObject("""{"streamPlaybackAccessToken":null}"""), "streamPlaybackAccessToken")
        }.exceptionOrNull()
        assertTrue(failure is TwitchException)
        assertEquals(TwitchError.UNAVAILABLE, (failure as TwitchException).reason)
    }

    // Catches ignoring restriction flags even when a signed token is returned.
    @Test fun deniedPlaybackIsNotTreatedAsPlayable() {
        val json = JSONObject().put("videoPlaybackAccessToken", JSONObject()
            .put("signature", "test-signature")
            .put("value", """{"authorization":{"forbidden":true},"vod_id":123}"""))
        val failure = runCatching { TwitchJson.playbackToken(json, "videoPlaybackAccessToken") }.exceptionOrNull()
        assertTrue(failure is TwitchException)
        assertEquals(TwitchError.ACCESS_DENIED, (failure as TwitchException).reason)
    }

    // Catches converting offline search results to live cards or rendering JSON null.
    @Test fun offlineChannelKeepsItsIdentityAndOfflineState() {
        val channel = TwitchJson.channel(JSONObject("""{"id":"12","login":"example","displayName":"Example","profileImageURL":"https://example.test/avatar.png","stream":null}"""))
        assertEquals("example", channel.login)
        assertEquals("Example", channel.name)
        assertFalse(channel.live)
        assertEquals(0, channel.viewers)
        assertFalse(channel.title.contains("null"))
    }

    @Test fun malformedResponseIsReportedAsServiceError() {
        val failure = runCatching { TwitchJson.data("<html>gateway failure</html>") }.exceptionOrNull()
        assertTrue(failure is TwitchException)
        assertEquals(TwitchError.SERVICE, (failure as TwitchException).reason)
    }
}
