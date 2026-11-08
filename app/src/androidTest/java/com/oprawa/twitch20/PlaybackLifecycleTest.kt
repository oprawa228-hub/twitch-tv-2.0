package com.oprawa.twitch20

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.Player
import com.oprawa.twitch20.playback.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Media3 state transitions, not a successful media/network playback test. */
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleTest {
    @Test fun playingRetryResolvedWhileBackgroundedResumesOnReturn() = verifyIntent(true)
    @Test fun pausedRetryResolvedWhileBackgroundedStaysPausedOnReturn() = verifyIntent(false)

    @Test fun repeatedLivePlayDoesNotSeekOrReplaceTheSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session = PlaybackSession(instrumentation.targetContext)
            try {
                // Test command dispatch synchronously before source loading. The URI is
                // deliberately local; successful Twitch playback is a separate check.
                session.play("asset:///qa-intent-only.mp4", "live:qa-intent", isLive = true)
                session.player.seekTo(12_000)
                val item = session.player.currentMediaItem
                var seekCount = 0
                session.player.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo, reason: Int) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) seekCount++
                    }
                })
                repeat(3) { session.resume() }
                assertEquals("PLAY on an already playing live item must not seek", 0, seekCount)
                assertEquals(12_000L, session.player.currentPosition)
                assertSame("PLAY must retain the resolved source", item, session.player.currentMediaItem)

                session.pause()
                session.resume()
                assertEquals("No valid HLS window means resume must not seek to a guessed default", 0, seekCount)
                repeat(3) { session.resume() }
                assertEquals("Repeated PLAY after resume must not add a seek", 0, seekCount)
                assertTrue(session.player.playWhenReady)
                assertSame(item, session.player.currentMediaItem)
            } finally {
                session.release()
            }
        }
    }

    private fun verifyIntent(requested: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session = PlaybackSession(instrumentation.targetContext)
            try {
                // No external request: only intent transitions are under test.
                val source = "asset:///qa-intent-only.mp4"
                session.play(source, "vod:qa-intent", isLive = false)
                if (!requested) session.pause()
                session.onBackground()
                // A fresh source resolves after ON_STOP; Viewing immediately
                // backgrounds the newly prepared item before any ON_START.
                session.play(source, "vod:qa-intent", isLive = false)
                session.onBackground()
                session.onForeground()
                assertEquals(requested, session.player.playWhenReady)
            } finally {
                session.release()
            }
        }
    }
}
