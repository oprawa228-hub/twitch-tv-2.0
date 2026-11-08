package com.oprawa.twitch20.playback

import com.oprawa.twitch20.data.VideoVariant
import org.junit.Assert.*
import org.junit.Test

class QualityPreferenceTest {
    private val selected = QualityOption("0:2", "720p60", 720, 60)

    @Test fun `same media retry restores semantics using new track indexes`() {
        val preference = selectedHls()
        preference.beginMedia("live:example")
        val options = listOf(QualityOption("0:2", "1080p60", 1080, 60),
            QualityOption("3:1", "720p60", 720, 60))
        assertEquals("3:1", preference.restoreHls(options)?.id)
    }

    @Test fun `pending same media retry keeps manual menu selection until tracks are replaced`() {
        val preference = selectedHls()
        preference.beginMedia("live:example")
        assertEquals("0:2", preference.pendingHlsId(listOf(selected)))
        assertNull(preference.restoreHls(emptyList()))
        assertEquals("0:2", preference.pendingHlsId(listOf(selected)))
        val fresh = selected.copy(id = "4:0")
        assertEquals("4:0", preference.restoreHls(listOf(fresh))?.id)
        assertEquals("4:0", preference.pendingHlsId(listOf(fresh)))
    }

    @Test fun `displaying an absent pending choice cannot erase the retry preference`() {
        val preference = selectedHls()
        assertNull(preference.pendingHlsId(emptyList()))
        assertEquals("0:2", preference.pendingHlsId(listOf(selected)))
        preference.rememberHls(null)
        assertNull(preference.pendingHlsId(listOf(selected)))
    }

    @Test fun `error recovery displays the newly requested manual choice before fresh tracks`() {
        val failed = PlaybackState(status = PlaybackStatus.Error, error = "decoder failure",
            selectedQualityId = "0:2", qualityOptions = listOf(selected, QualityOption("0:3", "480p")))
        val recovery = qualityRecoveryPlan(failed, selectedId = "0:3")
        assertEquals("0:3", recovery.state.selectedQualityId)
        assertEquals(PlaybackStatus.Buffering, recovery.state.status)
        assertNull(recovery.state.error)
    }

    @Test fun `explicit Auto during error clears the old manual checkmark`() {
        val failed = PlaybackState(status = PlaybackStatus.Error, error = "decoder failure", selectedQualityId = "0:2")
        assertNull(qualityRecoveryPlan(failed, selectedId = null).state.selectedQualityId)
    }

    @Test fun `height and frame rate distinguish otherwise identical menu labels`() {
        val preference = QualityPreference()
        preference.beginMedia("vod:123")
        preference.rememberHls(QualityOption("0:0", "720p", 720, 30))
        val choices = listOf(QualityOption("1:0", "720p", 720, 25), QualityOption("1:1", "720p", 720, 30))
        assertEquals("1:1", preference.restoreHls(choices)?.id)
    }

    @Test fun `empty tracks during reset do not erase a pending selection`() {
        val preference = selectedHls()
        assertNull(preference.restoreHls(emptyList()))
        assertEquals("2:0", preference.restoreHls(listOf(selected.copy(id = "2:0")))?.id)
    }

    @Test fun `missing supported quality falls back to Auto and clears old preference`() {
        val preference = selectedHls()
        assertEquals(selected, preference.restoreHls(listOf(selected)))
        assertNull(preference.restoreHls(listOf(QualityOption("0:0", "480p", 480, 30))))
        assertNull(preference.restoreHls(listOf(selected)))
    }

    @Test fun `choosing Auto clears retry selection`() {
        val preference = selectedHls()
        assertEquals(selected, preference.restoreHls(listOf(selected)))
        preference.rememberHls(null)
        preference.beginMedia("live:example")
        assertNull(preference.restoreHls(listOf(selected)))
    }

    @Test fun `different media never inherits old manual quality`() {
        val preference = selectedHls()
        assertEquals(selected, preference.restoreHls(listOf(selected)))
        preference.beginMedia("live:different")
        assertNull(preference.restoreHls(listOf(selected)))
    }

    @Test fun `clip retry uses refreshed URL for selected stable variant id`() {
        val preference = QualityPreference()
        preference.beginMedia("clip:example")
        preference.rememberProgressive("480")
        preference.beginMedia("clip:example")
        val high = VideoVariant("1080", "1080p", "https://example.test/new-high")
        val low = VideoVariant("480", "480p", "https://example.test/new-low")
        assertSame(low, preference.restoreProgressive(listOf(high, low)))
    }

    @Test fun `missing clip variant uses default and forgets unavailable preference`() {
        val preference = QualityPreference()
        preference.beginMedia("clip:example")
        preference.rememberProgressive("480")
        val high = VideoVariant("1080", "1080p", "https://example.test/high")
        val low = VideoVariant("480", "480p", "https://example.test/low")
        assertSame(low, preference.restoreProgressive(listOf(high, low)))
        assertSame(high, preference.restoreProgressive(listOf(high)))
        assertSame(high, preference.restoreProgressive(listOf(high, low)))
    }

    @Test fun `quality recovery clears failure and preserves VOD position and pause intent`() {
        for (requested in listOf(false, true)) {
            val failed = PlaybackState(mediaId = "vod:123", status = PlaybackStatus.Error,
                positionMs = 42_000, playWhenReady = requested, error = "decoder failure",
                qualityOptions = listOf(selected))
            val recovery = qualityRecoveryPlan(failed)
            assertNull(recovery.state.error)
            assertEquals(PlaybackStatus.Buffering, recovery.state.status)
            assertFalse(recovery.state.isPlaying)
            assertEquals(requested, recovery.state.playWhenReady)
            assertEquals(42_000L, recovery.seekPositionMs)
            assertEquals(failed.qualityOptions, recovery.state.qualityOptions)
        }
    }

    @Test fun `live quality recovery targets live edge without forcing play`() {
        val failed = PlaybackState(mediaId = "live:example", isLive = true,
            status = PlaybackStatus.Error, positionMs = 42_000, playWhenReady = false, error = "decoder failure")
        val recovery = qualityRecoveryPlan(failed)
        assertNull(recovery.seekPositionMs)
        assertFalse(recovery.state.playWhenReady)
        assertNull(recovery.state.error)
    }

    private fun selectedHls() = QualityPreference().apply {
        beginMedia("live:example")
        rememberHls(selected)
    }
}
