package com.oprawa.twitch20.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClipSourcesTest {
    private fun clip(vararg rows: String) = JSONObject().put("videoQualities", JSONArray(rows.toList().map(::JSONObject)))
        .put("playbackAccessToken", JSONObject().put("signature", "test-signature")
            .put("value", """{"authorization":{"forbidden":false},"test":"private-value"}"""))

    @Test fun preservesAvailableQualitiesAndSignsEverySourceWithoutLosingFrameRate() {
        val variants = ClipSources.parse(clip(
            """{"quality":"360","frameRate":30,"sourceURL":"https://example.test/360.mp4"}""",
            """{"quality":"720","frameRate":30,"sourceURL":"https://example.test/720-30.mp4"}""",
            """{"quality":"1080","frameRate":59.94,"sourceURL":"https://example.test/1080.mp4?sig=old&token=old"}""",
            """{"quality":"720","frameRate":60,"sourceURL":"https://example.test/720-60.mp4"}""",
        ))
        assertEquals(listOf("1080p60", "720p60", "720p", "360p"), variants.map { it.label })
        assertEquals(4, variants.map { it.id }.distinct().size)
        variants.forEach {
            assertEquals("test-signature", it.url.toHttpUrl().queryParameter("sig"))
            assertTrue(it.url.toHttpUrl().queryParameter("token")!!.contains("private-value"))
            assertFalse(it.toString().contains("private-value"))
            assertFalse(it.toString().contains("example.test"))
        }
    }

    @Test fun ignoresInvalidSourcesAndDeduplicatesEquivalentQualityChoices() {
        val variants = ClipSources.parse(clip(
            """{"quality":"720","frameRate":60,"sourceURL":"http://example.test/insecure.mp4"}""",
            """{"quality":"720","frameRate":60,"sourceURL":"https://example.test/a.mp4"}""",
            """{"quality":"720","frameRate":60,"sourceURL":"https://example.test/duplicate.mp4"}""",
            """{"quality":"360","frameRate":30,"sourceURL":"not-a-url"}""",
        ))
        assertEquals(1, variants.size)
        assertEquals("/a.mp4", variants.single().url.toHttpUrl().encodedPath)
    }

    @Test fun emptyOrUnplayableClipProducesAnExplicitUnavailableError() {
        val error = runCatching { ClipSources.parse(clip()) }.exceptionOrNull()
        assertTrue(error is TwitchException)
        assertEquals(TwitchError.UNAVAILABLE, (error as TwitchException).reason)
    }

    @Test fun accessDeniedCannotBecomeSelectableVariants() {
        val json = clip("""{"quality":"360","sourceURL":"https://example.test/a.mp4"}""")
        json.getJSONObject("playbackAccessToken").put("value", """{"authorization":{"forbidden":true}}""")
        val error = runCatching { ClipSources.parse(json) }.exceptionOrNull()
        assertTrue(error is TwitchException)
        assertEquals(TwitchError.ACCESS_DENIED, (error as TwitchException).reason)
    }
}
