package com.oprawa.twitch20.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsEditorStateTest {
    @Test fun backFromProfileRestoresItsLauncherInsideViewing() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.THEATER)
        assertEquals(SettingsEditorPage.THEATER, state.page)
        assertTrue(state.back())
        assertEquals(SettingsEditorPage.VIEWING, state.page)
        assertEquals("THEATER", state.focusedSetting)
        assertEquals(SettingsPreviewTarget.THEATER, state.previewTarget())
        assertTrue(state.back())
        assertEquals(SettingsEditorPage.OVERVIEW, state.page)
        assertEquals("VIEWING", state.overviewFocus)
        assertFalse("Root Back leaves settings without reverting confirmed values", state.back())
    }

    @Test fun switchingPagesDoesNotCreateAnExtraBackStackLevel() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.OVERLAY)
        state.open(SettingsEditorPage.INTERFACE)
        assertTrue(state.back())
        assertEquals(SettingsEditorPage.OVERVIEW, state.page)
        assertEquals("INTERFACE", state.overviewFocus)
        assertFalse(state.back())
    }

    @Test fun reopeningEditorStartsAtRootInsteadOfDiscardedSubpage() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.OVERLAY)
        state.rememberOverviewFocus("UPDATES")
        state.reset()
        assertEquals(SettingsEditorPage.OVERVIEW, state.page)
        assertNull(state.overviewFocus)
        assertFalse(state.back())
    }

    @Test fun clockHasAnIndependentPageAndReturnsToItsLauncher() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.CLOCK)
        assertEquals(SettingsEditorPage.CLOCK, state.page)
        assertTrue(state.back())
        assertEquals("CLOCK", state.focusedSetting)
        assertEquals(SettingsEditorPage.INTERFACE, state.page)
        assertTrue(state.back())
        assertEquals("INTERFACE", state.overviewFocus)
    }

    @Test fun focusedSettingSelectsTheCorrespondingExampleWithoutSwitchingPages() {
        val state = SettingsEditorState()
        state.rememberOverviewFocus("OVERLAY")
        assertEquals(SettingsPreviewTarget.OVERLAY, state.previewTarget())
        state.rememberOverviewFocus("ACCOUNT")
        assertEquals(SettingsPreviewTarget.ACCOUNT, state.previewTarget())
        state.open(SettingsEditorPage.INTERFACE)
        assertEquals(SettingsPreviewTarget.CATALOG, state.previewTarget())
        state.focusSetting("ANIMATIONS")
        assertEquals(SettingsPreviewTarget.ANIMATIONS, state.previewTarget())
        assertEquals(SettingsEditorPage.INTERFACE, state.page)
        state.focusSetting("CARD_PREVIEW")
        assertEquals(SettingsPreviewTarget.CATALOG, state.previewTarget())
        state.open(SettingsEditorPage.CLOCK)
        assertEquals(SettingsPreviewTarget.CLOCK, state.previewTarget())
    }

    @Test fun notificationPageReturnsToItsOwnOverviewRow() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.NOTIFICATIONS)
        assertEquals(SettingsPreviewTarget.NOTIFICATIONS, state.previewTarget())
        assertTrue(state.back())
        assertEquals("NOTIFICATIONS", state.overviewFocus)
        assertEquals(SettingsPreviewTarget.NOTIFICATIONS, state.previewTarget())
    }
}
