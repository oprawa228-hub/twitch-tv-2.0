package com.oprawa.twitch20

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.oprawa.twitch20.auth.TwitchAuth
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.playback.PlaybackResume
import com.oprawa.twitch20.ui.SearchFilter

internal data class Route(val kind: String, val value: String = "", val title: String = "", val categoryArt: String = "") {
    val key get() = "$kind:$value"
    val railSection get() = when (kind) {
        "search", "following", "recordings", "categories", "streams" -> kind
        else -> null
    }
}

internal data class Watching(
    val id: String,
    val kind: String,
    val login: String,
    val title: String,
    val subtitle: String,
    val channel: Channel? = null,
) {
    // Updated by the active playback owner and discarded when this viewing route closes.
    var resume: PlaybackResume? = null
}

/** Route data survives Activity recreation without retaining a view, player, or resolved media source. */
internal class NavigationMemory {
    val route = mutableStateOf(Route("streams", title = "Рекомендации"))
    val history = mutableStateListOf<Route>()
    val selections = mutableStateMapOf<String, String>()
    val watching = mutableStateOf<Watching?>(null)
    val railPreviewOrigin = mutableStateOf<Route?>(null)
    val hasInteracted = mutableStateOf(false)
    val settingsOpen = mutableStateOf(false)
    val accountOpen = mutableStateOf(false)
    val aboutOpen = mutableStateOf(false)
    val aboutPage = mutableStateOf("author")
    val exitOpen = mutableStateOf(false)
    val query = mutableStateOf("")
    val lastSubmittedSearch = mutableStateOf("")
    val searchFilter = mutableStateOf(SearchFilter.All)
}

internal class TwitchActivityState(application: Application) : AndroidViewModel(application) {
    val auth = TwitchAuth(application, BuildConfig.LEGACY_TV_CLIENT_ID.ifBlank { BuildConfig.TWITCH_CLIENT_ID })
    private var account: String? = null
    private var navigation = NavigationMemory()

    fun attachNavigation(loggedIn: Boolean, displayName: String): NavigationMemory {
        val nextAccount = displayName.takeIf { loggedIn }
        val previous = navigation
        if (account != null && account != nextAccount) {
            // Logout and account replacement discard private routes and focus. Keep the
            // account/settings surfaces visible so the result of the action is accessible.
            navigation = NavigationMemory().apply {
                settingsOpen.value = previous.settingsOpen.value
                accountOpen.value = previous.accountOpen.value
            }
        }
        account = nextAccount
        // A rail hover is transient. Re-enter the committed destination when its
        // temporary preview and requesting focus nodes were disposed with the Activity.
        navigation.railPreviewOrigin.value?.let {
            navigation.route.value = it
            navigation.railPreviewOrigin.value = null
        }
        return navigation
    }

    override fun onCleared() {
        auth.close()
    }
}
