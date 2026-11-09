package com.oprawa.twitch20

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.tv.material3.Text
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.data.FollowingMonitor
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.ui.FollowNotifications
import com.oprawa.twitch20.ui.SettingsScreen
import com.oprawa.twitch20.ui.TvAction
import com.oprawa.twitch20.ui.TvColors
import com.oprawa.twitch20.ui.TwitchTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real monitor, Compose banner/settings and remote input. Snapshot data is controlled, no Twitch. */
@RunWith(AndroidJUnit4::class)
class FollowNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun firstSnapshotIsSilentThenOneNewStreamShowsWithoutTakingFocusAndExpires() = withNotifications { fixture ->
        val baseline = channel("Baseline")
        fixture.publish(listOf(baseline))
        assertAbsent(baseline, 350)
        fixture.publish(listOf(baseline))
        assertAbsent(baseline, 350)
        val next = channel("New live")
        fixture.publish(listOf(baseline, next))
        awaitBanner(next)
        val shownAt = SystemClock.elapsedRealtime()
        assertTrue("Banner took focus from playback controls", anchorFocused())
        device.pressDPadCenter()
        assertTrue("OK no longer reaches the underlying control",
            device.wait(Until.hasObject(By.text("Нажатий: 1")), 2_000))
        SystemClock.sleep(1_500)
        assertTrue("Banner disappeared before its display interval", device.hasObject(banner(next)))
        assertTrue("Banner did not expire after 4.5 seconds", device.wait(Until.gone(banner(next)), 5_500))
        assertTrue("Banner was visible for less than 3.5 seconds", SystemClock.elapsedRealtime() - shownAt >= 3_500)
        assertTrue(anchorFocused())
        fixture.publish(listOf(baseline, next))
        assertAbsent(next, 500)
    }

    @Test fun currentChannelIsSuppressedAndPendingEventRechecksCurrentChannel() = withNotifications { fixture ->
        fixture.publish(emptyList())
        val current = channel("Current")
        fixture.change { it.copy(currentLogin = current.login.uppercase()) }
        fixture.publish(listOf(current))
        assertAbsent(current, 500)
        fixture.change { it.copy(controlsVisible = true, currentLogin = null) }
        val pending = channel("Pending current")
        fixture.publish(listOf(current, pending))
        assertAbsent(pending, 250)
        fixture.change { it.copy(currentLogin = pending.login.uppercase(), controlsVisible = false) }
        assertAbsent(pending, 500)
        fixture.change { it.copy(currentLogin = null) }
        fixture.publish(listOf(current, pending))
        assertAbsent(pending, 500)
        val other = channel("Other")
        fixture.publish(listOf(current, pending, other))
        awaitBanner(other)
        assertTrue(anchorFocused())
    }

    @Test fun settingsBlockRealAlertsAndTheirToggleImmediatelyDisablesAndReenablesDelivery() = withNotifications { fixture ->
        fixture.publish(emptyList())
        fixture.change { it.copy(settingsOpen = true) }
        openNotificationSettings()
        assertTrue("Settings example must identify itself rather than announce a real live start",
            device.wait(Until.hasObject(By.desc("Пример уведомления: Дядя на танке в эфире")), 3_000))
        val blocked = channel("While settings")
        fixture.publish(listOf(blocked))
        assertAbsent(blocked, 400)
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.desc("Новые эфиры каналов: Скрыты")), 3_000))
        assertFalse("Toggle did not immediately persist to the host", fixture.config.value.settings.followNotificationsEnabled)
        closeSettings()
        assertAbsent(blocked, 500)
        val disabled = channel("While disabled")
        fixture.publish(listOf(blocked, disabled))
        assertAbsent(disabled, 400)
        fixture.change { it.copy(settingsOpen = true) }
        openNotificationSettings(enabled = false)
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.desc("Новые эфиры каналов: Показаны")), 3_000))
        closeSettings()
        assertAbsent(disabled, 500)
        val enabled = channel("After enabled")
        fixture.publish(listOf(blocked, disabled, enabled))
        awaitBanner(enabled)
        assertTrue(anchorFocused())
    }

    @Test fun backgroundDoesNotConsumeTheDisplayIntervalOfAStillFreshPendingEvent() = withNotifications { fixture ->
        fixture.publish(emptyList())
        fixture.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse("Activity did not leave the foreground", fixture.foreground)
        // Models a late snapshot completion; the host blocks its banner while stopped.
        val pending = channel("During background")
        fixture.publish(listOf(pending))
        SystemClock.sleep(5_200)
        fixture.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitBanner(pending)
        assertTrue("Returning notification stole playback focus", anchorFocused())
        SystemClock.sleep(800)
        assertTrue("Background time consumed the actual visible interval", device.hasObject(banner(pending)))
    }

    @Test fun expiredBlockedEventIsDroppedAndTheQueueRetainsOnlyFourNewestWaitingStarts() = withNotifications { fixture ->
        fixture.publish(emptyList())
        fixture.change { it.copy(controlsVisible = true) }
        val expired = channel("Expired")
        fixture.publish(listOf(expired))
        // Keep one collector occupied while the bounded SharedFlow queue receives a burst.
        SystemClock.sleep(19_000)
        val burst = (1..6).map { channel("Queued $it") }
        fixture.publish(listOf(expired) + burst)
        SystemClock.sleep(2_000)
        fixture.change { it.copy(controlsVisible = false) }
        awaitBanner(burst[2], timeoutMs = 2_500)
        assertFalse("Expired event reappeared", device.hasObject(banner(expired)))
        assertFalse("Queue retained its first overflowed event", device.hasObject(banner(burst[0])))
        assertFalse("Queue retained its second overflowed event", device.hasObject(banner(burst[1])))
        assertTrue(anchorFocused())
    }

    @Test fun disablingAVisibleBannerClearsItAndDoesNotReplayOnEnable() = withNotifications { fixture ->
        fixture.publish(emptyList())
        val active = channel("Visible before disable")
        fixture.publish(listOf(active))
        awaitBanner(active)
        fixture.change { it.copy(settings = it.settings.copy(followNotificationsEnabled = false)) }
        assertTrue(device.wait(Until.gone(banner(active)), 2_000))
        fixture.change { it.copy(settings = it.settings.copy(followNotificationsEnabled = true)) }
        assertAbsent(active, 600)
        assertTrue(anchorFocused())
    }

    @Test fun openingAnnouncedChannelDismissesItsBannerPermanentlyAndReleasesTheNextEvent() = withNotifications { fixture ->
        fixture.publish(emptyList())
        val announced = channel("Announced")
        fixture.publish(listOf(announced))
        awaitBanner(announced)
        fixture.change { it.copy(currentLogin = announced.login.uppercase()) }
        assertTrue(device.wait(Until.gone(banner(announced)), 2_000))
        fixture.change { it.copy(currentLogin = null) }
        assertAbsent(announced, 400)
        val next = channel("After announced")
        fixture.publish(listOf(announced, next))
        // A hidden old banner must not keep occupying the collector's 4.5-second interval.
        awaitBanner(next, timeoutMs = 2_000)
        assertTrue(anchorFocused())
    }

    private fun withNotifications(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
            fixture.scenario = scenario
            scenario.onActivity { activity ->
                activity.setContent {
                    TwitchTheme {
                        // Capture a value now. A delegated getter inside semantics can expose a
                        // newer State before the child composable has received those parameters.
                        val config = fixture.config.value
                        val lifecycle = LocalLifecycleOwner.current.lifecycle
                        var foreground by remember(lifecycle) {
                            mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        }
                        DisposableEffect(lifecycle) {
                            fixture.foreground = foreground
                            val observer = LifecycleEventObserver { _, _ ->
                                foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                fixture.foreground = foreground
                            }
                            lifecycle.addObserver(observer)
                            onDispose { lifecycle.removeObserver(observer) }
                        }
                        val anchor = remember { FocusRequester() }
                        LaunchedEffect(config.settingsOpen, foreground) {
                            if (!config.settingsOpen && foreground) { withFrameNanos { }; anchor.requestFocus() }
                        }
                        Box(Modifier.fillMaxSize().background(TvColors.Background).semantics {
                            contentDescription = signature(config)
                        }) {
                            if (config.settingsOpen) SettingsScreen(config.settings,
                                onSave = { value -> fixture.config.value = fixture.config.value.copy(settings = value) },
                                onBack = { fixture.config.value = fixture.config.value.copy(settingsOpen = false) },
                                onCheckUpdates = {})
                            else Column(Modifier.align(Alignment.BottomStart).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvAction(ANCHOR, { fixture.clicks.value++ }, Modifier.focusRequester(anchor))
                                Text("Нажатий: ${fixture.clicks.value}", color = TvColors.Text)
                            }
                            FollowNotifications(fixture.monitor, config.settings.followNotificationsEnabled,
                                blocked = !foreground || config.settingsOpen || config.controlsVisible,
                                currentLogin = config.currentLogin,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
                        }
                        SideEffect { fixture.appliedConfig = config }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text(ANCHOR)), 5_000))
            awaitAnchor()
            block(fixture)
        }
    }

    private inner class Fixture {
        val config = mutableStateOf(Config())
        val clicks = mutableStateOf(0)
        @Volatile var foreground = false
        @Volatile var appliedConfig: Config? = null
        lateinit var scenario: ActivityScenario<CatalogFocusHostActivity>
        private var channels = emptyList<Channel>()
        val monitor = FollowingMonitor { channels }
        fun change(transform: (Config) -> Config) {
            instrumentation.runOnMainSync { config.value = transform(config.value) }
            val expected = config.value
            // SideEffect runs after applyChanges; AX semantics is not a composition barrier.
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while (appliedConfig != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
            assertEquals("Host state did not reach the child composition", expected, appliedConfig)
        }
        fun publish(next: List<Channel>) = instrumentation.runOnMainSync {
            channels = next
            // The fixture source is immediate; production refresh and its baseline logic are real.
            runBlocking { monitor.refresh() }
        }
    }

    private data class Config(val settings: AppSettings = AppSettings(animationsEnabled = false),
        val settingsOpen: Boolean = false, val controlsVisible: Boolean = false, val currentLogin: String? = null)

    private fun signature(config: Config) = "Notification fixture: ${config.settings.followNotificationsEnabled}|" +
        "${config.settingsOpen}|${config.controlsVisible}|${config.currentLogin.orEmpty()}"

    private fun channel(name: String) = Channel(id = name, login = name.lowercase().replace(' ', '_'),
        name = name, title = "Controlled live start", category = "Test", imageUrl = "", liveStreamId = "stream-$name")
    private fun banner(channel: Channel) = By.text("${channel.name} в эфире")
    private fun awaitBanner(channel: Channel, timeoutMs: Long = 3_000) {
        assertTrue("Expected notification for ${channel.name}", device.wait(Until.hasObject(banner(channel)), timeoutMs))
    }
    private fun assertAbsent(channel: Channel, durationMs: Long) {
        val until = SystemClock.elapsedRealtime() + durationMs
        do {
            assertFalse("Unexpected notification for ${channel.name}", device.hasObject(banner(channel)))
            SystemClock.sleep(40)
        } while (SystemClock.elapsedRealtime() < until)
    }
    private fun focusedContains(predicate: (UiObject2) -> Boolean): Boolean {
        fun contains(node: UiObject2): Boolean = predicate(node) || node.children.any(::contains)
        return device.findObject(By.focused(true))?.let(::contains) == true
    }
    private fun anchorFocused() = focusedContains { it.text == ANCHOR }
    private fun awaitAnchor() {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (!anchorFocused() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40)
        assertTrue("Playback control did not receive initial/return focus", anchorFocused())
    }
    private fun openNotificationSettings(enabled: Boolean = true) {
        assertTrue(device.wait(Until.hasObject(By.desc("Уведомления")), 3_000))
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (!focusedContains { it.contentDescription == "Уведомления" } && SystemClock.elapsedRealtime() < deadline) {
            device.pressDPadDown()
            SystemClock.sleep(80)
        }
        assertTrue("Could not focus notifications with the remote", focusedContains { it.contentDescription == "Уведомления" })
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.desc("Новые эфиры каналов: ${if (enabled) "Показаны" else "Скрыты"}")), 3_000))
        SystemClock.sleep(100)
    }
    private fun closeSettings() {
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.desc("Уведомления")), 3_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text(ANCHOR)), 3_000))
        awaitAnchor()
    }
    private companion object { const val ANCHOR = "Продолжить просмотр" }
}
