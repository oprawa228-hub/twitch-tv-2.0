package com.oprawa.twitch20

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.playback.PlaybackSession
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Media3 ownership; no source, decoder playback or external network is required. */
@RunWith(AndroidJUnit4::class)
class MediaSessionOwnershipTest {
    @Test fun aBackgroundedOwnerDoesNotPreventCreatingAnotherPlaybackSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var backgrounded: PlaybackSession? = null
            var foreground: PlaybackSession? = null
            try {
                val first = PlaybackSession(instrumentation.targetContext)
                backgrounded = first
                first.onBackground()
                // A stopped Activity can retain its owner while another owner is created.
                val second = PlaybackSession(instrumentation.targetContext)
                foreground = second
                assertNotSame(first.player, second.player)
            } finally {
                try { foreground?.release() }
                finally { backgrounded?.release() }
            }
        }
    }
}
