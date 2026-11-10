package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatStyle
import com.oprawa.twitch20.settings.OverlaySize
import org.junit.Assert.*
import org.junit.Test

class SettingsChoicesTest {
    @Test fun overlayChangesKeepCurrentModeAndTheaterProfile() {
        for (mode in ChatMode.entries) for (choice in SettingsChoice.entries) {
            val original = AppSettings(chatMode = mode, overlayStyle = ChatStyle(16, .35f),
                theaterStyle = ChatStyle(24, .1f), showClock = false, cardPreviewEnabled = false)
            val selection = SettingsSelection(original, ChatMode.OVERLAY, choice)
            for (delta in listOf(-1, 1)) {
                val changed = selection.step(delta)
                assertEquals(mode, changed.chatMode)
                assertEquals(original.theaterStyle, changed.theaterStyle)
                assertFalse(changed.showClock)
                assertFalse(changed.cardPreviewEnabled)
            }
        }
    }

    @Test fun tallOverlayWrapsToSmallWithoutChangingFontOrEnablingChat() {
        val original = AppSettings(chatMode = ChatMode.OFF, overlaySize = OverlaySize.LARGE,
            overlayStyle = ChatStyle(16, .35f), theaterStyle = ChatStyle(24, .1f))
        val tall = SettingsSelection(original, ChatMode.OVERLAY, SettingsChoice.OVERLAY_SIZE).step(1)
        assertEquals(original.copy(overlaySize = OverlaySize.TALL), tall)
        assertEquals(.20f, tall.overlaySize.widthFraction, 0f)
        assertEquals(1f, tall.overlaySize.heightFraction, 0f)
        val selection = SettingsSelection(tall, ChatMode.OVERLAY, SettingsChoice.OVERLAY_SIZE)
        assertEquals("Высокий", selection.options[selection.selectedIndex].label)
        assertEquals(OverlaySize.SMALL, selection.step(1).overlaySize)
        assertEquals(original, selection.step(-1))
    }

    @Test fun directionalChangesPreserveLegacyTransparencyAsCurrentUntilFirstPress() {
        for (alpha in listOf(0f, .1f, .35f, .7f, 1f)) {
            val original = AppSettings(overlayStyle = ChatStyle(18, alpha))
            val selection = SettingsSelection(original, ChatMode.OVERLAY, SettingsChoice.OPACITY)
            assertEquals(original, selection.options[selection.selectedIndex].settings)
            assertEquals(opacityLabel(alpha), selection.options[selection.selectedIndex].label)
            assertEquals(selection.options.size, selection.options.map { it.label }.distinct().size)
            assertNotEquals(alpha, selection.step(1).overlayStyle.opacity)
        }
    }

    @Test fun changingTheaterFontLeavesOverlayGeometryAndAppearanceAlone() {
        val original = AppSettings(chatMode = ChatMode.OVERLAY, overlaySize = OverlaySize.SMALL,
            overlayStyle = ChatStyle(16, .35f), theaterStyle = ChatStyle(24, .1f))
        val selection = SettingsSelection(original, ChatMode.THEATER, SettingsChoice.TEXT_SIZE)
        assertEquals(original.copy(theaterStyle = ChatStyle(22, .1f)), selection.step(-1))
    }
}
