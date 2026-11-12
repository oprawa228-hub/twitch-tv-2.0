package com.oprawa.twitch20

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.tv.material3.Text
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.ui.CatalogScreen
import com.oprawa.twitch20.ui.FollowingSidebarState
import com.oprawa.twitch20.ui.NavigationRail
import com.oprawa.twitch20.ui.TwitchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Production sidebar + catalog, real D-pad, controlled subscription updates; no Twitch I/O. */
@RunWith(AndroidJUnit4::class)
class SidebarNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun sectionFocusPreviewsAndRightCommitsWithoutAnotherRouteChange() = withSidebar { _ ->
        focus("Каналы")
        assertTrue(device.wait(Until.hasObject(By.text("Каталог following")), 3_000))
        focus("Поиск")
        assertTrue("Search focus must not replace the current catalog", device.hasObject(By.text("Каталог following")))
        focus("Категории")
        assertTrue(device.wait(Until.hasObject(By.text("Каталог categories")), 3_000))
        assertFocused("Категории")
        device.pressDPadRight()
        assertTrue("Right did not move focus into the already-previewed catalog",
            device.wait(Until.hasObject(By.focused(true).descStartsWith("Body card,")), 5_000))
        assertTrue(device.hasObject(By.text("Каталог categories")))
        assertFalse(device.hasObject(By.desc("Аккаунт")))
        assertFalse(device.hasObject(By.desc("О приложении")))
    }

    @Test fun leftFromLowerCatalogRowReturnsToItsSection() = withSidebar { _ ->
        focus("Категории")
        assertTrue(device.wait(Until.hasObject(By.text("Каталог categories")), 3_000))
        device.pressDPadRight()
        assertFocused("Body card,", prefix = true)
        device.pressDPadDown()
        device.pressDPadDown()
        assertFocused("Body card 6,", prefix = true)
        device.pressDPadLeft()
        assertFocused("Категории")
        assertTrue(device.hasObject(By.text("Каталог categories")))
        device.pressDPadRight()
        assertFocused("Body card 6,", prefix = true)
    }

    @Test fun refreshKeepsFocusedChannelAcrossOfflineTransitionAndBackRestoresOrigin() = withSidebar { fixture ->
        focus("Bravo, в эфире", prefix = true)
        assertTrue(device.wait(Until.hasObject(By.text("Предпросмотр Bravo")), 3_000))
        fixture.update(listOf(charlie, bravo.copy(viewers = 900), alpha, offline))
        assertTrue("The reordered subscription snapshot was not rendered",
            device.wait(Until.hasObject(By.desc("Bravo, в эфире, 900 зрителей")), 3_000))
        assertFocused("Bravo, в эфире", prefix = true)
        assertFalse("Offline subscriptions must not occupy the live sidebar", device.hasObject(By.descStartsWith("Offline, в эфире")))
        fixture.update(listOf(charlie, bravo.copy(live = false), alpha, offline))
        assertTrue(device.wait(Until.hasObject(By.desc("Bravo, офлайн")), 3_000))
        assertFocused("Bravo, офлайн")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Открыт канал bravo")), 3_000))
        assertEquals(0, fixture.shortActions.get())
        device.pressBack()
        assertTrue("A transient focus loss discarded the original catalog",
            device.wait(Until.hasObject(By.text("Каталог streams")), 5_000))
        assertTrue(device.wait(Until.hasObject(By.focused(true).descStartsWith("Body card,")), 5_000))
    }

    @Test fun quickChannelOkAndLongOkHaveSeparateActions() = withSidebar { fixture ->
        focus("Alpha, в эфире", prefix = true)
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Открыт эфир alpha")), 3_000))
        assertEquals(1, fixture.shortActions.get())
        assertEquals(0, fixture.longActions.get())
        // The harness retains the real rail so both physical actions can be observed independently.
        focus("Alpha, в эфире", prefix = true)
        device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
        assertTrue("Long OK did not invoke channel details",
            device.wait(Until.hasObject(By.text("Открыт канал alpha")), 3_000))
        assertEquals("Long OK also performed the short action", 1, fixture.shortActions.get())
        assertEquals("Long OK must invoke details exactly once", 1, fixture.longActions.get())
    }

    private fun withSidebar(check: (Fixture) -> Unit) {
        val fixture = Fixture()
        ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TwitchTheme {
                        var route by remember { mutableStateOf("streams") }
                        var origin by remember { mutableStateOf<String?>(null) }
                        var railOwnsFocus by remember { mutableStateOf(false) }
                        var userNavigated by remember { mutableStateOf(false) }
                        var generation by remember { mutableLongStateOf(0L) }
                        var preview by remember { mutableStateOf<Channel?>(null) }
                        var action by remember { mutableStateOf("") }
                        val selections = remember { mutableStateMapOf<String, String>() }
                        BackHandler(enabled = origin != null) {
                            route = requireNotNull(origin)
                            origin = null
                            preview = null
                            userNavigated = false
                            railOwnsFocus = false
                            generation++
                        }
                        Row(Modifier.fillMaxSize().onPreviewKeyEvent {
                            if (it.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                it.nativeKeyEvent.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_UP,
                                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) userNavigated = true
                            false
                        }) {
                            NavigationRail(selected = preview?.let { "live:${it.id}" } ?: route,
                                accountName = "Fixture", following = fixture.following.value,
                                onFocusOwnership = { railOwnsFocus = it },
                                onPreview = { section ->
                                    if (userNavigated && route != section) {
                                        if (origin == null) origin = route
                                        preview = null
                                        route = section
                                    }
                                },
                                onSelect = { section ->
                                    userNavigated = false
                                    origin = null
                                    preview = null
                                    route = section
                                    generation++
                                },
                                onChannelPreview = { channel ->
                                    if (userNavigated) {
                                        if (origin == null) origin = route
                                        preview = channel
                                    }
                                },
                                onChannelSelect = { fixture.shortActions.incrementAndGet(); action = "Открыт эфир ${it.id}" },
                                onChannelDetails = { fixture.longActions.incrementAndGet(); action = "Открыт канал ${it.id}" })
                            Column(Modifier.weight(1f)) {
                                if (action.isNotBlank()) Text(action)
                                val channel = preview
                                if (channel != null) Text("Предпросмотр ${channel.name}")
                                else key(route) {
                                    CatalogScreen(title = "Каталог $route", channels = bodyCards,
                                        busy = false, error = null, selectedKey = selections[route],
                                        onFocused = { selections[route] = it }, revision = generation,
                                        allowFocusRestore = !railOwnsFocus || !userNavigated,
                                        onChannel = {}, onCategory = {}, onVideo = {}, onRetry = {})
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.focused(true).descStartsWith("Body card,")), 5_000))
            check(fixture)
        }
    }

    private fun focus(description: String, prefix: Boolean = false) {
        repeat(32) {
            val target = device.findObject(if (prefix) By.descStartsWith(description) else By.desc(description))
                ?: throw AssertionError("No sidebar target $description")
            if (hasFocusedAncestor(target)) return
            val focused = device.findObject(By.focused(true))
            if (focused == null) device.pressDPadUp()
            else {
                val from = focused.visibleBounds
                val to = target.visibleBounds
                when {
                    from.left >= to.right -> device.pressDPadLeft()
                    from.centerY() > to.centerY() -> device.pressDPadUp()
                    else -> device.pressDPadDown()
                }
            }
            device.waitForIdle(300)
        }
        throw AssertionError("D-pad did not reach $description")
    }

    private fun assertFocused(description: String, prefix: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + 4_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val target = device.findObject(if (prefix) By.descStartsWith(description) else By.desc(description))
            if (target != null && hasFocusedAncestor(target)) return
            SystemClock.sleep(40)
        }
        assertTrue("Focus did not stay/restore on $description", false)
    }

    private fun hasFocusedAncestor(target: UiObject2): Boolean {
        var node: UiObject2? = target
        while (node != null) {
            if (node.isFocused) return true
            node = node.parent
        }
        return false
    }

    private inner class Fixture {
        val shortActions = AtomicInteger()
        val longActions = AtomicInteger()
        val following = mutableStateOf(FollowingSidebarState(listOf(alpha, bravo, charlie, offline),
            signedIn = true, initialized = true))
        fun update(channels: List<Channel>) = instrumentation.runOnMainSync {
            following.value = following.value.copy(channels = channels)
        }
    }

    private companion object {
        val alpha = Channel("alpha", "alpha", "Alpha", "One", "Game", "", viewers = 10)
        val bravo = Channel("bravo", "bravo", "Bravo", "Two", "Game", "", viewers = 20)
        val charlie = Channel("charlie", "charlie", "Charlie", "Three", "Game", "", viewers = 30)
        val offline = Channel("offline", "offline", "Offline", "", "", "", live = false)
        val body = Channel("body", "body", "Body card", "Navigation fixture", "Game", "")
        val bodyCards = listOf(body) + (1..8).map { index ->
            body.copy(id = "body$index", login = "body$index", name = "Body card $index")
        }
    }
}
