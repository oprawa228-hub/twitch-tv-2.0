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

    @Before fun launch() {
        device.pressHome()
        val intent = Intent().setComponent(ComponentName(packageName, "$packageName.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(intent)
        assertTrue("Приложение не открыло каталог", device.wait(Until.hasObject(By.text("Прямые эфиры")), 15_000))
        assertEquals(packageName, device.currentPackageName)
        awaitCatalogSettled()
    }

    @Test fun remoteCanReachCatalogSectionsAndSettingsAndReturn() {
        focusText("Категории")
        device.pressDPadCenter()
        assertTrue("Каталог не сменил раздел", device.wait(Until.gone(By.text("Прямые эфиры")), 8_000))
        assertTrue("Заголовок категорий не появился", device.findObjects(By.text("Категории")).size >= 2)
        awaitCatalogSettled()
        focusText("Эфиры")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Прямые эфиры")), 8_000))
        awaitCatalogSettled()
        openSettings()
        assertNotNull(device.findObject(By.descStartsWith("Чат поверх видео: ")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        assertTrue(device.hasObject(By.text("Прямые эфиры")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun previewCancelDoesNotSaveAndExplicitSaveSurvivesReopening() {
        openSettings()
        val original = chatDescription()
        val originalTheater = device.hasObject(By.desc("Кинотеатр: Включён"))
        assertTrue(original == "Чат поверх видео: Включён" || original == "Чат поверх видео: Выключен")
        val changed = if (original == "Чат поверх видео: Включён") "Чат поверх видео: Выключен" else "Чат поверх видео: Включён"
        // First setting owns focus on entry. A real remote arrow changes only its draft.
        focusDescription(original)
        device.pressDPadRight()
        assertTrue(device.wait(Until.hasObject(By.desc(changed)), 3_000))
        assertTrue(device.hasObject(By.text("Есть несохранённые изменения")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        openSettings()
        assertEquals("Back сохранил черновик", original, chatDescription())
        focusDescription(original)
        device.pressDPadRight()
        focusText("Сохранить")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        try {
            openSettings()
            assertEquals("Сохранённое значение потерялось при повторном открытии", changed, chatDescription())
            assertFalse(device.hasObject(By.text("Есть несохранённые изменения")))
        } finally {
            // Restore the original setting. Full process-restart verification is owned by the root runner.
            if (!device.hasObject(By.text("Предпросмотр"))) openSettings()
            val current = chatDescription()
            if (current != original) {
                focusDescription(current)
                device.pressDPadRight()
            }
            if (originalTheater && device.hasObject(By.desc("Кинотеатр: Выключен"))) {
                focusDescription("Кинотеатр: Выключен")
                device.pressDPadCenter()
            }
            focusText("Сохранить")
            device.pressDPadCenter()
        }
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
        assertFalse("Длинный OK запустил плеер", device.hasObject(By.text("Пауза")))
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
        val original = SettingsStore(instrumentation.targetContext).state.value
        openSettings()
        // Start from either persisted mode, and explicitly select overlay.
        if (chatDescription().endsWith("Выключен")) {
            focusDescription("Чат поверх видео: Выключен")
            device.pressDPadCenter()
        }
        assertTrue(device.hasObject(By.desc("Чат поверх видео: Включён")))
        assertTrue(device.hasObject(By.desc("Кинотеатр: Выключен")))
        focusDescription("Кинотеатр: Выключен")
        device.pressDPadCenter()
        assertTrue(device.hasObject(By.desc("Кинотеатр: Включён")))
        assertTrue(device.hasObject(By.desc("Чат поверх видео: Выключен")))
        device.pressDPadCenter()
        assertTrue(device.hasObject(By.desc("Кинотеатр: Выключен")))
        assertTrue(device.hasObject(By.desc("Чат поверх видео: Выключен")))

        val oldSize = original.overlayStyle.textSizeSp
        openEditorPage("Настроить чат поверх видео")
        focusDescription("Чат — размер текста: $oldSize")
        val newSize = if (oldSize == 24) oldSize - 2 else oldSize + 2
        if (oldSize == 24) device.pressDPadLeft() else device.pressDPadRight()
        assertTrue(device.hasObject(By.desc("Чат — размер текста: $newSize")))
        assertTrue(device.hasObject(By.text("Пример: Чат поверх видео")))
        assertTrue(device.hasObject(By.text("После сохранения: Без чата")))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.desc("Чат поверх видео: Выключен")), 3_000))
        assertTrue(device.hasObject(By.desc("Чат поверх видео: Выключен")))
        assertTrue(device.hasObject(By.desc("Кинотеатр: Выключен")))
        openEditorPage("Настроить кинотеатр")
        focusDescription("Кинотеатр — размер текста: ${original.theaterStyle.textSizeSp}")
        assertTrue(device.hasObject(By.text("Пример: Кинотеатр")))
        assertTrue(device.hasObject(By.text("После сохранения: Без чата")))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.desc("Чат поверх видео: Выключен")), 3_000))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        assertEquals("Отмена сохранила режим или чужой профиль", original,
            SettingsStore(instrumentation.targetContext).state.value)
    }

    @Test fun nestedUpdatesPreserveDraftUntilSettingsCancel() {
        val saved = SettingsStore(instrumentation.targetContext).state.value
        openSettings()
        val original = chatDescription()
        val changed = if (original == "Чат поверх видео: Включён") "Чат поверх видео: Выключен" else "Чат поверх видео: Включён"
        focusDescription(original)
        device.pressDPadRight()
        assertTrue(device.wait(Until.hasObject(By.desc(changed)), 3_000))
        val overlaySize = saved.overlayStyle.textSizeSp
        val theaterSize = saved.theaterStyle.textSizeSp
        val overlayDraft = if (overlaySize == 24) overlaySize - 2 else overlaySize + 2
        val theaterDraft = if (theaterSize == 24) theaterSize - 2 else theaterSize + 2
        openEditorPage("Настроить чат поверх видео")
        focusDescription("Чат — размер текста: $overlaySize")
        if (overlaySize == 24) device.pressDPadLeft() else device.pressDPadRight()
        device.pressBack()
        openEditorPage("Настроить кинотеатр")
        focusDescription("Кинотеатр — размер текста: $theaterSize")
        if (theaterSize == 24) device.pressDPadLeft() else device.pressDPadRight()
        device.pressBack()
        focusText("Проверить обновления")
        device.pressDPadCenter()
        assertTrue("Не открылся вложенный экран обновлений", device.wait(Until.hasObject(By.text("Twitch 2.0")), 5_000))
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Предпросмотр")), 5_000))
        assertEquals("Вложенный экран потерял черновик", changed, chatDescription())
        assertTrue(device.hasObject(By.text("Есть несохранённые изменения")))
        openEditorPage("Настроить чат поверх видео")
        focusDescription("Чат — размер текста: $overlayDraft")
        device.pressBack()
        openEditorPage("Настроить кинотеатр")
        focusDescription("Кинотеатр — размер текста: $theaterDraft")
        device.pressBack()
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        openSettings()
        assertEquals("Отмена после вложенного экрана сохранила черновик", original, chatDescription())
        assertEquals("Отмена сохранила настройки профилей", saved,
            SettingsStore(instrumentation.targetContext).state.value)
        device.pressBack()
    }

    @Test fun settingsPagesKeepOneDraftAndBackRestoresTheInitiatingRow() {
        val saved = SettingsStore(instrumentation.targetContext).state.value
        openSettings()
        openEditorPage("Настроить кинотеатр")
        val before = saved.theaterStyle.textSizeSp
        val changed = if (before == 24) 22 else before + 2
        focusDescription("Кинотеатр — размер текста: $before")
        if (before == 24) device.pressDPadLeft() else device.pressDPadRight()
        assertTrue(device.hasObject(By.desc("Кинотеатр — размер текста: $changed")))
        assertEquals("Изменение подстраницы записало черновик", saved, SettingsStore(instrumentation.targetContext).state.value)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Чат поверх видео: ")), 3_000))
        awaitStableFocus()
        val theaterLauncher = device.findObject(By.desc("Настроить кинотеатр"))
        assertTrue("Back не вернул фокус инициатору",
            theaterLauncher != null && targetOrAncestorHasFocus(theaterLauncher))
        openEditorPage("Интерфейс")
        focusDescription("Часы: ${if (saved.showClock) "Показаны" else "Скрыты"}")
        device.pressDPadCenter()
        device.pressBack()
        openEditorPage("Настроить кинотеатр")
        assertTrue("Переход на другую страницу потерял черновик профиля",
            device.wait(Until.hasObject(By.desc("Кинотеатр — размер текста: $changed")), 3_000))
        device.pressBack()
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        assertEquals("Back из корня должен отменить весь черновик", saved, SettingsStore(instrumentation.targetContext).state.value)
    }

    @Test fun oneBackClosesPlayerSettingsAndKeepsHud() {
        val card = device.findObject(By.focused(true))
            ?: throw AssertionError("Нет фокуса первого прямого эфира")
        assertTrue("Для проверки нужен настоящий прямой эфир",
            card.contentDescription?.contains("В ЭФИРЕ") == true)
        device.pressDPadCenter()
        assertTrue("Прямой эфир не начал воспроизведение",
            device.wait(Until.hasObject(By.text("Пауза")), 45_000))
        // Reveal only when hidden; a confirm on visible HUD would pause live playback.
        if (!device.hasObject(By.text("Качество"))) device.pressDPadCenter()
        focusText("Настройки")
        device.pressDPadCenter()
        assertTrue("Не открылась панель настроек плеера",
            device.wait(Until.hasObject(By.text("Просмотр")), 5_000))
        awaitStableFocus()

        device.pressBack() // Exactly one physical Back down/up pair.

        assertTrue("Первый Back только переместил фокус и оставил панель",
            device.wait(Until.gone(By.text("Просмотр")), 3_000))
        assertTrue("Back скрыл HUD вместе с панелью", device.hasObject(By.text("Качество")))
        assertTrue("Back остановил воспроизведение", device.hasObject(By.text("Пауза")))
        assertFalse("Back вышел в каталог", device.hasObject(By.text("Прямые эфиры")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun authenticatedCatalogLoadsBeyondFirstThirtyAndRestoresCard() {
        assertTrue("Для Helix-проверки нужен уже сохранённый аккаунт", device.hasObject(By.descStartsWith("Аккаунт: ")))
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
        assertTrue(device.wait(Until.hasObject(By.text("Клипы")), 25_000))
        device.pressBack()
        awaitCatalogSettled()
        val restored = device.findObject(By.focused(true))!!
        assertTrue("Возврат потерял карточку загруженной страницы", restored.contentDescription?.startsWith("$name, ") == true)
        assertEquals("Возврат сдвинул каталог", top, restored.visibleBounds.top)
    }

    // Refresh restoration uses tools/qa_catalog_refresh.py with fresh native AX snapshots.

    private fun openSettings() {
        focusText("Настройки")
        device.pressDPadCenter()
        assertTrue("Настройки не открыли пример", device.wait(Until.hasObject(By.text("Предпросмотр")), 5_000))
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Чат поверх видео: ")), 3_000))
        awaitStableFocus()
    }

    private fun chatDescription(): String = device.findObject(By.descStartsWith("Чат поверх видео: "))?.contentDescription
        ?: throw AssertionError("Не найден параметр чата")

    private fun openEditorPage(label: String) {
        focusDescription(label)
        device.pressDPadCenter()
        assertTrue("Не открылась страница $label", device.wait(Until.gone(By.descStartsWith("Чат поверх видео: ")), 3_000))
        awaitStableFocus()
    }

    private fun focusText(text: String) = focusTarget("text=$text") { device.findObject(By.text(text)) }
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
