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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Actual HLS tracker/decoder; only network I/O is replaced by deterministic local bytes. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class HlsSourceRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

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

    private fun withFixture(alwaysFrozen: Boolean = false, startZero: Boolean = false, block: (PlaybackSession, FixtureSource) -> Unit) {
        val source = FixtureSource(alwaysFrozen, startZero)
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

    private inner class FixtureSource(private val alwaysFrozen: Boolean, private val startZero: Boolean) : DataSource.Factory {
        val masterLoads = AtomicInteger()
        val failures = AtomicInteger()
        val expanded = AtomicBoolean(false)
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
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=120000,CODECS=\"avc1.42c00a\",RESOLUTION=160x90\nlive.m3u8\n".toByteArray()
                    }
                    "live.m3u8" -> {
                        val ended = !alwaysFrozen && masterLoads.get() > 1
                        var body = if (ended) playlist else playlist.replace("#EXT-X-ENDLIST", "")
                        if (startZero) body = body.replace("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES")
                        if (expanded.get()) body += "\n#EXT-X-DISCONTINUITY\n" +
                            (0..2).joinToString("") { "#EXTINF:2.0,\nqa-hls-$it.ts\n" }
                        body.toByteArray()
                    }
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
