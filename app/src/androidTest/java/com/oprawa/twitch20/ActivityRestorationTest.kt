package com.oprawa.twitch20

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.playback.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ActivityRestorationTest {
    @Test fun recreationKeepsCommittedRouteAndPlaybackUntilAccountChanges() {
        val origin = Route("channel", "fixture", "Fixture")
        ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = ViewModelProvider(activity)[TwitchActivityState::class.java]
                val nav = host.attachNavigation(true, "Fixture account")
                nav.history.add(Route("search", "fixture"))
                nav.selections[origin.key] = "video:7"
                nav.route.value = Route("rail-channel", "other")
                nav.railPreviewOrigin.value = origin
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val host = ViewModelProvider(activity)[TwitchActivityState::class.java]
                val nav = host.attachNavigation(true, "Fixture account")
                assertEquals(origin, nav.route.value)
                assertEquals("fixture", nav.history.single().value)
                assertEquals("video:7", nav.selections[origin.key])
                nav.watching.value = Watching("7", "vod", "fixture", "Запись", "fixture")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val host = ViewModelProvider(activity)[TwitchActivityState::class.java]
                assertEquals("7", host.attachNavigation(true, "Fixture account").watching.value?.id)
                val guest = host.attachNavigation(false, "")
                assertNull(guest.watching.value)
                assertTrue(guest.history.isEmpty())
                assertTrue(guest.selections.isEmpty())
            }
        }
    }

    @Test fun stoppedOwnerHandsPausedVodPositionToItsReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val source = "asset:///qa-seek.mp4"
            val owner = PlaybackSession(context, DefaultDataSource.Factory(context))
            val saved = try {
                owner.play(source, "vod:fixture", false, 12_000L)
                owner.pause()
                owner.onBackground()
                requireNotNull(owner.resumePoint())
            } finally {
                owner.release()
            }
            val replacement = PlaybackSession(context, DefaultDataSource.Factory(context))
            try {
                replacement.play(source, "vod:fixture", false, saved.positionMs,
                    initialPlayWhenReady = saved.playWhenReady)
                assertEquals(12_000L, replacement.player.currentPosition)
                assertFalse(replacement.player.playWhenReady)
                replacement.resume()
                replacement.onBackground()
                assertTrue("System pause must preserve pre-background playback intent",
                    requireNotNull(replacement.resumePoint()).playWhenReady)
                assertEquals(Player.STATE_IDLE, replacement.player.playbackState)
            } finally {
                replacement.release()
            }
        }
    }
}
