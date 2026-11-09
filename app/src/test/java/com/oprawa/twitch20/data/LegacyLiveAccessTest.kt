package com.oprawa.twitch20.data

import com.oprawa.twitch20.auth.StoredSession
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyLiveAccessTest {
    private val session = StoredSession("test-client", "access-secret", "refresh-secret", "test", "123")

    @Test fun `disabled experiment and different client never create an authenticated request`() {
        listOf(false to "test-client", true to "", true to "other-client").forEach { (enabled, client) ->
            assertTrue(runCatching { LegacyLiveAccess.request("test", session, enabled, client) }.isFailure)
        }
    }

    @Test fun `credentials are sent only in headers to the fixed Twitch endpoint`() {
        val request = LegacyLiveAccess.request("test\"login", session, true, "test-client")
        assertEquals("https://gql.twitch.tv/gql", request.url.toString())
        assertEquals("OAuth access-secret", request.header("Authorization"))
        assertEquals("test-client", request.header("Client-ID"))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertEquals("test\"login", JSONObject(body).getJSONObject("variables").getString("login"))
        assertFalse(body.contains(session.accessToken))
        assertFalse(body.contains(session.refreshToken))
        assertFalse(request.url.toString().contains("secret"))
    }

    @Test fun `matching account produces a correctly encoded direct media URL without OAuth`() {
        val value = """{"user_id":123,"note":"a+b&c"}"""
        val url = LegacyLiveAccess.masterUrl("test", response(value), "123").toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("usher.ttvnw.net", url.host)
        assertEquals("/api/channel/hls/test.m3u8", url.encodedPath)
        assertEquals(value, url.queryParameter("token"))
        assertEquals("signed+value&test", url.queryParameter("sig"))
        assertFalse(url.toString().contains(session.accessToken))
        assertFalse(url.toString().contains(session.refreshToken))
    }

    @Test fun `guest missing or different identity cannot silently become account playback`() {
        listOf("{}", """{"user_id":0}""", """{"user_id":999}""", """{"user_id":null}""")
            .forEach { denied(response(it), "123") }
        denied(response("""{"user_id":""}"""), "")
    }

    @Test fun `forbidden media stays denied even with matching identity`() {
        denied(response("""{"user_id":123,"authorization":{"forbidden":true}}"""), "123")
    }

    @Test fun `service error text and signed data never escape through exceptions`() {
        val raw = """{"errors":[{"message":"access-secret refresh-secret signed+value&test","extensions":{"code":"FORBIDDEN"}}]}"""
        denied(raw, "123")
    }

    @Test fun `malformed response has a bounded safe error`() {
        val error = runCatching { LegacyLiveAccess.masterUrl("test", "<html>access-secret</html>", "123") }.exceptionOrNull()
        assertTrue(error is TwitchException)
        assertEquals(TwitchError.SERVICE, (error as TwitchException).reason)
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    private fun response(value: String) = JSONObject().put("data", JSONObject().put("streamPlaybackAccessToken",
        JSONObject().put("value", value).put("signature", "signed+value&test"))).toString()

    private fun denied(raw: String, userId: String) {
        val error = runCatching { LegacyLiveAccess.masterUrl("test", raw, userId) }.exceptionOrNull()
        assertTrue(error is TwitchException)
        assertEquals(TwitchError.ACCESS_DENIED, (error as TwitchException).reason)
        listOf("access-secret", "refresh-secret", "signed+value&test").forEach {
            assertFalse(error.message.orEmpty().contains(it))
        }
    }
}
