package com.oprawa.twitch20.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

enum class SettingsEditorPage { OVERVIEW, OVERLAY, THEATER, INTERFACE }

/** Navigation only: the enclosing settings session owns its single unsaved draft. */
@Stable
class SettingsEditorState(initialPage: SettingsEditorPage = SettingsEditorPage.OVERVIEW) {
    var page by mutableStateOf(initialPage)
        private set
    var overviewFocus by mutableStateOf<String?>(null)
        private set

    fun open(destination: SettingsEditorPage) {
        page = destination
    }
    fun back(): Boolean {
        if (page == SettingsEditorPage.OVERVIEW) return false
        overviewFocus = page.name
        page = SettingsEditorPage.OVERVIEW
        return true
    }
    fun reset() {
        page = SettingsEditorPage.OVERVIEW
        overviewFocus = null
    }
    fun rememberOverviewFocus(key: String) { overviewFocus = key }
}

@Composable
fun rememberSettingsEditorState(): SettingsEditorState = rememberSaveable(saver = listSaver<SettingsEditorState, String>(
    save = { listOf(it.page.name, it.overviewFocus.orEmpty()) },
    restore = { values -> SettingsEditorState(SettingsEditorPage.valueOf(values[0])).apply {
        values[1].takeIf(String::isNotBlank)?.let(::rememberOverviewFocus)
    } },
)) { SettingsEditorState() }
