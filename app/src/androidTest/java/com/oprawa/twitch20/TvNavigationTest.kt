package com.oprawa.twitch20

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
    }

    @Test fun remoteCanReachCatalogSectionsAndSettingsAndReturn() {
        focusText("Категории")
        device.pressDPadCenter()
        assertTrue("Каталог не сменил раздел", device.wait(Until.gone(By.text("Прямые эфиры")), 8_000))
        assertTrue("Заголовок категорий не появился", device.findObjects(By.text("Категории")).size >= 2)
        focusText("Эфиры")
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.text("Прямые эфиры")), 8_000))
        openSettings()
        assertNotNull(device.findObject(By.descStartsWith("Чат: ")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Предпросмотр")), 5_000))
        assertTrue(device.hasObject(By.text("Прямые эфиры")))
        assertEquals(packageName, device.currentPackageName)
    }

    @Test fun previewCancelDoesNotSaveAndExplicitSaveSurvivesReopening() {
        openSettings()
        val original = chatDescription()
        assertTrue(original == "Чат: Показан" || original == "Чат: Скрыт")
        val changed = if (original == "Чат: Показан") "Чат: Скрыт" else "Чат: Показан"
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
            focusText("Сохранить")
            device.pressDPadCenter()
        }
    }

    private fun openSettings() {
        focusText("Настройки")
        device.pressDPadCenter()
        assertTrue("Настройки не открыли пример", device.wait(Until.hasObject(By.text("Предпросмотр")), 5_000))
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Чат: ")), 3_000))
    }

    private fun chatDescription(): String = device.findObject(By.descStartsWith("Чат: "))?.contentDescription
        ?: throw AssertionError("Не найден параметр чата")

    private fun focusText(text: String) = focusTarget("text=$text") { device.findObject(By.text(text)) }
    private fun focusDescription(description: String) = focusTarget(description) { device.findObject(By.desc(description)) }

    /** Move toward observed node bounds; every step is based on current focus and target geometry. */
    private fun focusTarget(label: String, find: () -> UiObject2?) {
        repeat(24) {
            val target = find() ?: throw AssertionError("Не найден элемент $label")
            val focused = device.findObject(By.focused(true))
            if (target.isFocused || (focused != null && sameFocus(focused, target))) return
            if (focused == null) {
                device.pressDPadLeft()
            } else {
                val from = focused.visibleBounds
                val to = target.visibleBounds
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

    private fun sameFocus(focused: UiObject2, target: UiObject2): Boolean {
        val focusBounds = focused.visibleBounds
        val targetBounds = target.visibleBounds
        return Rect.intersects(focusBounds, targetBounds) && focusBounds.contains(targetBounds.centerX(), targetBounds.centerY()) &&
            (focused.text == target.text && target.text != null || focused.contentDescription == target.contentDescription && target.contentDescription != null)
    }
}
