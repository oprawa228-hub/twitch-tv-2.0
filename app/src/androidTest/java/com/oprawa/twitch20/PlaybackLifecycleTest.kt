package com.oprawa.twitch20

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.playback.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Media3 state transitions, not a successful media/network playback test. */
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleTest {
    @Test fun playingRetryResolvedWhileBackgroundedResumesOnReturn() = verifyIntent(true)
    @Test fun pausedRetryResolvedWhileBackgroundedStaysPausedOnReturn() = verifyIntent(false)

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
