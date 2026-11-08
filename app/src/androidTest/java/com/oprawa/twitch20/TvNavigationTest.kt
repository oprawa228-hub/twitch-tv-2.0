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
        assertTrue("Приложение не открыло каталог", device.wait(Until.hasObject(By.text("Прямые эфиры")), 15_000))
        assertEquals(packageName, device.currentPackageName)
        awaitCatalogSettled()
    }

    @After fun restoreSettings() {
        // Test cleanup only; assertions below verify persistence through the real remote UI.
        device.pressHome()
        SettingsStore(instrumentation.targetContext).save(initialSettings)
    }

    @Test fun remoteFocusPreviewsCatalogSectionsAndSettingsRemainsTheOnlyBottomDestination() {
        assertFalse(device.hasObject(By.desc("Аккаунт")))
        assertFalse(device.hasObject(By.desc("О приложении")))
        assertFalse(device.hasObject(By.desc("Войти в Twitch")))
        focusDescription("Категории") // No OK: the focused section must preview itself.
        assertTrue("Фокус не сменил раздел", device.wait(Until.gone(By.text("Прямые эфиры")), 8_000))
        assertTrue("Заголовок категорий не появился", device.findObjects(By.text("Категории")).size >= 2)
        awaitCatalogSettled()
        assertTrue("Загрузка украла фокус у раздела", targetOrAncestorHasFocus(device.findObject(By.desc("Категории"))!!))
        focusDescription("Эфиры")
        assertTrue(device.wait(Until.hasObject(By.text("Прямые эфиры")), 8_000))
        openSettings()
        assertNotNull(device.findObject(By.desc("Аккаунт")))
        assertNotNull(device.findObject(By.desc("О приложении")))
        assertFalse(device.hasObject(By.text("Сохранить")))
        assertFalse(device.hasObject(By.text("Отменить")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Ваш эфир")), 5_000))
        assertTrue(device.hasObject(By.desc("Эфиры")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun railPreviewBackRestoresTheOriginalCardAndScrollPosition() {
        val card = device.findObject(By.focused(true)) ?: throw AssertionError("Нет фокуса карточки")
        val original = card.contentDescription ?: throw AssertionError("Для проверки нужен каталог эфиров")
        assertTrue(original.contains("В ЭФИРЕ"))
        val top = card.visibleBounds.top
        focusDescription("Категории")
        assertTrue(device.wait(Until.gone(By.text("Прямые эфиры")), 8_000))
        awaitCatalogSettled()
        assertTrue(targetOrAncestorHasFocus(device.findObject(By.desc("Категории"))!!))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Прямые эфиры")), 8_000))
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true)) ?: throw AssertionError("Back потерял фокус")
        assertEquals("Back после предпросмотра вернул другую карточку", original, restored.contentDescription)
        assertEquals("Back после предпросмотра сдвинул каталог", top, restored.visibleBounds.top)
        assertFalse(device.hasObject(By.text("Выйти из Twitch 2.0?")))
    }

    @Test fun confirmedToggleSavesImmediatelyAndBackKeepsIt() {
        openSettings()
        openEditorPage("Чат поверх видео")
        val wasEnabled = savedSettings().overlayEnabled
        setProfileEnabled(!wasEnabled)
        assertEquals(!wasEnabled, savedSettings().overlayEnabled)
        assertFalse(device.hasObject(By.text("Сохранить")))
        assertFalse(device.hasObject(By.text("Есть несохранённые изменения")))
        device.pressBack()
        device.pressBack()
        openSettings()
        openEditorPage("Чат поверх видео")
        assertTrue(device.hasObject(By.desc("Показывать: ${if (!wasEnabled) "Включён" else "Выключен"}")))
        assertEquals("Back отменил подтверждённое изменение", !wasEnabled, savedSettings().overlayEnabled)
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
        assertTrue(device.wait(Until.hasObject(By.text("Прямые эфиры")), 8_000))
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true))
            ?: throw AssertionError("Back потерял фокус карточки")
        assertTrue("Back вернул другой канал", restored.contentDescription?.startsWith("$channelName, ") == true)
        assertEquals("Back сдвинул строку каталога", originalTop, restored.visibleBounds.top)
    }

    @Test fun chatModesAreExclusiveAndInactiveProfileEditingDoesNotEnableIt() {
        openSettings()
        openEditorPage("Чат поверх видео")
        setProfileEnabled(true)
        assertEquals(ChatMode.OVERLAY, savedSettings().chatMode)
        device.pressBack()
        openEditorPage("Кинотеатр")
        setProfileEnabled(true)
        assertEquals(ChatMode.THEATER, savedSettings().chatMode)
        assertFalse(savedSettings().overlayEnabled)
        assertFalse("В кинотеатре нет прозрачности", device.hasObject(By.descStartsWith("Фон чата:")))
        assertFalse("Кинотеатр имеет фиксированную ширину", device.hasObject(By.descStartsWith("Размер чата:")))
        setProfileEnabled(false)
        assertEquals(ChatMode.OFF, savedSettings().chatMode)
        device.pressBack()
        openEditorPage("Чат поверх видео")
        val before = savedSettings().overlayStyle.textSizeSp
        val after = if (before == 22) 14 else 22
        val theater = savedSettings().theaterStyle
        selectTextSize(before, after)
        assertEquals(ChatMode.OFF, savedSettings().chatMode)
        assertEquals(theater, savedSettings().theaterStyle)
        assertEquals(after, savedSettings().overlayStyle.textSizeSp)
        assertTrue(device.hasObject(By.text("Профиль выключен")))
    }

    @Test fun accountAndAboutAreNestedInSettingsAndBackRestoresTheirRows() {
        openSettings()
        openEditorPage("Чат поверх видео")
        val before = savedSettings().overlayStyle.textSizeSp
        val after = if (before == 22) 14 else 22
        selectTextSize(before, after)
        device.pressBack()
        focusDescription("Аккаунт")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Аккаунт Twitch")), 5_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Ваш эфир")), 5_000))
        awaitStableFocus()
        assertTrue("Back не вернул строку аккаунта", targetOrAncestorHasFocus(device.findObject(By.desc("Аккаунт"))!!))
        focusDescription("О приложении")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Twitch 2.0")), 5_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Ваш эфир")), 5_000))
        awaitStableFocus()
        assertTrue("Back не вернул строку приложения", targetOrAncestorHasFocus(device.findObject(By.desc("О приложении"))!!))
        assertEquals("Вложенные экраны потеряли подтверждённую настройку", after, savedSettings().overlayStyle.textSizeSp)
    }

    @Test fun settingsProfilesSaveIndependentlyAndClockHasItsOwnPage() {
        openSettings()
        openEditorPage("Кинотеатр")
        val before = savedSettings().theaterStyle.textSizeSp
        val after = if (before == 22) 14 else 22
        val overlay = savedSettings().overlayStyle
        selectTextSize(before, after)
        assertEquals(after, savedSettings().theaterStyle.textSizeSp)
        assertEquals(overlay, savedSettings().overlayStyle)
        device.pressBack()
        awaitStableFocus()
        assertTrue("Back не вернул профиль кинотеатра", targetOrAncestorHasFocus(device.findObject(By.desc("Кинотеатр"))!!))
        openEditorPage("Интерфейс")
        assertFalse(device.hasObject(By.descStartsWith("На главной и в плеере:")))
        device.pressBack()
        openEditorPage("Часы")
        val clockBefore = savedSettings().showClock
        focusDescription("На главной и в плеере: ${if (clockBefore) "Показаны" else "Скрыты"}")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.desc("На главной и в плеере: ${if (!clockBefore) "Показаны" else "Скрыты"}")), 3_000))
        assertEquals(!clockBefore, savedSettings().showClock)
        assertEquals(after, savedSettings().theaterStyle.textSizeSp)
    }

    @Test fun textSizeFocusOnlyPreviewsAndOkPersistsWithoutAnExtraSave() {
        val saved = savedSettings()
        val before = saved.overlayStyle.textSizeSp
        val after = if (before == 22) 14 else 22
        openSettings()
        openEditorPage("Чат поверх видео")
        focusDescription(fontLauncher(before))
        device.pressDPadCenter()
        awaitStableFocus()
        assertTrue("Picker должен открыться на текущем размере",
            targetOrAncestorHasFocus(device.findObject(By.desc("Размер текста: ${fontLabel(before)}"))!!))
        focusDescription("Размер текста: ${fontLabel(after)}")
        assertEquals("Один фокус сохранил значение", saved, savedSettings())
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.desc("Показывать: ${if (saved.overlayEnabled) "Включён" else "Выключен"}")), 3_000))
        awaitStableFocus()
        assertTrue("Back не восстановил строку размера", targetOrAncestorHasFocus(device.findObject(By.desc(fontLauncher(before)))!!))
        assertEquals(saved, savedSettings())
        selectTextSize(before, after)
        assertEquals("OK не сохранил размер", after, savedSettings().overlayStyle.textSizeSp)
        device.pressBack()
        device.pressBack()
        assertEquals("Выход из настроек отменил подтверждённый размер", after, savedSettings().overlayStyle.textSizeSp)
    }

    private fun fontLabel(size: Int): String = when (size) {
        14 -> "Маленький"
        18 -> "Средний"
        22 -> "Большой"
        else -> "Свой размер"
    }

    private fun fontLauncher(size: Int) = "Размер текста: ${fontLabel(size)}"

    private fun selectTextSize(before: Int, after: Int) {
        focusDescription(fontLauncher(before))
        device.pressDPadCenter()
        focusDescription("Размер текста: ${fontLabel(after)}")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Показывать: ")), 3_000))
        assertTrue(device.hasObject(By.desc(fontLauncher(after))))
        awaitStableFocus()
    }

    @Test fun oneBackClosesPlayerChatProfileAndKeepsHud() {
        val card = device.findObject(By.focused(true))
            ?: throw AssertionError("Нет фокуса первого прямого эфира")
        assertTrue("Для проверки нужен настоящий прямой эфир",
            card.contentDescription?.contains("В ЭФИРЕ") == true)
        device.pressDPadCenter()
        assertTrue("Прямой эфир не начал воспроизведение",
            device.wait(Until.hasObject(By.desc("Пауза")), 45_000))
        // Reveal only when hidden; a confirm on visible HUD would pause live playback.
        if (!device.hasObject(By.desc("Качество"))) device.pressDPadCenter()
        focusTarget("Чат") { device.findObject(By.descStartsWith("Чат:")) }
        device.executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER")
        assertTrue("Не открылся профиль чата",
            device.wait(Until.hasObject(By.descStartsWith("Показывать: ")), 5_000))
        awaitStableFocus()

        device.pressBack() // Exactly one physical Back down/up pair.

        assertTrue("Первый Back только переместил фокус и оставил панель",
            device.wait(Until.gone(By.descStartsWith("Показывать: ")), 3_000))
        assertTrue("Back скрыл HUD вместе с панелью", device.hasObject(By.desc("Качество")))
        assertTrue("Back остановил воспроизведение", device.hasObject(By.desc("Пауза")))
        assertFalse("Back вышел в каталог", device.hasObject(By.text("Прямые эфиры")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun authenticatedCatalogLoadsBeyondFirstThirtyAndRestoresCard() {
        val initialCard = device.findObject(By.focused(true))?.contentDescription
            ?: throw AssertionError("Нет начальной карточки")
        openSettings()
        focusDescription("Аккаунт")
        device.pressDPadCenter()
        assertTrue("Для Helix-проверки нужен уже сохранённый аккаунт",
            device.wait(Until.hasObject(By.textStartsWith("Вы вошли: ")), 8_000))
        device.pressBack()
        device.pressBack()
        focusDescription("Эфиры")
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

    private fun savedSettings() = SettingsStore(instrumentation.targetContext).state.value

    private fun openSettings() {
        focusDescription("Настройки")
        device.pressDPadCenter()
        assertTrue("Настройки не открылись", device.wait(Until.hasObject(By.text("Ваш эфир")), 5_000))
        assertTrue(device.hasObject(By.desc("Аккаунт")))
        awaitStableFocus()
    }

    private fun setProfileEnabled(enabled: Boolean) {
        val desired = "Показывать: ${if (enabled) "Включён" else "Выключен"}"
        if (!device.hasObject(By.desc(desired))) {
            focusTarget("Показывать") { device.findObject(By.descStartsWith("Показывать: ")) }
            device.pressDPadCenter()
        }
        assertTrue(device.wait(Until.hasObject(By.desc(desired)), 3_000))
    }

    private fun openEditorPage(label: String) {
        focusDescription(label)
        device.pressDPadCenter()
        assertTrue("Не открылась страница $label", device.wait(Until.gone(By.desc("Аккаунт")), 3_000))
        awaitStableFocus()
    }

    private fun focusDescription(description: String) = focusTarget(description) { device.findObject(By.desc(description)) }

    /** Move toward observed node bounds; every step is based on current focus and target geometry. */
    private fun focusTarget(label: String, find: () -> UiObject2?) {
        repeat(48) { attempt ->
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
