package com.oprawa.twitch20

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.ui.CatalogScreen
import com.oprawa.twitch20.ui.NavigationRail
import com.oprawa.twitch20.ui.TwitchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production Compose controls + real remote input; deterministic page data, no Twitch/network. */
@RunWith(AndroidJUnit4::class)
class CatalogFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun delayedCardsDoNotStealFocusAfterManualRailNavigation() = withCatalog { fixture ->
        focusCategoriesInRail()
        fixture.publish(channels = channels)
        assertTrue(device.wait(Until.hasObject(By.text("Fixture first")), 5_000))
        assertFocusRemains("Категории")
    }

    @Test fun delayedEmptyErrorDoesNotStealFocusAfterManualRailNavigation() = withCatalog { fixture ->
        focusCategoriesInRail()
        fixture.publish(error = "Fixture empty result")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture empty result")), 5_000))
        assertFocusRemains("Категории")
    }

    @Test fun initialResultRestoresSelectedCardWithoutManualRailInput() = withCatalog("channel:second") { fixture ->
        // Do not manufacture focus with requestFocus or accessibility click: exercise initial entry.
        fixture.publish(channels = channels)
        assertTrue("Initial result did not restore the selected card",
            device.wait(Until.hasObject(By.focused(true).descStartsWith("Fixture second,")), 5_000))
    }

    @Test fun enteringRailWithOneLeftProtectsFocusDuringNextGeneration() = withCatalog("channel:first") { fixture ->
        fixture.publish(channels = channels)
        assertTrue(device.wait(Until.hasObject(By.focused(true).descStartsWith("Fixture first,")), 5_000))
        // This key originates in the catalog, so the rail's preview-key handler never receives it.
        device.pressDPadLeft()
        device.waitForIdle(1_000)
        val railDescription = focusedRailDescription()
        assertTrue("One LEFT did not enter the rail: $railDescription", railDescription in railDescriptions)
        fixture.loadingNextGeneration()
        assertTrue(device.wait(Until.hasObject(By.text("Загружаем…")), 5_000))
        fixture.publish(channels = channels)
        assertTrue(device.wait(Until.hasObject(By.text("Fixture first")), 5_000))
        assertFocusRemains(requireNotNull(railDescription))
    }

    @Test fun railFocusPreviewsWithoutOkAndBackRestoresTheSelectedCard() =
        withCatalog("channel:item7", previewRoutes = true) { fixture ->
            val longPage = (1..18).map {
                Channel("item$it", "item$it", "Fixture item$it", "Rail preview fixture", "Test", "")
            }
            fixture.publish(channels = longPage)
            assertTrue(device.wait(Until.hasObject(By.focused(true).descStartsWith("Fixture item7,")), 5_000))
            val originalTop = device.findObject(By.focused(true))!!.visibleBounds.top
            assertFalse("Account must be nested in Settings", device.hasObject(By.desc("Аккаунт")))
            assertFalse("About must be nested in Settings", device.hasObject(By.desc("О приложении")))
            // Item 7 is in the first column for both 3- and 2-column accessibility layouts.
            device.pressDPadLeft()
            focusCategoriesInRail()
            assertTrue("Focus did not preview the section without OK",
                device.wait(Until.hasObject(By.text("Focus fixture · categories")), 5_000))
            assertTrue(device.wait(Until.hasObject(By.text("Preview item1")), 5_000))
            assertFocusRemains("Категории")
            device.pressBack()
            assertTrue("Back did not return the previous section",
                device.wait(Until.hasObject(By.text("Focus fixture")), 5_000))
            assertTrue("Back did not restore the selected card",
                device.wait(Until.hasObject(By.focused(true).descStartsWith("Fixture item7,")), 5_000))
            assertEquals("Back moved the original grid row", originalTop,
                device.findObject(By.focused(true))!!.visibleBounds.top)
        }

    private fun withCatalog(selectedKey: String? = null, previewRoutes: Boolean = false, check: (Fixture) -> Unit) {
        val fixture = Fixture(selectedKey)
        ActivityScenario.launch(CatalogFocusHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TwitchTheme {
                        var railOwnsFocus by remember { mutableStateOf(false) }
                        var route by remember { mutableStateOf("streams") }
                        var previewOrigin by remember { mutableStateOf<String?>(null) }
                        // Page revision must not reset deliberate navigation. The route-enabled
                        // case also exercises Back from a rail preview to the original catalog.
                        var userNavigated by remember { mutableStateOf(false) }
                        val page = fixture.page.value
                        // Host route bookkeeping mirrors MainActivity; the actual rail, grid,
                        // delayed focus restore and every input remain production controls.
                        BackHandler(enabled = previewOrigin != null && railOwnsFocus) {
                            route = requireNotNull(previewOrigin)
                            previewOrigin = null
                            railOwnsFocus = false
                            userNavigated = false
                        }
                        Row(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_UP,
                                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) userNavigated = true
                            false
                        }) {
                            NavigationRail(selected = route, accountName = "",
                                onSelect = { userNavigated = false },
                                onPreview = { section ->
                                    if (previewRoutes && userNavigated && route != section) {
                                        if (previewOrigin == null) previewOrigin = route
                                        route = section
                                    }
                                },
                                onFocusOwnership = {
                                    railOwnsFocus = it
                                    if (!it) previewOrigin = null
                                })
                            Box(Modifier.weight(1f)) {
                                key(route) {
                                    CatalogScreen(title = if (route == "streams") "Focus fixture" else "Focus fixture · $route",
                                        channels = if (route == "streams") page.channels else page.channels.map {
                                            it.copy(id = "preview-${it.id}", name = "Preview ${it.login}")
                                        },
                                        busy = page.busy, error = page.error, revision = page.revision,
                                        selectedKey = if (route == "streams") fixture.selected.value else null,
                                        onFocused = { if (route == "streams") fixture.selected.value = it },
                                        allowFocusRestore = !railOwnsFocus || !userNavigated,
                                        onChannel = {}, onCategory = {}, onVideo = {}, onRetry = {})
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Focus fixture")), 5_000))
            check(fixture)
        }
    }

    private fun focusCategoriesInRail() {
        // During the pending initial result the only focusable controls are real rail items.
        repeat(8) {
            if (focusedRailDescription() == "Эфиры") return@repeat
            device.pressDPadUp()
            device.waitForIdle(500)
        }
        assertTrue("Could not reach the first rail item using the remote",
            focusedRailDescription() == "Эфиры")
        device.pressDPadDown()
        device.pressDPadDown()
        awaitRailFocus("Категории")
    }

    private fun awaitRailFocus(description: String) {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (focusedRailDescription() != description && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(40)
        }
        assertEquals("Expected rail item did not gain focus", description, focusedRailDescription())
    }

    private fun assertFocusRemains(description: String) {
        // Observe beyond the three-frame restoration coroutine; a one-shot assertion can miss it.
        val deadline = SystemClock.elapsedRealtime() + 800
        do {
            assertEquals("Asynchronous catalog result stole focus", description,
                focusedRailDescription())
            SystemClock.sleep(40)
        } while (SystemClock.elapsedRealtime() < deadline)
    }

    private fun focusedRailDescription(): String? {
        val focused = device.findObject(By.focused(true)) ?: return null
        // In the actual Compose accessibility tree the focused control owns a separate,
        // non-focusable description child. Inspect only this focused node's subtree, never
        // siblings or arbitrary ancestors. A container spanning several rail items is rejected.
        val descriptions = mutableSetOf<String>()
        fun collect(node: UiObject2) {
            node.contentDescription?.takeIf { it in railDescriptions }?.let(descriptions::add)
            node.children.forEach(::collect)
        }
        collect(focused)
        return descriptions.singleOrNull()
    }

    private inner class Fixture(selectedKey: String?) {
        val page = mutableStateOf(Page())
        val selected = mutableStateOf(selectedKey)

        fun publish(channels: List<Channel> = emptyList(), error: String? = null) {
            instrumentation.runOnMainSync {
                page.value = page.value.copy(channels = channels, error = error, busy = false)
            }
        }

        fun loadingNextGeneration() {
            instrumentation.runOnMainSync { page.value = Page(revision = page.value.revision + 1) }
        }
    }

    private data class Page(val channels: List<Channel> = emptyList(), val busy: Boolean = true,
        val error: String? = null, val revision: Long = 0)

    private companion object {
        val channels = listOf("first", "second", "third").map {
            Channel(it, it, "Fixture $it", "Focus restoration fixture", "Test", "")
        }
        val railDescriptions = setOf("Эфиры", "Поиск", "Категории", "Отслеживаемые", "Настройки")
    }
}
