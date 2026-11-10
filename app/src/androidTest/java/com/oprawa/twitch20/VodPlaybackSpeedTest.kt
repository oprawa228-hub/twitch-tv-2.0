package com.oprawa.twitch20

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.data.PlaybackHistory
import com.oprawa.twitch20.playback.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VodPlaybackSpeedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun speedChangesMediaClockAndSurvivesPausedSeek() {
        val context = instrumentation.targetContext
        val session = onMain { PlaybackSession(context, DefaultDataSource.Factory(context)) }
        try {
            onMain { session.play("asset:///qa-seek.mp4", "vod:qa-seek", isLive = false) }
            await("Local VOD did not start") { onMain { session.player.isPlaying } }
            onMain { session.setPlaybackSpeed(1.5f) }
            await("Playback speed was not published") { session.state.value.playbackSpeed == 1.5f }
            SystemClock.sleep(250)

            val before = sample(session)
            SystemClock.sleep(1_800)
            val after = sample(session)
            assertTrue("Fixture must be playing at both clock samples", before.playing && after.playing)
            val rate = (after.mediaMs - before.mediaMs).toDouble() / (after.elapsedMs - before.elapsedMs)
            assertTrue("Expected 1.5x media clock, measured ${rate}x", rate in 1.25..1.75)

            onMain {
                session.pause()
                session.seekTo(20_000, playWhenReady = false)
            }
            await("Paused seek did not settle") {
                onMain {
                    session.player.playbackState == Player.STATE_READY && session.player.currentPosition == 20_000L
                }
            }
            onMain {
                assertFalse(session.player.playWhenReady)
                assertEquals(1.5f, session.player.playbackParameters.speed, 0f)
                assertEquals(1f, session.player.playbackParameters.pitch, 0f)
            }
            onMain { session.resume() }
            await("VOD did not resume after seek") { onMain { session.player.isPlaying } }
            assertEquals(1.5f, onMain { session.player.playbackParameters.speed }, 0f)
        } finally {
            onMain { session.release() }
        }
    }

    @Test fun speedPersistsAcrossInstancesAndIsIsolatedByVodId() {
        val base = instrumentation.context
        val prefsName = "qa_vod_speed_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(prefsName, mode)
        }
        try {
            val first = PlaybackHistory(context)
            assertEquals(1f, first.readSpeed("101"), 0f)
            first.saveSpeed("101", 1.5f)
            assertEquals(1f, first.readSpeed("102"), 0f)
            first.saveSpeed("102", 2f)
            first.save("vod", "101", 10_000)
            first.save("vod", "101", 0)

            val reopened = PlaybackHistory(context)
            assertEquals(1.5f, reopened.readSpeed("101"), 0f)
            assertEquals(2f, reopened.readSpeed("102"), 0f)
            assertEquals(1f, reopened.readSpeed("103"), 0f)
            assertEquals(0L, reopened.read("vod", "101"))
            reopened.saveSpeed("101", 1f)
            assertEquals(1f, PlaybackHistory(context).readSpeed("101"), 0f)
            assertEquals(2f, PlaybackHistory(context).readSpeed("102"), 0f)
        } finally {
            base.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
            base.deleteSharedPreferences(prefsName)
        }
    }

    private fun sample(session: PlaybackSession): ClockSample = onMain {
        ClockSample(SystemClock.elapsedRealtime(), session.player.currentPosition, session.player.isPlaying)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(30)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertTrue(message, condition())
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private data class ClockSample(val elapsedMs: Long, val mediaMs: Long, val playing: Boolean)
}
