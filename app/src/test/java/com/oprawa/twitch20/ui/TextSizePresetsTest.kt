package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatStyle
import com.oprawa.twitch20.settings.SettingsCodec
import org.junit.Assert.*
import org.junit.Test

class TextSizePresetsTest {
    @Test fun legacyValuesSurviveOpeningCancelAndUnrelatedSaveExactly() {
        for (size in 12..24) {
            val settings = AppSettings(overlayStyle = ChatStyle(size, .35f))
            val picker = TextSizeSelection(settings, ChatMode.OVERLAY)
            assertEquals(size, picker.focusedSizeSp)
            assertTrue(size in picker.options)
            assertEquals(settings, picker.preview())
            assertEquals(settings, picker.focus(22).cancel())
            val unrelatedChange = picker.cancel().copy(showClock = false)
            assertEquals(size, SettingsCodec.decode(SettingsCodec.encode(unrelatedChange)).overlayStyle.textSizeSp)
        }
    }

    @Test fun namesAreStableAndCustomValuesStayDistinguishable() {
        assertEquals("Маленький", textSizeLabel(14))
        assertEquals("Средний", textSizeLabel(18))
        assertEquals("Большой", textSizeLabel(22))
        for (size in listOf(12, 16, 20, 24)) assertEquals("Свой размер", textSizeLabel(size))
    }

    @Test fun focusingPreviewDoesNotChangeDraftOrEnableInactiveProfile() {
        for (active in ChatMode.entries) for (edited in listOf(ChatMode.OVERLAY, ChatMode.THEATER)) {
            val settings = AppSettings(chatMode = active, overlayStyle = ChatStyle(16, .35f),
                theaterStyle = ChatStyle(20, .85f), showClock = false, animationsEnabled = false)
            val picker = TextSizeSelection(settings, edited).focus(22)
            assertEquals(settings, picker.original)
            val expected = settings.withStyle(edited, settings.styleFor(edited).copy(textSizeSp = 22))
            assertEquals(expected, picker.preview())
            assertEquals(active, picker.preview().chatMode)
            assertEquals(settings.styleFor(edited).textSizeSp, picker.selectedSizeSp)
            assertEquals(settings, picker.cancel())
        }
    }

    @Test fun confirmingChangesOnlyTargetProfileAndReopeningSelectsThatValue() {
        val original = AppSettings(chatMode = ChatMode.OFF,
            overlayStyle = ChatStyle(16, .25f), theaterStyle = ChatStyle(24, .75f))
        val confirmed = TextSizeSelection(original, ChatMode.THEATER).focus(18).confirm()
        assertEquals(original.copy(theaterStyle = ChatStyle(18, .75f)), confirmed)
        val reopened = TextSizeSelection(confirmed, ChatMode.THEATER)
        assertEquals(18, reopened.selectedSizeSp)
        assertEquals(18, reopened.focusedSizeSp)
        assertEquals(listOf(14, 18, 22), reopened.options)
    }
}
