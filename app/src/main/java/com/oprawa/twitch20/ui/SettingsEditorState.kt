package com.oprawa.twitch20.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

enum class SettingsEditorPage { OVERVIEW, OVERLAY, THEATER, INTERFACE, CLOCK, NOTIFICATIONS }
internal enum class SettingsPreviewTarget { CATALOG, ANIMATIONS, OVERLAY, THEATER, CLOCK, NOTIFICATIONS, ACCOUNT, ABOUT }

/** Navigation only. Confirmed choices are persisted immediately by the enclosing screen. */
@Stable
class SettingsEditorState(initialPage: SettingsEditorPage = SettingsEditorPage.OVERVIEW) {
    var page by mutableStateOf(initialPage)
        private set
    var overviewFocus by mutableStateOf<String?>(null)
        private set
    var focusedSetting by mutableStateOf<String?>(null)
        private set

    fun open(destination: SettingsEditorPage) {
        focusedSetting = null
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
        focusedSetting = null
    }
    fun rememberOverviewFocus(key: String) { overviewFocus = key }
    fun focusSetting(key: String) { focusedSetting = key }
}

/** The example follows the setting being inspected, not the currently playing chat mode. */
internal fun SettingsEditorState.previewTarget(): SettingsPreviewTarget = when (page) {
    SettingsEditorPage.OVERLAY -> SettingsPreviewTarget.OVERLAY
    SettingsEditorPage.THEATER -> SettingsPreviewTarget.THEATER
    SettingsEditorPage.CLOCK -> SettingsPreviewTarget.CLOCK
    SettingsEditorPage.NOTIFICATIONS -> SettingsPreviewTarget.NOTIFICATIONS
    SettingsEditorPage.INTERFACE -> if (focusedSetting == "ANIMATIONS") SettingsPreviewTarget.ANIMATIONS else SettingsPreviewTarget.CATALOG
    SettingsEditorPage.OVERVIEW -> when (overviewFocus) {
        "OVERLAY" -> SettingsPreviewTarget.OVERLAY
        "THEATER" -> SettingsPreviewTarget.THEATER
        "CLOCK" -> SettingsPreviewTarget.CLOCK
        "NOTIFICATIONS" -> SettingsPreviewTarget.NOTIFICATIONS
        "ACCOUNT" -> SettingsPreviewTarget.ACCOUNT
        "ABOUT", "UPDATES" -> SettingsPreviewTarget.ABOUT
        else -> SettingsPreviewTarget.CATALOG
    }
}

@Composable
fun rememberSettingsEditorState(): SettingsEditorState = rememberSaveable(saver = listSaver<SettingsEditorState, String>(
    save = { listOf(it.page.name, it.overviewFocus.orEmpty()) },
    restore = { values -> SettingsEditorState(SettingsEditorPage.valueOf(values[0])).apply {
        values[1].takeIf(String::isNotBlank)?.let(::rememberOverviewFocus)
    } },
)) { SettingsEditorState() }
