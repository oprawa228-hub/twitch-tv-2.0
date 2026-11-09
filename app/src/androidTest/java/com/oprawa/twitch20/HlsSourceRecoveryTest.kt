package com.oprawa.twitch20

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture

/** Actual HLS tracker/decoder; only network I/O is replaced by deterministic local bytes. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class HlsSourceRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun softwareAvcProbeRecreatesDecoderOnResolutionChangeWithoutNewSource() {
        val preferredCodec = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
            .getDecoderInfos("video/avc", false, false).firstOrNull()?.name
        org.junit.Assume.assumeTrue("This diagnostic only targets the named emulator codecs",
            preferredCodec != null && preferredCodec in com.oprawa.twitch20.playback.AvcResolutionCompatibility.DIAGNOSTIC_TARGETS)
        withCodecProbePreference(enabled = true) { assertResolutionChangeDoesNotReloadSource() }
    }

    @Test fun goldfishResolutionChangeRecreatesDecoderWithProductionPolicy() {
        val preferredCodec = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
            .getDecoderInfos("video/avc", false, false).firstOrNull()?.name
        org.junit.Assume.assumeTrue("This production compatibility test requires Goldfish AVC",
            preferredCodec == com.oprawa.twitch20.playback.AvcResolutionCompatibility.GOLDFISH_AVC)
        withCodecProbePreference(enabled = false) { assertResolutionChangeDoesNotReloadSource() }
    }

    @Test fun productionCodecSelectorKeepsOrderAndOtherDecoderCapabilities() {
        val defaults = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
            .getDecoderInfos("video/avc", false, false)
        val adjusted = com.oprawa.twitch20.playback.AvcResolutionCompatibility.selector()
            .getDecoderInfos("video/avc", false, false)
        assertEquals(defaults.map { it.name }, adjusted.map { it.name })
        defaults.zip(adjusted).forEach { (original, selected) ->
            if (original.name == com.oprawa.twitch20.playback.AvcResolutionCompatibility.GOLDFISH_AVC) {
                assertFalse(selected.adaptive)
                assertSame(original.capabilities, selected.capabilities)
                assertEquals(original.mimeType, selected.mimeType)
                assertEquals(original.codecMimeType, selected.codecMimeType)
                assertEquals(original.hardwareAccelerated, selected.hardwareAccelerated)
                assertEquals(original.softwareOnly, selected.softwareOnly)
                assertEquals(original.vendor, selected.vendor)
                assertEquals(original.secure, selected.secure)
            } else assertSame("Unaffected codecs must remain untouched", original, selected)
        }
    }

    private fun withCodecProbePreference(enabled: Boolean, block: () -> Unit) {
        val prefs = instrumentation.targetContext.getSharedPreferences("playback-diagnostics", android.content.Context.MODE_PRIVATE)
        val existed = prefs.contains("recreate_software_avc")
        val previous = prefs.getBoolean("recreate_software_avc", false)
        prefs.edit().putBoolean("recreate_software_avc", enabled).commit()
        try { block() } finally {
            val editor = prefs.edit()
            if (existed) editor.putBoolean("recreate_software_avc", previous) else editor.remove("recreate_software_avc")
            editor.commit()
        }
    }

    private fun assertResolutionChangeDoesNotReloadSource() = withFixture(resolutionTransition = true) { session, source ->
        val recreated = AtomicBoolean()
        onMain {
            session.player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onVideoInputFormatChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                    if (format.height == 90 && decoderReuseEvaluation?.oldFormat?.height == 180 &&
                        decoderReuseEvaluation.result == androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO)
                        recreated.set(true)
                }
            })
        }
        await("Resolution transition did not recreate the affected decoder", timeoutMs = 8_000) { recreated.get() }
        await("Decoder did not continue with the second resolution", timeoutMs = 4_000) {
            session.player.videoFormat?.height == 90 && session.player.currentPosition > 2_500
        }
        assertEquals("Codec transition must not reload master", 1, source.masterLoads.get())
        assertEquals(0, source.failures.get())
    }

    @Test fun foregroundLiveReturnDoesNotReplayStartZeroAndPausedReturnKeepsPosition() = withFixture(startZero = true) { session, _ ->
        await("Live fixture not ready") { session.player.playbackState == Player.STATE_READY }
        onMain {
            session.player.seekTo(0)
            session.onBackground()
            session.onForeground()
            assertEquals("Playing foreground return must use the validated tail", 2_000L, session.player.currentPosition)
            assertTrue(session.player.playWhenReady)
        }
        await("Reprepared fixture not ready") { session.player.playbackState == Player.STATE_READY }
        onMain {
            session.pause(); session.player.seekTo(1_000)
            session.onBackground(); session.onForeground()
            assertEquals("A paused return must not seek or resume", 1_000L, session.player.currentPosition)
            assertFalse(session.player.playWhenReady)
        }
    }

    @Test fun fatalDecoderErrorDoesNotSilentlyRestartTheLiveSource() = withFixture(startZero = true) { session, source ->
        await("Live fixture not ready") { session.player.playbackState == Player.STATE_READY && session.player.videoFormat != null }
        onMain {
            val format = requireNotNull(session.player.videoFormat)
            session.player.createMessage { _, _ ->
                throw androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(
                    IllegalStateException("controlled decoder fixture"), "MediaCodecVideoRenderer", 0,
                    format, androidx.media3.common.C.FORMAT_HANDLED, null, false,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED)
            }.send()
        }
        await("Decoder failure must expose the actionable error") { session.state.value.status == PlaybackStatus.Error }
        onMain {
            assertNotNull(session.state.value.error)
            assertEquals(Player.STATE_IDLE, session.player.playbackState)
            assertEquals("Fatal decoder recovery must not reload a fresh preroll", 1, source.masterLoads.get())
        }
    }

    @Test fun mediaControllerPlayUsesSameLiveEdgePolicyAndRepeatedPlayIsIdempotent() = withFixture(startZero = true) { session, _ ->
        var controller: MediaController? = null
        try {
            lateinit var future: ListenableFuture<MediaController>
            onMain { future = MediaController.Builder(instrumentation.targetContext, session.sessionToken).buildAsync() }
            controller = future.get(5, TimeUnit.SECONDS)
            val remote = requireNotNull(controller)
            await("HLS did not become ready") { session.player.playbackState == Player.STATE_READY }
            onMain { remote.pause() }
            await("MediaSession pause was not delivered") { !session.player.playWhenReady }
            val resumeSeeks = AtomicInteger()
            onMain {
                session.player.seekTo(0)
                session.player.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK && newPosition.positionMs == 2_000L) resumeSeeks.incrementAndGet()
                    }
                })
                remote.play()
            }
            await("Controller PLAY bypassed live-edge seek", timeoutMs = 2_000) { resumeSeeks.get() == 1 }
            onMain { repeat(3) { remote.play() } }
            SystemClock.sleep(300)
            assertEquals("Repeated external PLAY must not rewind or seek again", 1, resumeSeeks.get())
        } finally { onMain { controller?.release() } }
    }

    @Test fun regressedFullPlaylistFailsWithDefaultTrackerBeforeItCatchesUp() = withFixture(regressedWindow = true) { _, source ->
        await("Default tracker did not reproduce growing-but-older snapshot failure", timeoutMs = 42_000) {
            source.masterLoads.get() == 2
        }
        assertEquals(1, source.failures.get())
    }

    @Test fun observationGraceLetsRegressedPlaylistCatchUpWithoutRestart() {
        val prefs = instrumentation.targetContext.getSharedPreferences("playback-diagnostics", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("observe_stuck_60s", true).commit()
        try {
            withFixture(regressedWindow = true) { session, source ->
                await("Older growing playlist did not eventually overtake the initial full window", timeoutMs = 48_000) {
                    session.player.duration >= 36_000
                }
                assertEquals("Source must be retained throughout observation", 1, source.masterLoads.get())
                assertEquals("Growing HTTP window is not a fatal source failure", 0, source.failures.get())
            }
        } finally { prefs.edit().remove("observe_stuck_60s").commit() }
    }

    @Test fun productionGraceLetsMarkedRegressedPlaylistCatchUpWithoutRestart() =
        withFixture(regressedWindow = true, commercialMetadata = true) { session, source ->
            await("Production grace did not retain the progressing source", timeoutMs = 48_000) {
                session.player.duration >= 36_000
            }
            assertEquals(1, source.masterLoads.get())
            assertEquals(0, source.failures.get())
            onMain { assertNull(session.state.value.error) }
        }

    @Test fun productionGraceDoesNotHideARegressedPlaylistThatStopsGrowing() =
        withFixture(regressedWindow = true, commercialMetadata = true, stopProgress = true) { _, source ->
            await("Stopped progress must not receive an indefinite grace", timeoutMs = 42_000) {
                source.masterLoads.get() == 2
            }
            assertEquals(1, source.failures.get())
        }

    @Test fun adaptiveQualityLabelUsesRendererInputInsteadOfLastSelectedVariant() = withFixture(adaptive = true) { session, _ ->
        await("Adaptive fixture did not expose renderer format") {
            session.player.videoFormat != null && session.state.value.activeQuality.isNotEmpty()
        }
        onMain {
            val group = session.player.currentTracks.groups.first { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
            assertTrue("Fixture must include multiple selected ABR candidates", (0 until group.length).count(group::isTrackSelected) > 1)
            val format = requireNotNull(session.player.videoFormat)
            assertEquals("Actual local video is 90px high", 90, format.height)
            assertEquals("ABR menu ends in advertised160p, but decoder plays90p", "90p", session.state.value.activeQuality)
            assertNull("Actual quality must not force a manual preference", session.state.value.selectedQualityId)
        }
    }

    @Test fun liveResumeIgnoresPinnedStartZeroAndRepeatedPlayDoesNotSeekAgain() = withFixture(startZero = true) { session, _ ->
        await("Initial START=0 HLS did not become ready") { session.player.playbackState == Player.STATE_READY }
        onMain {
            session.pause()
            session.player.seekTo(0)
            val window = session.player.currentTimeline.getWindow(0, Timeline.Window())
            assertEquals("Fixture must expose the problematic source default", 0L, window.defaultPositionMs)
            session.resume()
            assertEquals("Resume should use the penultimate complete segment", 2_000L, session.player.currentPosition)
            repeat(3) { session.resume() }
            assertEquals(2_000L, session.player.currentPosition)
        }
    }

    @Test fun liveButtonFollowsGrowingWindowAndResumeNeverMovesBehindCurrentPosition() = withFixture(startZero = true) { session, source ->
        await("Initial HLS did not become ready") { session.player.playbackState == Player.STATE_READY }
        onMain { session.pause() }
        source.expanded.set(true)
        await("HLS window did not grow while paused") { session.player.duration == 12_000L }
        onMain {
            val window = session.player.currentTimeline.getWindow(0, Timeline.Window())
            assertEquals("Growing window still has START=0", 0L, window.defaultPositionMs)
            session.seekToLive()
            assertEquals("Live action must follow the new tail, not source START", 8_000L, session.player.currentPosition)
            session.pause()
            session.player.seekTo(9_000)
            session.resume()
            assertEquals("Resume ahead of the safe tail must not rewind", 9_000L, session.player.currentPosition)
        }
    }

    @Test fun frozenLivePlaylistRestartsSameUriOnceAndReachesFreshMedia() = withFixture { session, source ->
        await("Live playlist did not exhaust its frozen snapshot") { source.masterLoads.get() == 2 }
        await("Restarted source did not reach fresh HLS media") {
            session.player.playbackState == Player.STATE_READY || session.player.playbackState == Player.STATE_ENDED
        }
        onMain {
            assertEquals("https://fixture.invalid/master.m3u8", session.player.currentMediaItem!!.localConfiguration!!.uri.toString())
            assertTrue(session.player.playWhenReady)
            assertNull(session.state.value.error)
        }
        SystemClock.sleep(1_200)
        assertEquals("No new token/URI or repeated restart", 2, source.masterLoads.get())
    }

    @Test fun pauseDuringRecoveryDelayWinsOverAutomaticRestart() = withFixture { session, source ->
        await("PlaylistStuckException was not reached") { source.failures.get() == 1 }
        onMain { session.pause() }
        await("Paused source was not repaired") { source.masterLoads.get() == 2 }
        await("Paused repaired source did not become ready") { session.player.playbackState == Player.STATE_READY }
        onMain { assertFalse(session.player.playWhenReady); assertFalse(session.player.isPlaying) }
    }

    @Test fun persistentStuckSourceStopsAfterOneAutomaticRestart() = withFixture(alwaysFrozen = true) { session, source ->
        await("Second frozen source did not reach terminal error", timeoutMs = 30_000) {
            session.state.value.status == PlaybackStatus.Error
        }
        assertEquals(2, source.masterLoads.get())
        SystemClock.sleep(1_500)
        assertEquals("Retry budget must survive READY between errors", 2, source.masterLoads.get())
    }

    @Test fun backgroundDuringRecoveryDefersRepairAndRetainsPauseOnReturn() = withFixture { session, source ->
        await("PlaylistStuckException was not reached") { source.failures.get() == 1 }
        onMain { session.pause(); session.onBackground() }
        SystemClock.sleep(1_200)
        assertEquals("No source requests while backgrounded", 1, source.masterLoads.get())
        onMain { session.onForeground() }
        await("Deferred recovery did not restart on return") { source.masterLoads.get() == 2 }
        await("Deferred repaired source did not become ready") { session.player.playbackState == Player.STATE_READY }
        onMain { assertFalse(session.player.playWhenReady); assertFalse(session.player.isPlaying) }
    }

    @Test fun manualQualityRecoverySupersedesPendingAutomaticRestart() = withFixture { session, source ->
        await("PlaylistStuckException was not reached") { source.failures.get() == 1 }
        onMain { session.pause(); session.selectQuality(null) }
        await("Manual quality recovery did not load source") { source.masterLoads.get() == 2 }
        await("Manual quality recovery did not become ready") { session.player.playbackState == Player.STATE_READY }
        SystemClock.sleep(1_500)
        assertEquals("Delayed automatic job must not replace manually recovered source", 2, source.masterLoads.get())
        onMain { assertFalse(session.player.playWhenReady) }
    }

    private fun withFixture(alwaysFrozen: Boolean = false, startZero: Boolean = false, adaptive: Boolean = false,
        regressedWindow: Boolean = false, commercialMetadata: Boolean = false, stopProgress: Boolean = false,
        resolutionTransition: Boolean = false,
        block: (PlaybackSession, FixtureSource) -> Unit) {
        val source = FixtureSource(alwaysFrozen, startZero, adaptive, regressedWindow, commercialMetadata, stopProgress, resolutionTransition)
        var session: PlaybackSession? = null
        try {
            onMain {
                val owned = PlaybackSession(instrumentation.targetContext, source)
                session = owned
                owned.player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        source.failures.incrementAndGet()
                    }
                })
                owned.play("https://fixture.invalid/master.m3u8", "live:hls-recovery", true)
            }
            block(requireNotNull(session), source)
        } finally {
            onMain { session?.release() }
        }
    }

    private fun await(message: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var passed = false
            onMain { passed = condition() }
            if (passed) return
            SystemClock.sleep(30)
        }
        fail(message)
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private inner class FixtureSource(private val alwaysFrozen: Boolean, private val startZero: Boolean,
        private val adaptive: Boolean, private val regressedWindow: Boolean,
        private val commercialMetadata: Boolean, private val stopProgress: Boolean,
        private val resolutionTransition: Boolean) : DataSource.Factory {
        val masterLoads = AtomicInteger()
        val failures = AtomicInteger()
        val expanded = AtomicBoolean(false)
        private var sourceStartedMs = 0L
        private val mediaLoads = AtomicInteger()
        private val playlist = instrumentation.targetContext.assets.open("qa-hls-fixture.m3u8")
            .bufferedReader().use { it.readText() }
        private val segments = (0..2).associateWith { index ->
            instrumentation.targetContext.assets.open("qa-hls-$index.ts").use { it.readBytes() }
        }
        override fun createDataSource(): DataSource = object : DataSource {
            private var delegate: ByteArrayDataSource? = null
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                val bytes = when (val path = dataSpec.uri.lastPathSegment) {
                    "master.m3u8" -> {
                        masterLoads.incrementAndGet()
                        sourceStartedMs = SystemClock.elapsedRealtime()
                        mediaLoads.set(0)
                        (if (adaptive) "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=240000,CODECS=\"avc1.42c00a\",RESOLUTION=640x360\nlive.m3u8\n" +
                            "#EXT-X-STREAM-INF:BANDWIDTH=120000,CODECS=\"avc1.42c00a\",RESOLUTION=284x160\nlow.m3u8\n"
                         else "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=120000,CODECS=\"avc1.42c00a\",RESOLUTION=160x90\nlive.m3u8\n").toByteArray()
                    }
                    "live.m3u8", "low.m3u8" -> {
                        if (resolutionTransition) {
                            ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:0\n" +
                                "#EXTINF:2.0,\nqa-hls-180.ts\n#EXT-X-DISCONTINUITY\n" +
                                (0..2).joinToString("") { "#EXTINF:2.0,\nqa-hls-$it.ts\n" } + "#EXT-X-ENDLIST\n").toByteArray()
                        } else if (regressedWindow) {
                            val count = if (mediaLoads.incrementAndGet() == 1) 16 else
                                (3 + (SystemClock.elapsedRealtime() - sourceStartedMs) / 2_500).toInt().coerceAtMost(if (stopProgress) 6 else 18)
                            val body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:0\n" +
                                "#EXT-X-START:TIME-OFFSET=0,PRECISE=YES\n" +
                                (if (commercialMetadata) "#EXT-X-DATERANGE:ID=\"fixture\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-01-01T00:00:00Z\",DURATION=32.0\n" else "") +
                                (0 until count).joinToString("") { index ->
                                    (if (index > 0 && index % 3 == 0) "#EXT-X-DISCONTINUITY\n" else "") +
                                        "#EXTINF:2.0,\nqa-hls-${index % 3}.ts\n"
                                } + if (count >= 18) "#EXT-X-ENDLIST\n" else ""
                            body.toByteArray()
                        } else {
                        val ended = !alwaysFrozen && masterLoads.get() > 1
                        var body = if (ended) playlist else playlist.replace("#EXT-X-ENDLIST", "")
                        if (startZero) body = body.replace("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES")
                        if (expanded.get()) body += "\n#EXT-X-DISCONTINUITY\n" +
                            (0..2).joinToString("") { "#EXTINF:2.0,\nqa-hls-$it.ts\n" }
                        body.toByteArray()
                        }
                    }
                    "qa-hls-180.ts" -> instrumentation.targetContext.assets.open("qa-hls-180.ts").use { it.readBytes() }
                    else -> segments.getValue(path!!.removePrefix("qa-hls-").removeSuffix(".ts").toInt())
                }
                return ByteArrayDataSource(bytes).also { delegate = it }.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(delegate).read(buffer, offset, length)
            override fun getUri(): Uri? = delegate?.uri
            override fun close() { delegate?.close(); delegate = null }
        }
    }
}
