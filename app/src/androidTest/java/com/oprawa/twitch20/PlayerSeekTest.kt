package com.oprawa.twitch20

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.ui.PlayerScreen
import com.oprawa.twitch20.ui.TwitchTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Real D-pad + actual local Media3 video. No Twitch requests, saved account, or preferences. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerSeekTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    @get:Rule val testName = TestName()

    @Test fun backCancelsPausedPreviewWithoutMovingOrExiting() = withPlayer { fixture ->
        val before = fixture.snapshot()
        beginRight()
        assertEquals("Preview must not seek", before.position, fixture.snapshot().position)
        assertFalse(fixture.snapshot().playWhenReady)
        device.pressBack()
        assertPreviewClosed()
        assertEquals(before.position, fixture.snapshot().position)
        assertFalse("Cancel must retain paused playback", fixture.snapshot().playWhenReady)
        assertEquals(0, fixture.seeks.get())
        assertEquals(0, fixture.exits.get())
    }

    @Test fun backCancelsPlayingPreviewAndResumesOriginalPosition() = withPlayer(playing = true) { fixture ->
        beginRight()
        val frozen = fixture.snapshot()
        assertFalse("Preview freezes playback until a decision", frozen.playWhenReady)
        SystemClock.sleep(180)
        assertEquals(frozen.position, fixture.snapshot().position)
        device.pressBack()
        assertPreviewClosed()
        await("Cancelled playing preview did not resume") { fixture.snapshot().isPlaying }
        val resumed = fixture.snapshot()
        assertTrue(resumed.playWhenReady)
        assertTrue("Cancel must not jump to draft target", resumed.position in frozen.position..(frozen.position + 3_000))
        assertEquals(0, fixture.seeks.get())
        assertEquals(0, fixture.exits.get())
    }

    @Test fun okCommitsPausedPreviewExactlyOnceAndKeepsPause() = withPlayer { fixture ->
        beginRight()
        val target = fixture.snapshot().position + 10_000
        assertEquals(0, fixture.seeks.get())
        device.pressDPadCenter()
        assertPreviewClosed()
        await("Confirmed seek did not reach target") { abs(fixture.snapshot().position - target) < 250 }
        assertFalse(fixture.snapshot().playWhenReady)
        assertEquals(1, fixture.seeks.get())
        device.pressDPadCenter()
        SystemClock.sleep(180)
        assertEquals("A second OK on the idle timeline must not seek again", 1, fixture.seeks.get())
        assertFalse(fixture.snapshot().playWhenReady)
        assertEquals(0, fixture.exits.get())
    }

    @Test fun okCommitsPlayingPreviewAndRestoresPlaybackIntent() = withPlayer(playing = true) { fixture ->
        beginRight()
        val target = fixture.snapshot().position + 10_000
        assertFalse(fixture.snapshot().playWhenReady)
        device.pressDPadCenter()
        assertPreviewClosed()
        await("Committed playing preview did not resume") { fixture.snapshot().isPlaying }
        val resumed = fixture.snapshot()
        assertTrue(resumed.playWhenReady)
        assertTrue("Playback did not resume around confirmed target", abs(resumed.position - target) < 3_000)
        assertEquals(1, fixture.seeks.get())
        device.pressDPadCenter()
        SystemClock.sleep(180)
        assertEquals(1, fixture.seeks.get())
    }

    @Test fun repeatedArrowsClampAtStartAndEndOfRecording() = withPlayer(positionMs = 2_000) { fixture ->
        repeat(3) { device.pressDPadLeft() }
        assertTarget("0:00")
        assertEquals(2_000L, fixture.snapshot().position)
        device.pressDPadCenter()
        await("Seek to start failed") { fixture.snapshot().position == 0L }
        assertEquals(1, fixture.seeks.get())
        val duration = fixture.snapshot().duration
        repeat(9) { device.pressDPadRight() }
        assertTarget("1:00")
        assertEquals(0L, fixture.snapshot().position)
        device.pressDPadCenter()
        assertPreviewClosed()
        await("Seek to end failed") { abs(fixture.snapshot().position - duration) < 100 }
        assertFalse(fixture.snapshot().playWhenReady)
        assertEquals(2, fixture.seeks.get())
        assertEquals(0, fixture.exits.get())
    }

    @Test fun cancellingPreviewAfterVideoEndedDoesNotRestartRecording() = withPlayer { fixture ->
        instrumentation.runOnMainSync {
            fixture.session.player.seekTo(fixture.session.player.duration)
            // Retain the normal completed-playback intent: playWhenReady=true, STATE_ENDED.
            fixture.session.player.play()
        }
        await("Local video did not reach its ended state") { fixture.snapshot().state == Player.STATE_ENDED }
        val end = fixture.snapshot().duration
        fixture.seeks.set(0)
        device.pressDPadLeft()
        assertTarget("0:50")
        // Media3's final renderer clock can settle just beyond container duration (53 ms on
        // this fixture). Bound that overshoot, then require exact preservation of the frozen clock.
        val frozen = fixture.snapshot().position
        assertTrue("Ended preview moved away from recording end", abs(end - frozen) <= 250)
        device.pressBack()
        assertPreviewClosed()
        assertEquals("Cancel at end must preserve the frozen position", frozen, fixture.snapshot().position)
        assertFalse("Cancel at end must not restart playback", fixture.snapshot().playWhenReady)
        assertFalse(fixture.snapshot().isPlaying)
        assertEquals(0, fixture.seeks.get())
        assertEquals(0, fixture.exits.get())
    }

    @Test fun heldArrowAcceleratesThenStopsAtReleaseAndNewPressUsesSmallStep() =
        withPlayer(positionMs = 5_000) { fixture ->
            val downTime = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            SystemClock.sleep(1_600)
            instrumentation.sendKeySync(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT, 1))
            instrumentation.sendKeySync(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            assertTarget("0:45")
            SystemClock.sleep(350)
            assertTarget("0:45")
            device.pressDPadLeft()
            assertTarget("0:35")
            assertEquals(5_000L, fixture.snapshot().position)
            assertEquals(0, fixture.seeks.get())
            device.pressBack()
            assertPreviewClosed()
        }

    private fun beginRight() {
        device.pressDPadRight()
        awaitTimeline("Right on timeline did not open a position draft") {
            it.startsWith("Перемотка записи: Перейти к ")
        }
    }

    private fun assertPreviewClosed() {
        awaitTimeline("Seek draft stayed visible or player HUD closed") {
            !it.startsWith("Перемотка записи: Перейти к ")
        }
    }

    private fun assertTarget(time: String) {
        awaitTimeline("Expected seek draft at $time") { it == "Перемотка записи: Перейти к $time" }
    }

    private fun awaitTimeline(message: String, predicate: (String) -> Boolean): String {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        var description: String?
        do {
            description = freshTimelineDescription()
            if (description != null && predicate(description)) return description
            SystemClock.sleep(30)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("$message; refreshed accessibility description=$description")
        error("unreachable")
    }

    /**
     * UIAutomator can retain the focused Compose node while its text/description has changed.
     * Refresh the native nodes explicitly; do not accept a caption or player state as a substitute
     * for the actual target exposed by the production timeline's accessibility semantics.
     */
    @Suppress("DEPRECATION")
    private fun freshTimelineDescription(): String? {
        fun find(node: AccessibilityNodeInfo): String? {
            if (!node.refresh()) return null
            val description = node.contentDescription?.toString()
            if (description?.startsWith("Перемотка записи:") == true) return description
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    find(child)?.let { return it }
                } finally {
                    child.recycle()
                }
            }
            return null
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return try { find(root) } finally { root.recycle() }
    }

    private fun withPlayer(playing: Boolean = false, positionMs: Long = 20_000,
        block: (Fixture) -> Unit) {
        var session: PlaybackSession? = null
        try {
            ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
                try {
                val seeks = AtomicInteger()
                val exits = AtomicInteger()
                scenario.onActivity { activity ->
                    val owned = PlaybackSession(activity)
                    session = owned
                    owned.play("asset:///qa-seek.mp4", "vod:qa-seek", false, startPositionMs = positionMs)
                    // Production intentionally uses its Twitch HTTP source factory. This fixture
                    // supplies a local source to the same real player, without changing that factory.
                    val source = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(activity))
                        .createMediaSource(MediaItem.Builder().setUri("asset:///qa-seek.mp4")
                            .setMediaId("vod:qa-seek").build())
                    owned.player.setMediaSource(source, positionMs)
                    owned.player.prepare()
                    owned.pause()
                    activity.setContent {
                        TwitchTheme {
                            PlayerScreen(owned, title = "Player seek fixture", subtitle = "Local 60-second video",
                                settings = AppSettings(chatMode = ChatMode.OFF, showClock = false, animationsEnabled = false),
                                onBack = { exits.incrementAndGet() })
                        }
                    }
                }
                val fixture = Fixture(requireNotNull(session), seeks, exits)
                await("Local seek asset did not become ready") {
                    val snapshot = fixture.snapshot()
                    snapshot.duration >= 59_900 && snapshot.state == Player.STATE_READY
                }
                assertTrue(device.wait(Until.hasObject(By.text("Player seek fixture")), 3_000))
                awaitTimeline("Timeline did not appear") { true }
                // This Surface/Compose debug host may omit UIA focused=true. Initial focus is
                // Play/Pause; one physical UP must reach the timeline. Draft behavior verifies it.
                device.pressDPadUp()
                SystemClock.sleep(120)
                instrumentation.runOnMainSync {
                    fixture.session.player.addListener(object : Player.Listener {
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo, reason: Int) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) seeks.incrementAndGet()
                        }
                    })
                    if (playing) fixture.session.resume()
                }
                if (playing) await("Local video did not start playing") { fixture.snapshot().isPlaying }
                block(fixture)
                } catch (error: Throwable) {
                    // Capture before ActivityScenario closes the fixture Activity.
                    val directory = File(instrumentation.targetContext.filesDir, "qa-player-seek").apply { mkdirs() }
                    runCatching { device.takeScreenshot(File(directory, "${testName.methodName}.png")) }
                    runCatching { device.dumpWindowHierarchy(File(directory, "${testName.methodName}.xml")) }
                    throw error
                }
            }
        } finally {
            instrumentation.runOnMainSync { session?.release() }
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
        assertTrue(message, condition())
    }

    private inner class Fixture(val session: PlaybackSession, val seeks: AtomicInteger, val exits: AtomicInteger) {
        fun snapshot(): Snapshot {
            var result: Snapshot? = null
            instrumentation.runOnMainSync {
                val player = session.player
                result = Snapshot(player.currentPosition, player.duration, player.playWhenReady,
                    player.isPlaying, player.playbackState)
            }
            return requireNotNull(result)
        }
    }

    private data class Snapshot(val position: Long, val duration: Long, val playWhenReady: Boolean,
        val isPlaying: Boolean, val state: Int)
}
