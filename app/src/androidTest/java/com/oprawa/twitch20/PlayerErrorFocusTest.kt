package com.oprawa.twitch20

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackStatus
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.ui.PlayerScreen
import com.oprawa.twitch20.ui.TwitchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.io.File

/** Real Media3 file failure while a real player settings panel owns focus; no network or prefs. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerErrorFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun backFromClockAfterPlaybackErrorFocusesRetry() = exerciseClose("Часы", 6)
    @Test fun backFromTheaterAfterPlaybackErrorFocusesRetry() = exerciseClose("Кинотеатр", 5)
    @Test fun backFromChatAfterPlaybackErrorFocusesRetry() = exerciseClose("Overlay", 4)

    @Test fun firstOkRetriesWhenErrorArrivesAfterHudWasHidden() {
        var owned: PlaybackSession? = null
        val retries = AtomicInteger()
        val exits = AtomicInteger()
        try {
            ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val session=PlaybackSession(activity)
                    owned=session
                    activity.setContent { TwitchTheme {
                        PlayerScreen(session,"Hidden HUD fixture","",onBack={exits.incrementAndGet()},
                            onRetry={retries.incrementAndGet()})
                    } }
                }
                assertTrue(device.wait(Until.hasObject(By.text("Hidden HUD fixture")),5_000))
                device.pressBack()
                assertTrue(device.wait(Until.gone(By.text("Hidden HUD fixture")),3_000))
                scenario.onActivity { owned!!.play("asset:///missing-hidden-fixture.mp4","hidden-error",false) }
                assertTrue(device.wait(Until.hasObject(By.text("Видео недоступно")),8_000))
                settleRemoteMove()
                device.pressDPadCenter()
                val deadline=SystemClock.elapsedRealtime()+3_000
                while(retries.get()==0 && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(40)
                assertEquals("First OK must Retry, not reveal an invisible HUD",1,retries.get())
                assertEquals(0,exits.get())
            }
        } finally { instrumentation.runOnMainSync { owned?.release() } }
    }

    private fun exerciseClose(profile: String, steps: Int) {
        var ownedSession: PlaybackSession? = null
        val saves = AtomicInteger()
        val savedChatMode = AtomicReference<ChatMode?>()
        val retries = AtomicInteger()
        val exits = AtomicInteger()
        try {
            ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val session = PlaybackSession(activity)
                    ownedSession = session
                    activity.setContent {
                        TwitchTheme {
                            PlayerScreen(session, title = "Player focus fixture", subtitle = "",
                                chatContent = {}, onBack = { exits.incrementAndGet() },
                                onRetry = { retries.incrementAndGet() },
                                onSettingsChange = { savedChatMode.set(it.chatMode); saves.incrementAndGet() })
                        }
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.text("Player focus fixture")), 5_000))
                focusHudAction(profile, stepsFromFirst = steps)
                device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
                assertTrue("Player profile did not open", device.wait(Until.hasObject(By.text(profile)), 5_000))
                val session = requireNotNull(ownedSession)
                scenario.onActivity {
                    session.play("asset:///missing-focus-fixture.mp4", "missing-focus-fixture", false)
                }
                val errorDeadline = SystemClock.elapsedRealtime() + 10_000
                while (session.state.value.status != PlaybackStatus.Error && SystemClock.elapsedRealtime() < errorDeadline) {
                    SystemClock.sleep(40)
                }
                assertEquals("Missing local asset did not produce a Media3 error", PlaybackStatus.Error, session.state.value.status)
                assertTrue("The settings panel closed before the test action", device.hasObject(By.text(profile)))
                assertFalse("Error actions leaked through the modal panel", device.hasObject(By.text("Видео недоступно")))

                device.pressBack()
                assertTrue("Error screen did not appear after closing settings",
                    device.wait(Until.hasObject(By.text("Видео недоступно")), 5_000))
                assertTrue(device.wait(Until.gone(By.text(profile)), 3_000))
                // This debug host's Surface interop omits UIA focused=true even when the native
                // screenshot shows Compose focus. Assert the actual destination of one remote OK.
                settleRemoteMove()
                device.pressDPadCenter()
                val retryDeadline = SystemClock.elapsedRealtime() + 3_000
                while (retries.get() == 0 && exits.get() == 0 && SystemClock.elapsedRealtime() < retryDeadline) {
                    SystemClock.sleep(40)
                }
                assertEquals("The first OK after closing settings must invoke Retry", 1, retries.get())
                assertEquals("Panel close or next OK unexpectedly invoked player exit", 0, exits.get())
                assertEquals("Long-OK release and closing a profile must not change settings", 0, saves.get())
            }
        } finally {
            // ActivityScenario closes/disposes Compose first; Media3 is then released on main.
            instrumentation.runOnMainSync { ownedSession?.release() }
        }
    }

    private fun focusHudAction(label: String, stepsFromFirst: Int) {
        // Exact fixture: idle VOD HUD has seven actions in this order:
        // -10, Watch, +10, Quality, Chat, Theater, Clock. Clamp to its left edge using
        // real remote events, then traverse to the intended action. Opening the intended
        // panel and zero mutation callbacks provide the behavioral assertion, not a UIA focus flag.
        saveNavigationDiagnostics("hud-initial", label)
        repeat(16) { device.pressDPadLeft() }
        settleRemoteMove()
        repeat(stepsFromFirst) {
            device.pressDPadRight()
            settleRemoteMove()
        }
        saveNavigationDiagnostics("hud-target", label)
    }

    private fun settleRemoteMove() {
        device.waitForIdle(500)
        // waitForIdle can return before Compose's bring-into-view scroll animation settles.
        SystemClock.sleep(180)
    }

    private fun saveNavigationDiagnostics(stage: String, label: String): String {
        val directory = File(instrumentation.targetContext.filesDir, "qa-error-focus").apply { mkdirs() }
        val name = "$stage-${label.hashCode()}"
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
        device.takeScreenshot(File(directory, "$name.png"))
        return "$directory/$name"
    }
}
