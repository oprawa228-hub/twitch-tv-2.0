package com.oprawa.twitch20.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsEditorStateTest {
    @Test fun backFromProfileReturnsToOverviewAndRestoresItsLauncher() {
        val state = SettingsEditorState()
        state.open(SettingsEditorPage.THEATER)
        assertEquals(SettingsEditorPage.THEATER, state.page)
        assertTrue(state.back())
        assertEquals(SettingsEditorPage.OVERVIEW, state.page)
        assertEquals("THEATER", state.overviewFocus)
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
        assertEquals("CLOCK", state.overviewFocus)
        assertEquals(SettingsEditorPage.OVERVIEW, state.page)
    }
}
