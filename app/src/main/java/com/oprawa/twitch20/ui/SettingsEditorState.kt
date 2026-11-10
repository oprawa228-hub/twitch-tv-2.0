package com.oprawa.twitch20.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

enum class SettingsEditorPage { OVERVIEW, OVERLAY, THEATER, INTERFACE, CLOCK, NOTIFICATIONS, VIEWING, ABOUT }
internal enum class SettingsPreviewTarget { CATALOG, ANIMATIONS, VIEWING, OVERLAY, THEATER, CLOCK, NOTIFICATIONS, ACCOUNT, ABOUT }

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
        val previous = page
        page = when (previous) {
            SettingsEditorPage.OVERLAY, SettingsEditorPage.THEATER -> SettingsEditorPage.VIEWING
            SettingsEditorPage.CLOCK -> SettingsEditorPage.INTERFACE
            else -> SettingsEditorPage.OVERVIEW
        }
        focusedSetting = previous.name
        if (page == SettingsEditorPage.OVERVIEW) overviewFocus = previous.name
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
    SettingsEditorPage.ABOUT -> SettingsPreviewTarget.ABOUT
    SettingsEditorPage.VIEWING -> if (focusedSetting == "THEATER") SettingsPreviewTarget.THEATER else SettingsPreviewTarget.OVERLAY
    SettingsEditorPage.INTERFACE -> when (focusedSetting) {
        "ANIMATIONS" -> SettingsPreviewTarget.ANIMATIONS
        "CLOCK" -> SettingsPreviewTarget.CLOCK
        else -> SettingsPreviewTarget.CATALOG
    }
    SettingsEditorPage.OVERVIEW -> when (overviewFocus) {
        "VIEWING" -> SettingsPreviewTarget.VIEWING
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
    save = { listOf(it.page.name, it.overviewFocus.orEmpty(), it.focusedSetting.orEmpty()) },
    restore = { values -> SettingsEditorState(SettingsEditorPage.valueOf(values[0])).apply {
        values.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::rememberOverviewFocus)
        values.getOrNull(2)?.takeIf(String::isNotBlank)?.let(::focusSetting)
    } },
)) { SettingsEditorState() }
