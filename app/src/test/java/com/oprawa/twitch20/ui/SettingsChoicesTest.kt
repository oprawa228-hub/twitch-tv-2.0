package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatStyle
import com.oprawa.twitch20.settings.OverlaySize
import org.junit.Assert.*
import org.junit.Test

class SettingsChoicesTest {
    @Test fun everyOverlayChoicePreviewsWithoutActivatingOrModifyingTheater() {
        for (mode in ChatMode.entries) for (choice in SettingsChoice.entries) {
            val original = AppSettings(chatMode = mode, overlayStyle = ChatStyle(16, .35f),
                theaterStyle = ChatStyle(24, .1f), showClock = false, cardPreviewEnabled = false)
            val picker = SettingsSelection(original, ChatMode.OVERLAY, choice)
            assertEquals(original, picker.preview())
            picker.options.indices.forEach { index ->
                val focused = picker.focus(index)
                assertEquals(original, focused.cancel())
                assertEquals(mode, focused.preview().chatMode)
                assertEquals(original.theaterStyle, focused.preview().theaterStyle)
                assertEquals(original.showClock, focused.preview().showClock)
                assertEquals(original.cardPreviewEnabled, focused.preview().cardPreviewEnabled)
                assertEquals(picker.selectedIndex, focused.selectedIndex)
            }
        }
    }

    @Test fun confirmingBoxSizeChangesGeometryButKeepsBothFontSizes() {
        val original = AppSettings(chatMode = ChatMode.OFF,
            overlayStyle = ChatStyle(16, .35f), theaterStyle = ChatStyle(24, .1f))
        val picker = SettingsSelection(original, ChatMode.OVERLAY, SettingsChoice.OVERLAY_SIZE)
        val result = picker.focus(OverlaySize.LARGE.ordinal).confirm()
        assertEquals(original.copy(overlaySize = OverlaySize.LARGE), result)
        assertEquals(OverlaySize.LARGE.ordinal,
            SettingsSelection(result, ChatMode.OVERLAY, SettingsChoice.OVERLAY_SIZE).selectedIndex)
        assertTrue(OverlaySize.SMALL.widthFraction < OverlaySize.MEDIUM.widthFraction)
        assertTrue(OverlaySize.MEDIUM.heightFraction < OverlaySize.LARGE.heightFraction)
    }

    @Test fun legacyTransparencyHasNamedCurrentOptionAndIsUnchangedUntilConfirmed() {
        for (alpha in listOf(0f, .1f, .35f, .7f, 1f)) {
            val original = AppSettings(overlayStyle = ChatStyle(18, alpha))
            val picker = SettingsSelection(original, ChatMode.OVERLAY, SettingsChoice.OPACITY)
            assertEquals(original, picker.preview())
            assertEquals(opacityLabel(alpha), picker.options[picker.selectedIndex].label)
            assertEquals(picker.options.size, picker.options.map { it.label }.distinct().size)
            assertTrue(picker.options.none { it.label.startsWith("Свой") })
            assertTrue(picker.options.none { option -> option.label.any(Char::isDigit) })
            val light = picker.options.indexOfFirst { it.label == "Лёгкий" }
            assertEquals(original, picker.focus(light).cancel())
            assertEquals(original.copy(overlayStyle = ChatStyle(18, .25f)), picker.focus(light).confirm())
        }
    }

    @Test fun theaterFontConfirmationLeavesOverlayGeometryAndAppearanceAlone() {
        val original = AppSettings(chatMode = ChatMode.OVERLAY, overlaySize = OverlaySize.SMALL,
            overlayStyle = ChatStyle(16, .35f), theaterStyle = ChatStyle(24, .1f))
        val picker = SettingsSelection(original, ChatMode.THEATER, SettingsChoice.TEXT_SIZE)
        val medium = picker.options.indexOfFirst { it.label == "Средний" }
        assertEquals(original.copy(theaterStyle = ChatStyle(18, .1f)), picker.focus(medium).confirm())
    }
}
