package com.oprawa.twitch20

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.oprawa.twitch20.settings.SettingsStore
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against real app data. No click coordinates, API fixtures or mouse/touch events. */
@RunWith(AndroidJUnit4::class)
class TvNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = "com.oprawa.twitch20"

    private lateinit var initialSettings: AppSettings

    @Before fun launch() {
        initialSettings = savedSettings()
        device.pressHome()
        val intent = Intent().setComponent(ComponentName(packageName, "$packageName.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(intent)
        assertTrue("Приложение не открыло меню", device.wait(Until.hasObject(By.desc("Поиск")), 15_000))
        assertEquals(packageName, device.currentPackageName)
        // Logged-in startup is Following; these real-stream regressions explicitly choose discovery.
        focusDescription("Рекомендации")
        device.pressDPadCenter()
        awaitCatalogTitle("Рекомендации")
        awaitCatalogSettled()
    }

    @After fun restoreSettings() {
        // Test cleanup only; assertions below verify persistence through the real remote UI.
        device.pressHome()
        SettingsStore(instrumentation.targetContext).save(initialSettings)
    }

    @Test fun remoteFocusPreviewsCatalogSectionsAndSettingsRemainsTheOnlyBottomDestination() {
        assertFalse(device.hasObject(By.descStartsWith("Аккаунт: ")))
        assertFalse(device.hasObject(By.descStartsWith("О приложении: ")))
        assertFalse(device.hasObject(By.desc("Войти в Twitch")))
        focusDescription("Категории") // No OK: the focused section must preview itself.
        awaitCatalogTitle("Категории")
        assertFalse("Фокус не сменил раздел", hasCatalogTitle("Рекомендации"))
        assertTrue("Заголовок категорий не появился", device.findObjects(By.text("Категории")).size >= 2)
        awaitCatalogSettled()
        assertTrue("Загрузка украла фокус у раздела", targetOrAncestorHasFocus(device.findObject(By.desc("Категории"))!!))
        focusDescription("Рекомендации")
        awaitCatalogTitle("Рекомендации")
        openSettings()
        assertNotNull(device.findObject(By.descStartsWith("Аккаунт: ")))
        assertNotNull(device.findObject(By.descStartsWith("О приложении: ")))
        assertFalse(device.hasObject(By.text("Сохранить")))
        assertFalse(device.hasObject(By.text("Отменить")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.descStartsWith("Аккаунт: ")), 5_000))
        assertTrue(device.hasObject(By.desc("Рекомендации")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun railPreviewBackRestoresTheOriginalCardAndScrollPosition() {
        val card = device.findObject(By.focused(true)) ?: throw AssertionError("Нет фокуса карточки")
        val original = card.contentDescription ?: throw AssertionError("Для проверки нужен каталог эфиров")
        assertTrue(original.contains("В ЭФИРЕ"))
        val top = card.visibleBounds.top
        focusDescription("Категории")
        awaitCatalogTitle("Категории")
        awaitCatalogSettled()
        assertTrue(targetOrAncestorHasFocus(device.findObject(By.desc("Категории"))!!))
        device.pressBack()
        awaitCatalogTitle("Рекомендации")
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true)) ?: throw AssertionError("Back потерял фокус")
        assertEquals("Back после предпросмотра вернул другую карточку", original, restored.contentDescription)
        assertEquals("Back после предпросмотра сдвинул каталог", top, restored.visibleBounds.top)
        assertFalse(device.hasObject(By.text("Выйти из Twitch 2.0?")))
    }

    @Test fun searchOpensWithTheQueryFieldFocused() {
        focusDescription("Каналы")
        awaitCatalogTitle("Каналы")
        focusDescription("Поиск")
        assertTrue("Фокус поиска не должен заменять текущий каталог", hasCatalogTitle("Каналы"))
        device.pressDPadCenter()
        assertTrue("Поиск не открыл поле ввода",
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
        awaitStableFocus()
        val field = device.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Поле поиска исчезло")
        assertTrue("Поиск должен сразу принимать ввод, а не фокусировать кнопку пустого каталога",
            targetOrAncestorHasFocus(field))
    }

    @Test fun previewModeChangesImmediatelyAndBackKeepsIt() {
        val before = savedSettings()
        openSettings()
        openEditorPage("Интерфейс")
        focusDescription("Предпросмотр")
        if (before.cardPreviewEnabled) device.pressDPadLeft() else device.pressDPadRight()
        awaitStableFocus()
        val after = savedSettings()
        assertTrue(before.cardPreviewEnabled != after.cardPreviewEnabled ||
            before.previewSoundEnabled != after.previewSoundEnabled)
        device.pressBack()
        device.pressBack()
        assertEquals(after, savedSettings())
    }

    @Test fun platformLongOkOpensChannelAndBackRestoresCard() {
        val original = device.findObject(By.focused(true))
            ?: throw AssertionError("Нет фокуса карточки после загрузки")
        val description = original.contentDescription
            ?: throw AssertionError("Для проверки длинного OK нужен настоящий каталог эфиров")
        assertTrue("Начальный фокус не на карточке эфира", description.contains("В ЭФИРЕ"))
        val channelName = description.substringBefore(", ")
        val originalTop = original.visibleBounds.top

        // Uses Android's own long-press timing/flag, reproducing the 500ms vs 550ms regression.
        device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
        assertTrue("Длинный OK не открыл страницу канала", device.wait(Until.hasObject(By.text("Клипы")), 25_000))
        assertFalse("Длинный OK запустил плеер", device.hasObject(By.desc("Пауза")))
        assertFalse("Длинный OK запустил подключение к видео", device.hasObject(By.text("Подключение к Twitch…")))
        device.pressBack()
        awaitCatalogTitle("Рекомендации")
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true))
            ?: throw AssertionError("Back потерял фокус карточки")
        assertTrue("Back вернул другой канал", restored.contentDescription?.startsWith("$channelName, ") == true)
        assertEquals("Back сдвинул строку каталога", originalTop, restored.visibleBounds.top)
    }

    @Test fun chatModesAreExclusiveAndInactiveProfileEditingDoesNotEnableIt() {
        startLiveHud()
        if (!savedSettings().overlayEnabled) activatePlayerChat("Чат:")
        assertEquals(ChatMode.OVERLAY, savedSettings().chatMode)
        activatePlayerChat("Кинотеатр:")
        assertEquals(ChatMode.THEATER, savedSettings().chatMode)
        activatePlayerChat("Кинотеатр:")
        assertEquals(ChatMode.OFF, savedSettings().chatMode)
        device.pressBack()
        device.pressBack()
        awaitCatalogTitle("Рекомендации")

        openSettings()
        openViewingProfile("Overlay")
        val before = savedSettings()
        val after = changeTextSize(ChatMode.OVERLAY, before.overlayStyle.textSizeSp)
        assertEquals(ChatMode.OFF, savedSettings().chatMode)
        assertEquals(before.theaterStyle, savedSettings().theaterStyle)
        assertEquals(after, savedSettings().overlayStyle.textSizeSp)
        assertTrue(device.hasObject(By.textStartsWith("Профиль выключен")))
    }

    @Test fun accountAndAboutAreNestedInSettingsAndBackRestoresTheirRows() {
        openSettings()
        focusDescription("Аккаунт")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Аккаунт Twitch")), 5_000))
        device.pressBack()
        awaitSettingsOverview()
        awaitStableFocus()
        assertTrue("Back не вернул строку аккаунта", targetOrAncestorHasFocus(findDescription("Аккаунт")!!))
        focusDescription("О приложении")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.desc("Авторы и лицензии")), 5_000))
        device.pressBack()
        awaitSettingsOverview()
        awaitStableFocus()
        assertTrue("Back не вернул строку приложения", targetOrAncestorHasFocus(findDescription("О приложении")!!))
    }

    @Test fun settingsProfilesSaveIndependentlyAndClockHasItsOwnPage() {
        openSettings()
        openViewingProfile("Кинотеатр")
        val before = savedSettings()
        assertFalse(device.hasObject(By.descStartsWith("Фон чата:")))
        assertFalse(device.hasObject(By.descStartsWith("Размер чата:")))
        val after = changeTextSize(ChatMode.THEATER, before.theaterStyle.textSizeSp)
        assertEquals(after, savedSettings().theaterStyle.textSizeSp)
        assertEquals(before.overlayStyle, savedSettings().overlayStyle)
        device.pressBack()
        awaitStableFocus()
        assertTrue("Back не вернул профиль кинотеатра", targetOrAncestorHasFocus(findDescription("Кинотеатр")!!))
        device.pressBack()
        openEditorPage("Интерфейс")
        assertFalse(device.hasObject(By.descStartsWith("На главной и в плеере:")))
        openEditorPage("Часы")
        val clockBefore = savedSettings().showClock
        focusDescription("На главной и в плеере")
        if (clockBefore) device.pressDPadLeft() else device.pressDPadRight()
        awaitStableFocus()
        assertEquals(!clockBefore, savedSettings().showClock)
        assertEquals(after, savedSettings().theaterStyle.textSizeSp)
    }

    @Test fun focusedTextSizeChangesWithArrowWithoutAnExtraSave() {
        val before = savedSettings()
        openSettings()
        openViewingProfile("Overlay")
        focusDescription("Размер текста")
        assertEquals("Один фокус изменил сохранённое значение", before, savedSettings())
        val after = changeTextSize(ChatMode.OVERLAY, before.overlayStyle.textSizeSp)
        device.pressBack()
        device.pressBack()
        device.pressBack()
        assertEquals(after, savedSettings().overlayStyle.textSizeSp)
    }

    private fun changeTextSize(mode: ChatMode, before: Int): Int {
        focusDescription("Размер текста")
        if (before == 22) device.pressDPadLeft() else device.pressDPadRight()
        awaitStableFocus()
        return savedSettings().styleFor(mode).textSizeSp.also { assertTrue("Стрелка не изменила размер", it != before) }
    }

    private fun startLiveHud() {
        val card = device.findObject(By.focused(true)) ?: throw AssertionError("Нет фокуса первого эфира")
        assertTrue(card.contentDescription?.contains("В ЭФИРЕ") == true)
        device.pressDPadCenter()
        assertTrue("Эфир не открыл плеер", device.wait(Until.hasObject(By.desc("Пауза")), 45_000))
    }

    private fun activatePlayerChat(prefix: String) {
        if (!device.hasObject(By.desc("Качество"))) device.pressDPadCenter()
        focusTarget(prefix) { device.findObject(By.descStartsWith(prefix)) }
        device.pressDPadCenter()
        awaitStableFocus()
    }

    @Test fun oneBackClosesPlayerChatProfileAndKeepsHud() {
        startLiveHud()
        if (!device.hasObject(By.desc("Качество"))) device.pressDPadCenter()
        focusTarget("Чат") { device.findObject(By.descStartsWith("Чат:")) }
        device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
        assertTrue("Не открылся профиль Overlay",
            device.wait(Until.hasObject(By.descStartsWith("Размер чата:")), 5_000))
        assertFalse(device.hasObject(By.descStartsWith("Показывать:")))
        awaitStableFocus()

        device.pressBack()

        assertTrue("Первый Back оставил панель",
            device.wait(Until.gone(By.descStartsWith("Размер чата:")), 3_000))
        assertTrue("Back скрыл HUD вместе с панелью", device.hasObject(By.desc("Качество")))
        assertTrue("Back остановил воспроизведение", device.hasObject(By.desc("Пауза")))
        assertFalse("Back вышел в каталог", hasCatalogTitle("Рекомендации"))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun authenticatedCatalogLoadsBeyondFirstThirtyAndRestoresCard() {
        val initialCard = device.findObject(By.focused(true))?.contentDescription
            ?: throw AssertionError("Нет начальной карточки")
        openSettings()
        focusDescription("Аккаунт")
        device.pressDPadCenter()
        assertTrue("Для Helix-проверки нужен уже сохранённый аккаунт",
            device.wait(Until.hasObject(By.text("Аккаунт подключён")), 8_000))
        device.pressBack()
        device.pressBack()
        focusDescription("Рекомендации")
        device.pressDPadCenter()
        awaitCatalogSettled()
        focusDescription(initialCard)
        val names = mutableSetOf<String>()
        repeat(14) {
            awaitCatalogSettled()
            val card = device.findObject(By.focused(true)) ?: throw AssertionError("Потерян фокус")
            val description = card.contentDescription.orEmpty()
            assertTrue("Прокрутка остановилась на первой странице: $description", description.contains("В ЭФИРЕ"))
            assertTrue("D-pad не переместился на следующую строку", names.add(description.substringBefore(", ")))
            if (it < 13) device.pressDPadDown()
        }
        val selected = device.findObject(By.focused(true))!!
        val name = selected.contentDescription!!.substringBefore(", ")
        val top = selected.visibleBounds.top
        device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
        val opened = device.wait(Until.hasObject(By.text("Клипы")), 25_000)
        if (!opened) {
            val evidence = instrumentation.targetContext.filesDir
            device.dumpWindowHierarchy(java.io.File(evidence, "pagination-details-failure.xml"))
            device.takeScreenshot(java.io.File(evidence, "pagination-details-failure.png"))
        }
        assertTrue("Long OK did not open details for $name; focused=${device.findObject(By.focused(true))?.contentDescription}", opened)
        device.pressBack()
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true))!!
        assertTrue("Возврат потерял карточку загруженной страницы", restored.contentDescription?.startsWith("$name, ") == true)
        assertEquals("Возврат сдвинул каталог", top, restored.visibleBounds.top)
    }

    // Refresh restoration uses tools/qa_catalog_refresh.py with fresh native AX snapshots.

    private fun hasCatalogTitle(title: String): Boolean {
        val rail = device.findObject(By.desc("Рекомендации")) ?: return false
        val railRight = nearestFocusableAncestor(rail).visibleBounds.right
        // Expanded rail labels and the actual content heading intentionally share vocabulary.
        return device.findObjects(By.text(title)).any { it.visibleBounds.left >= railRight }
    }

    private fun awaitCatalogTitle(title: String) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (!hasCatalogTitle(title) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Не появился заголовок каталога $title", hasCatalogTitle(title))
    }

    private fun savedSettings() = SettingsStore(instrumentation.targetContext).state.value

    private fun openSettings() {
        focusDescription("Настройки")
        device.pressDPadCenter()
        awaitSettingsOverview()
        awaitStableFocus()
    }

    private fun awaitSettingsOverview() {
        // The rail has its own Settings label; account is only exposed by the overview.
        assertTrue("Обзор настроек не открылся", device.wait(Until.hasObject(By.descStartsWith("Аккаунт: ")), 5_000))
        assertTrue("Нет заголовка настроек", device.hasObject(By.text("Настройки")))
    }

    private fun openViewingProfile(label: String) {
        openEditorPage("Просмотр")
        openEditorPage(label)
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Размер текста:")), 3_000))
    }

    private fun openEditorPage(label: String) {
        focusDescription(label)
        device.pressDPadCenter()
        assertTrue("Не открылась страница $label", device.wait(Until.gone(By.descStartsWith("Аккаунт: ")), 3_000))
        awaitStableFocus()
    }

    private fun focusDescription(description: String) = focusTarget(description) { findDescription(description) }

    private fun findDescription(description: String): UiObject2? =
        device.findObject(By.desc(description)) ?: device.findObject(By.descStartsWith("$description: "))

    /** Move toward observed node bounds; every step is based on current focus and target geometry. */
    private fun focusTarget(label: String, find: () -> UiObject2?) {
        repeat(48) { attempt ->
            try {
            val target = find()
            if (target == null) {
                // A settings profile can be below the viewport; traverse actual focusable rows.
                if (attempt < 24) device.pressDPadDown() else device.pressDPadUp()
                device.waitForIdle(500)
                return@repeat
            }
            if (targetOrAncestorHasFocus(target)) return
            val focused = device.wait(Until.findObject(By.focused(true)), 2_000)
                ?: run {
                    // Closing Android TV's IME may leave the host outside keyboard mode.
                    // A real remote direction re-enters navigation; restoration assertions
                    // still use awaitStableFocus and never manufacture a focused node.
                    device.pressDPadDown()
                    return@repeat
                }
            run {
                val from = focused.visibleBounds
                val to = nearestFocusableAncestor(target).visibleBounds
                when {
                    to.centerX() < from.left -> device.pressDPadLeft()
                    to.centerX() > from.right -> device.pressDPadRight()
                    to.centerY() < from.top -> device.pressDPadUp()
                    to.centerY() > from.bottom -> device.pressDPadDown()
                    to.centerX() < from.centerX() -> device.pressDPadLeft()
                    else -> device.pressDPadRight()
                }
            }
            device.waitForIdle(500)
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Rail expansion and account validation may replace an accessibility node
                // between lookup and ancestry reads. Retry from fresh nodes, with the same
                // bounded navigation budget and the same final focus assertion.
                device.waitForIdle(500)
            }
        }
        throw AssertionError("D-pad не довёл фокус до $label; текущий: ${device.findObject(By.focused(true))?.contentDescription}")
    }

    private fun targetOrAncestorHasFocus(target: UiObject2): Boolean {
        var node: UiObject2? = target
        while (node != null) {
            if (node.isFocused) return true
            // Only the target's own ancestry is considered. A nearby sibling cannot satisfy this.
            node = node.parent
        }
        return false
    }

    private fun nearestFocusableAncestor(target: UiObject2): UiObject2 {
        var node: UiObject2? = target
        while (node != null) {
            if (node.isFocusable) return node
            node = node.parent
        }
        return target
    }

    private fun awaitCatalogSettled() {
        assertTrue("Каталог не завершил загрузку", device.wait(Until.gone(By.text("Обновление…")), 25_000))
        assertTrue("Начальная загрузка не завершилась", device.wait(Until.gone(By.text("Загружаем…")), 2_000))
        // Both a real data result and an accessible error/retry state are valid navigation prerequisites.
        awaitStableFocus()
    }

    private fun awaitStableFocus() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var previous: String? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val focused = device.findObject(By.focused(true))
            val snapshot = focused?.let { "${it.visibleBounds}|${it.text}|${it.contentDescription}" }
            val now = SystemClock.elapsedRealtime()
            if (snapshot != null && snapshot == previous) {
                if (now - stableSince >= 250) return
            } else {
                previous = snapshot
                stableSince = now
            }
            SystemClock.sleep(100)
        }
        val evidence = instrumentation.targetContext.cacheDir
        device.dumpWindowHierarchy(java.io.File(evidence, "focus-failure.xml"))
        device.takeScreenshot(java.io.File(evidence, "focus-failure.png"))
        throw AssertionError("Начальный D-pad фокус не установился за 5 секунд; последний: $previous")
    }
}
