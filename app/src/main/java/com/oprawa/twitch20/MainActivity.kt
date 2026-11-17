package com.oprawa.twitch20

import androidx.compose.ui.input.key.onPreviewKeyEvent

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.oprawa.twitch20.auth.AuthException
import com.oprawa.twitch20.auth.DeviceAuthorization
import com.oprawa.twitch20.chat.ChatClient
import com.oprawa.twitch20.data.*
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackPolicy
import com.oprawa.twitch20.settings.SettingsStore
import com.oprawa.twitch20.update.UpdateManager
import com.oprawa.twitch20.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { TwitchTheme { TwitchApp() } }
    }
}

private const val clipsButtonKey = "action:clips"
private const val liveButtonKey = "action:live"

@Composable
private fun TwitchApp() {
    var railOwnsFocus by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { TwitchRepository() }
    val settingsStore = remember { SettingsStore(context) }
    val settings by settingsStore.state.collectAsStateWithLifecycle()
    val legacyLive = BuildConfig.LEGACY_TV_CLIENT_ID.isNotEmpty()
    val host: TwitchActivityState = androidx.lifecycle.viewmodel.compose.viewModel()
    val auth = host.auth
    val authState by auth.state.collectAsStateWithLifecycle()
    val navigation = remember(host, authState.loggedIn, authState.displayName) {
        host.attachNavigation(authState.loggedIn, authState.displayName)
    }
    var accountProfile by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(authState.loggedIn, authState.displayName) {
        accountProfile = null
        if (authState.loggedIn) try {
            val user = auth.helixGet("users", emptyMap()).optJSONArray("data")?.optJSONObject(0)
            if (user != null) accountProfile = Channel(user.optString("id"), user.optString("login"),
                user.optString("display_name"), "", "", "", live = false,
                avatarUrl = user.optString("profile_image_url"), description = user.optString("description"))
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { }
    }
    val liveSources = remember(repository, auth, legacyLive) {
        LiveSourceResolver(session = { if (legacyLive) auth.legacyLiveSession() else null },
            guest = repository::resolveLive, authenticated = repository::resolveLegacyLive,
            onEvent = { if (BuildConfig.DEBUG) android.util.Log.i("TwitchPlayback", it) })
    }
    LaunchedEffect(authState.loggedIn, authState.displayName) { liveSources.clear() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var appForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            appForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val followingMonitor = remember(authState.loggedIn, authState.displayName) {
        FollowingMonitor { auth.followedChannels() }
    }
    val followingState by followingMonitor.state.collectAsStateWithLifecycle()
    val currentFollowing by rememberUpdatedState(followingState)
    var followingRefreshRequested by remember(followingMonitor) { mutableIntStateOf(0) }
    val updater = remember { UpdateManager(context) }
    val updateState by updater.state.collectAsStateWithLifecycle()
    val catalogState = rememberSaveableStateHolder()
    var route by navigation.route
    val history = navigation.history
    val selections = navigation.selections
    var watching by navigation.watching
    val watchedChannels = remember(authState.loggedIn, authState.displayName) {
        ChannelWatchHistory(context, if (authState.loggedIn) authState.displayName.lowercase(Locale.ROOT) else "guest")
    }
    var watchRevision by remember(watchedChannels) { mutableIntStateOf(0) }
    val rankedOfflineLogins = remember(watching, watchedChannels, watchRevision) { watchedChannels.rankedLogins() }
    var playerControlsVisible by remember { mutableStateOf(false) }
    var railChannelPreview by remember { mutableStateOf<Channel?>(null) }
    var focusedRailChannelId by remember { mutableStateOf<String?>(null) }
    var latestRailVideo by remember(railChannelPreview?.id) { mutableStateOf<Video?>(null) }
    LaunchedEffect(railChannelPreview?.id, focusedRailChannelId) {
        val focused = railChannelPreview ?: return@LaunchedEffect
        if (focusedRailChannelId != focused.id) return@LaunchedEffect
        delay(450)
        try {
            val detail = if (authState.loggedIn) HelixCatalogRepository(auth::helixGet).channel(focused.login)
                else repository.channel(focused.login)
            railChannelPreview?.takeIf { it.id == focused.id }?.let { current ->
                railChannelPreview = current.copy(description = detail.description, bannerUrl = detail.bannerUrl,
                    avatarUrl = detail.avatarUrl.ifBlank { current.avatarUrl })
            }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { }
        if (!focused.live) {
            latestRailVideo = try { repository.videos(focused.login).firstOrNull() }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        }
    }
    LaunchedEffect(followingState.channels) {
        val shown = railChannelPreview ?: return@LaunchedEffect
        followingState.channels.firstOrNull { it.id == shown.id }?.let { fresh ->
            railChannelPreview = fresh.copy(description = shown.description, bannerUrl = shown.bannerUrl)
        }
    }
    var hasInteracted by navigation.hasInteracted
    var catalogEntryGeneration by remember { mutableLongStateOf(0L) }
    var settingsOpen by navigation.settingsOpen
    var accountOpen by navigation.accountOpen
    var railPreviewOrigin by navigation.railPreviewOrigin
    var aboutOpen by navigation.aboutOpen
    var aboutPage by navigation.aboutPage
    var exitOpen by navigation.exitOpen
    // Android may place initial focus in the first rail item before data arrives.
    // Only a deliberate remote move should prevent the catalog's initial restoration.
    var userNavigated by remember(watching, settingsOpen, aboutOpen, accountOpen, exitOpen) { mutableStateOf(false) }
    var query by navigation.query
    var lastSubmittedSearch by navigation.lastSubmittedSearch
    var searchFilter by navigation.searchFilter
    var searchFiltersFocused by remember(route.kind) { mutableStateOf(false) }
    var voiceUnavailable by remember { mutableStateOf(false) }
    // Identity is part of the cache boundary: logout cannot leave a personal catalog behind.
    val catalogCache = remember(authState.loggedIn, authState.displayName) { linkedMapOf<String, CatalogPager<CatalogItem>>() }
    val searches = remember(authState.loggedIn, authState.displayName) { mutableMapOf<String, SearchCatalog>() }
    val channelInfo = remember(authState.loggedIn, authState.displayName) { mutableStateMapOf<String, Channel>() }
    val pager = remember(route.key, catalogCache) {
        val requested = route
        val signedIn = authState.loggedIn
        var helix = HelixCatalogRepository(auth::helixGet)
        var recordingChannels = emptyList<Channel>()
        val cacheable = requested.kind !in setOf("following", "rail-channel", "account")
        val existing = if (cacheable) catalogCache.remove(requested.key) else null
        val result = existing ?: CatalogPager<CatalogItem>({ it.key },
            allowEmptyPageProgress = requested.kind == "recordings") { after ->
            if (after == null) helix = HelixCatalogRepository(auth::helixGet)
            when (requested.kind) {
                "streams", "category" -> {
                    val categoryId = requested.value.takeIf { requested.kind == "category" }
                    val page = if (signedIn) helix.streamsPage(categoryId, after)
                        else CatalogPage(repository.streams(categoryId), null)
                    CatalogPage(page.items.map { CatalogItem.Stream(it) }, page.nextCursor)
                }
                "categories" -> {
                    val page = if (signedIn) helix.categoriesPage(after) else CatalogPage(repository.categories(), null)
                    CatalogPage(page.items.map { CatalogItem.Game(it) }, page.nextCursor)
                }
                "channel", "clips" -> {
                    if (requested.kind == "channel" && after == null) {
                        // Header failure must not block available recordings or the clips action.
                        channelInfo[requested.key] = try {
                            if (signedIn) helix.channel(requested.value) else repository.channel(requested.value)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { Channel(requested.value, requested.value,
                            requested.title.ifBlank { requested.value }, "", "", "", live = false) }
                    }
                    val page = if (requested.kind == "clips") {
                        if (signedIn) helix.clipsPage(requested.value, after) else CatalogPage(repository.clips(requested.value), null)
                    } else {
                        if (signedIn) helix.videosPage(requested.value, after) else CatalogPage(repository.videos(requested.value), null)
                    }
                    CatalogPage(page.items.map { CatalogItem.Recording(it) }, page.nextCursor)
                }
                "recordings" -> {
                    if (after == null) recordingChannels = currentFollowing.channels
                    val page = helix.recordingsPage(recordingChannels, after)
                    CatalogPage(page.items.map { CatalogItem.Recording(it) }, page.nextCursor)
                }
                // Following is supplied directly by the shared monitor, never a duplicate request.
                "following" -> CatalogPage(emptyList(), null)
                else -> CatalogPage(emptyList(), null)
            }
        }
        // A quick-channel preview has no catalog page. It must not evict the catalog
        // we're returning to, nor consume one LRU slot for every focused channel.
        if (cacheable) {
            catalogCache[requested.key] = result
            while (catalogCache.size > 3) {
                val oldest = catalogCache.keys.firstOrNull {
                    it != requested.key && it != railPreviewOrigin?.key && history.none { entry -> entry.key == it }
                } ?: break
                catalogCache.remove(oldest)?.cancel()
                searches.remove(oldest)
                channelInfo.remove(oldest)
                selections.remove(oldest)
                catalogState.removeState(oldest)
            }
        }
        result
    }
    val pagerPage by pager.state.collectAsStateWithLifecycle()
    val search = remember(route.key, authState.loggedIn, authState.displayName) {
        if (route.kind == "search" && route.value.isNotBlank()) {
            searches.getOrPut(route.key) { repository.catalogSearch(route.value) }
        } else null
    }
    val searchState = search?.state?.collectAsStateWithLifecycle()?.value
    val searchFailures = searchState?.failedTypes.orEmpty()
    val page = searchState?.page ?: if (route.kind == "search") {
        CatalogPageState(initialized = true, hasMore = false)
    } else pagerPage
    var retrySearch by remember(search) { mutableIntStateOf(0) }
    LaunchedEffect(search, retrySearch) {
        if (search != null && (!search.state.value.page.initialized || retrySearch > 0)) search.load()
    }
    val isFollowing = route.kind == "following"
    val channels = if (isFollowing) followingState.channels
        else page.items.filterIsInstance<CatalogItem.Stream>().map { it.channel }
    val categories = page.items.filterIsInstance<CatalogItem.Game>().map { it.category }
    val videos = page.items.filterIsInstance<CatalogItem.Recording>().map { it.video }
    val channel = channelInfo[route.key]
    val waitingForCatalog = when {
        route.kind in setOf("search", "rail-channel", "account") -> false
        route.kind in setOf("following", "recordings") && !authState.loggedIn -> false
        isFollowing || route.kind == "recordings" && !followingState.initialized ->
            followingState.channels.isEmpty() && !followingState.initialized && followingState.error == null
        else -> channel == null && page.items.isEmpty() && (page.loading || !page.initialized) && page.error == null
    }
    var pageJob by remember(pager) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun loadMore() { if (pageJob?.isActive != true) pageJob = scope.launch { pager.loadNext() } }
    var clock by remember { mutableStateOf("") }
    fun closeSettings() { settingsOpen=false; catalogState.removeState("settings-panel") }
    fun sectionRoute(kind: String) = Route(kind, title = when (kind) {
        "following" -> "Каналы"
        "recordings" -> "Записи"
        "categories" -> "Категории"
        "search" -> "Поиск"
        else -> "Рекомендации"
    })
    fun restoreRailOrigin() {
        railPreviewOrigin?.let { route = it }
        railPreviewOrigin = null
        railChannelPreview = null
        userNavigated = false
        catalogEntryGeneration++
    }
    fun refreshFollowing() { followingRefreshRequested++ }

    fun sectionFor(destination: Route): String =
        destination.railSection ?: history.lastOrNull { it.railSection != null }?.railSection ?: "streams"

    fun showCatalog(next: Route) {
        // Explicit navigation restores content focus. Only a deliberate rail hover keeps it.
        userNavigated = false
        railPreviewOrigin = null
        railChannelPreview = null
        catalogEntryGeneration++
        if (next.kind == "search") query = next.value
        route = next
    }
    fun navigate(next: Route, push: Boolean = true) {
        if (push) history.add(route) else history.clear()
        showCatalog(next)
    }
    fun submitSearch(value: String) {
        val term = value.trim().take(100)
        query = term
        lastSubmittedSearch = term
        if (route.kind == "search" && route.value != term) {
            showCatalog(if (term.isBlank()) sectionRoute("search") else Route("search", term, "Результаты поиска"))
        }
    }
    val voiceSearch = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.let(::submitSearch)
        }
    }
    fun back() {
        if (railPreviewOrigin != null) {
            restoreRailOrigin();railOwnsFocus=false
            return
        }
        if (history.isNotEmpty()) {
            showCatalog(history.removeAt(history.lastIndex))
        } else exitOpen=true
    }
    fun openChannel(ch: Channel) {
        if(ch.live) watching = Watching(ch.login,"live",ch.login,ch.title,ch.name,ch)
        else navigate(Route("channel",ch.login,ch.name))
    }
    LaunchedEffect(authState.loggedIn, authState.displayName) {
        // Delayed account validation must not redirect someone who already used the remote.
        if (authState.loggedIn && !hasInteracted && route.kind == "streams") {
            navigate(sectionRoute("following"), false)
        }
    }
    LaunchedEffect(followingMonitor, authState.loggedIn, lifecycle, followingRefreshRequested) {
        if (authState.loggedIn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                followingMonitor.refresh()
                delay(if (followingMonitor.state.value.error == null) 30_000 else 10_000)
            }
        }
    }
    LaunchedEffect(watching) { playerControlsVisible = watching != null }
    DisposableEffect(updater) { onDispose { updater.close() } }
    LaunchedEffect(settings.showClock) {
        while(settings.showClock) { clock=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()); delay(15_000) }
    }
    LaunchedEffect(pager, route.kind != "recordings" || followingState.initialized) {
        val usesPager = route.kind !in setOf("following", "rail-channel", "account", "search") &&
            (route.kind != "recordings" || authState.loggedIn && followingState.initialized)
        if (usesPager && !pager.state.value.initialized) pager.loadNext()
    }
    DisposableEffect(pager) { onDispose { pageJob?.cancel(); pager.cancel() } }
    BackHandler(enabled=watching==null && !settingsOpen && !aboutOpen && !accountOpen) { back() }
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        val active=watching
        if(!settingsOpen && !aboutOpen && !accountOpen && !exitOpen) {
        if(active!=null) {
            key(active.id,active.kind) {
                Viewing(active,repository,settingsStore,resolveLive=liveSources::play,onBack={watching=null},
                    onWatched={ elapsed -> if (authState.loggedIn) { watchedChannels.record(active.login, elapsed); watchRevision++ } },
                    onControlsVisibleChanged={playerControlsVisible=it})
            }
        } else CatalogNavigationLayout(Modifier.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) { userNavigated = true; hasInteracted = true }
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) hasInteracted = true
            false
        }, channelPreview = route.kind == "rail-channel" && railChannelPreview != null,
            waitingForContent = waitingForCatalog, rail = { needsFocus ->
            NavigationRail(selected=railChannelPreview?.let { "live:${it.id}" } ?: sectionFor(route),
                // Only explicit commit/back owns the preview origin. Removing an offline
                // quick channel may briefly report false before its neighbour gains focus.
                onFocusOwnership={ owns -> railOwnsFocus=owns },
                allowExpand=userNavigated,
                needsInitialFocus=needsFocus,
                animationsEnabled=settings.animationsEnabled,
                onChannelFocusChanged={focusedRailChannelId=it},
                onPreview={ section -> if(userNavigated) {
                    val origin = railPreviewOrigin ?: route
                    if (section == sectionFor(origin)) {
                        route = origin
                        railPreviewOrigin = null
                        railChannelPreview = null
                    } else if (route.kind != section) {
                        railPreviewOrigin = origin
                        railChannelPreview = null
                        route = sectionRoute(section)
                    }
                } },
                accountName=if(authState.loggedIn)authState.displayName else "",
                rankedOfflineLogins=rankedOfflineLogins,
                following=FollowingSidebarState(followingState.channels,authState.loggedIn,
                    followingState.loading,followingState.initialized,followingState.error),
                onChannelPreview={ ch -> if(userNavigated) {
                    if(railPreviewOrigin==null) railPreviewOrigin=route
                    railChannelPreview=ch
                    route=Route("rail-channel",ch.id,ch.name)
                } },
                onChannelSelect={ ch -> restoreRailOrigin();openChannel(ch) },
                onChannelDetails={ ch -> restoreRailOrigin();navigate(Route("channel",ch.login,ch.name)) },
                onSelect={section ->
                    hasInteracted=true
                    userNavigated=false
                    when(section) {
                        "settings" -> { restoreRailOrigin();settingsOpen=true }
                        else -> {
                            val origin = railPreviewOrigin ?: route
                            when {
                                section == sectionFor(origin) -> restoreRailOrigin()
                                section == "search" -> {
                                    restoreRailOrigin()
                                    query = lastSubmittedSearch
                                    navigate(if (query.isEmpty()) sectionRoute("search")
                                        else Route("search", query, "Результаты поиска"))
                                }
                                else -> navigate(sectionRoute(section), false)
                            }
                        }
                    }
                })
        }) {
            Column(Modifier.fillMaxSize()) {
                if(route.kind=="account") {
                    AccountScreen(auth, settings.animationsEnabled, onBack={back()}, profile=accountProfile)
                } else if(route.kind=="rail-channel" && railChannelPreview != null) {
                    SidebarChannelPreview(railChannelPreview!!, settings.cardPreviewEnabled && focusedRailChannelId==railChannelPreview?.id,
                        settings.previewSoundEnabled, settings.animationsEnabled, latestRailVideo) { liveSources.preview(it.login) }
                } else if(route.kind in setOf("following", "recordings") && !authState.loggedIn) {
                    MessageScreen(if(route.kind=="following") "Каналы" else "Записи",
                        "Войдите в Twitch, чтобы видеть свои каналы и их записи.", "Войти", autoFocus=!railOwnsFocus || !userNavigated) {
                        accountOpen=true
                    }
                } else if(route.kind=="recordings" && !followingState.initialized) {
                    if (followingState.error != null) MessageScreen("Записи", followingState.error!!, "Повторить",
                        autoFocus=!railOwnsFocus || !userNavigated) { refreshFollowing() }
                    else Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) {
                        Text("Загрузка каналов…",color=TvColors.Muted)
                    }
                } else if(isFollowing && followingState.initialized && channels.isEmpty() && followingState.error==null) {
                    MessageScreen("Каналы", "Здесь появятся каналы, которые вы отслеживаете в Twitch.", null,
                        autoFocus=!railOwnsFocus || !userNavigated) { refreshFollowing() }
                } else {
                    val searchFieldFocus = remember(route.kind) { FocusRequester() }
                    if(route.kind=="search") {
                        val noMatches = page.initialized && !page.loading && page.error == null && when (searchFilter) {
                            SearchFilter.All -> page.items.isEmpty() && searchFailures.isEmpty()
                            SearchFilter.Channels -> channels.isEmpty() && SearchCatalogType.Channels !in searchFailures
                            SearchFilter.Categories -> categories.isEmpty() && SearchCatalogType.Categories !in searchFailures
                            SearchFilter.Recordings -> videos.isEmpty() && SearchCatalogType.Recordings !in searchFailures
                        }
                        LaunchedEffect(route.key, catalogEntryGeneration, noMatches) {
                            if ((route.value.isBlank() || noMatches) && !userNavigated) {
                                withFrameNanos { }
                                searchFieldFocus.requestFocus()
                            }
                        }
                        key(route.value) {
                        SearchHeader(query, { query = it }, ::submitSearch,
                            fieldFocus = searchFieldFocus,
                            filter = searchFilter,
                            onFilterChange = { searchFilter = it },
                            onFilterFocus = { searchFiltersFocused = it },
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp,
                                end = if (settings.showClock) 112.dp else 24.dp, top = 16.dp, bottom = 8.dp),
                            onVoice = {
                                voiceUnavailable = false
                                try {
                                    voiceSearch.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                        .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Поиск в Twitch"))
                                } catch (_: android.content.ActivityNotFoundException) { voiceUnavailable = true }
                            })
                        }
                        if (voiceUnavailable) Text("Голосовой ввод недоступен на этом устройстве. Введите запрос с клавиатуры.",
                            Modifier.padding(horizontal = 24.dp), color = TvColors.Muted, fontSize = 14.sp)
                    }
                    val selected = selections[route.key]
                    val headerSelected = route.kind == "channel" && (selected == clipsButtonKey || selected == liveButtonKey)
                    channel?.let { ch ->
                        val clipsFocus = remember(route.key) { FocusRequester() }
                        val liveFocus = remember(route.key) { FocusRequester() }
                        LaunchedEffect(route.key, catalogEntryGeneration) {
                            if (headerSelected && (!railOwnsFocus || !userNavigated)) {
                                (if (selected == liveButtonKey && ch.live) liveFocus else clipsFocus).requestFocus()
                            }
                        }
                        ChannelHeader(ch)
                        Row(Modifier.padding(horizontal=24.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(ch.live) TvAction("Смотреть эфир",{openChannel(ch)}, Modifier.focusRequester(liveFocus)
                                .onFocusChanged { if (it.isFocused) selections[route.key] = liveButtonKey })
                            TvAction("Клипы",{navigate(Route("clips",ch.login,"Клипы · ${ch.name}"))}, Modifier.focusRequester(clipsFocus)
                                .onFocusChanged { if (it.isFocused) selections[route.key] = clipsButtonKey })
                        }
                    }
                    Box(Modifier.weight(1f)) { catalogState.SaveableStateProvider(route.key) {
                        key(authState.loggedIn, authState.displayName) {
                        CatalogScreen(title=if(channel != null) "Последние записи" else route.title.ifBlank {"Twitch"},channels=channels,categories=categories,videos=videos,
                            busy=if(isFollowing) followingState.loading || !followingState.initialized && followingState.error==null else page.loading || !page.initialized,
                            error=if(isFollowing) followingState.error else page.error,
                            selectedKey=selections[route.key],onChannel={openChannel(it)},
                            onCategory={navigate(Route("category",it.id,it.name,it.imageUrl))},
                            onVideo={watching=Watching(it.id,it.kind,it.channelLogin,it.title,it.channelLogin)},
                            onRetry={ if(isFollowing) refreshFollowing() else { if (!page.hasMore) pager.reset(); loadMore() } },
                            onFocused={selections[route.key]=it},
                            hasMore=!isFollowing && page.hasMore,onLoadMore={loadMore()},limitReached=!isFollowing && page.limitReached,
                            categoryArt=route.categoryArt,
                            searchQuery=route.value.takeIf { route.kind == "search" }, searchFilter=searchFilter,
                            searchFailures=searchFailures, onSearchRetry={
                                if (search?.state?.value?.page?.loading == false) retrySearch++
                            },
                            searchFallbackFocus=searchFieldFocus.takeIf { route.kind == "search" },
                            animationsEnabled=settings.animationsEnabled,
                            loadLatestVideo=if (isFollowing) ({ login -> repository.videos(login).firstOrNull() }) else null,
                            guestLimited=!authState.loggedIn && route.kind in setOf("streams","category","categories","channel","clips"),
                            revision=page.revision + catalogEntryGeneration,
                            previewEnabled=settings.cardPreviewEnabled, previewSoundEnabled=settings.previewSoundEnabled,
                            resolvePreview={ liveSources.preview(it.login) },
                            allowFocusRestore=!headerSelected && (!railOwnsFocus || !userNavigated) && (route.kind!="search" || route.value.isNotBlank() && !searchFiltersFocused),
                            onChannelDetails={navigate(Route("channel",it.login,it.name))})
                        }
                    } }
                }
            }
        }
        }
        FollowNotifications(followingMonitor, enabled=settings.followNotificationsEnabled && authState.loggedIn,
            eligible=watching?.kind in setOf("live", "vod"), soundEnabled=settings.notificationSoundEnabled,
            animationsEnabled=settings.animationsEnabled, loadLiveMessage={ repository.liveMessage(it) },
            blocked=!appForeground || settingsOpen || aboutOpen || accountOpen || exitOpen || watching!=null && playerControlsVisible,
            currentLogin=watching?.login ?: railChannelPreview?.login.takeIf { route.kind=="rail-channel" },
            modifier=Modifier.align(Alignment.TopCenter).padding(top=20.dp))
        if(settings.showClock && watching==null && !settingsOpen && !aboutOpen && !accountOpen && !exitOpen)
            TvClock(settings, Modifier.align(Alignment.TopEnd).padding(end=28.dp,top=18.dp))
        if(settingsOpen && !aboutOpen && !accountOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            catalogState.SaveableStateProvider("settings-panel") {
                SettingsScreen(settings,onSave={settingsStore.save(it)},onBack={closeSettings()},
                    onCheckUpdates={aboutPage="updates";aboutOpen=true},
                    onAccount={accountOpen=true},onAbout={aboutPage="author";aboutOpen=true},
                    updateState=updateState, onAboutFocused={
                        scope.launch {
                            updater.checkIfStale(context.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE)
                                .getBoolean("testChannel", true))
                        }
                    },
                    accountProfile=accountProfile, accountDisplayName=authState.displayName.takeIf { authState.loggedIn }, onNotificationExampleSound={scope.launch { playLiveNotificationSound() }})
            }
        }
        if(accountOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            BackHandler { accountOpen=false }
            AccountScreen(auth, settings.animationsEnabled, onBack={accountOpen=false}, profile=accountProfile)
        }
        if(aboutOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            BackHandler { aboutOpen=false }
            if (aboutPage=="updates") UpdatesScreen(updater, onBack={aboutOpen=false})
            else AuthorScreen(onBack={aboutOpen=false})
        }
        if(exitOpen) Box(Modifier.fillMaxSize().background(TvColors.Background.copy(alpha=.96f)),contentAlignment=Alignment.Center) {
            val stayFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; stayFocus.requestFocus() }
            BackHandler { exitOpen=false }
            Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("Выйти из Twitch 2.0?",color=TvColors.Text,fontSize=26.sp)
                TvAction("Остаться",{exitOpen=false}, Modifier.focusRequester(stayFocus))
                TvAction("Выйти",{(context as? android.app.Activity)?.finish()})
            }
        }
    }
}

@Composable private fun Viewing(target:Watching,repository:TwitchRepository,store:SettingsStore,resolveLive:suspend (String,Boolean)->String,onBack:()->Unit,
    onControlsVisibleChanged:(Boolean)->Unit, onWatched:(Long)->Unit = {}) {
    val context=LocalContext.current
    val session=remember { PlaybackSession(context) }
    val resume = remember(target) { target.resume }
    var metadata by remember(target.id) { mutableStateOf(target.channel) }
    LaunchedEffect(target.login) {
        if(target.login.isNotBlank()) while(true) {
            try { metadata=repository.channel(target.login) }
            catch(cancel:CancellationException) { throw cancel } catch(_:Exception) { }
            delay(60_000)
        }
    }
    val chat=remember { ChatClient() }
    val settings by store.state.collectAsStateWithLifecycle()
    val messages by chat.messages.collectAsStateWithLifecycle()
    val chatStatus by chat.status.collectAsStateWithLifecycle()
    var chatPreviewEnabled by remember { mutableStateOf<Boolean?>(null) }
    val chatNeeded = chatPreviewEnabled ?: settings.chatEnabled
    val currentChatEnabled by rememberUpdatedState(chatNeeded)
    val lifecycleOwner=LocalLifecycleOwner.current
    var resolving by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val watchCallback by rememberUpdatedState(onWatched)
    LaunchedEffect(session) {
        var pending = 0L
        var last = SystemClock.elapsedRealtime()
        var wasPlaying = false
        try {
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val playing = session.player.isPlaying && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (playing && wasPlaying) pending += (now - last).coerceIn(0L, 2000L)
                last = now; wasPlaying = playing
                if (pending >= 15_000) { watchCallback(pending); pending = 0 }
            }
        } finally { if (pending > 0) watchCallback(pending) }
    }
    val positions=remember { PlaybackHistory(context) }
    val storyboards=remember { VodStoryboardRepository() }
    fun savePosition() {
        session.resumePoint()?.let { target.resume = it }
        if(target.kind!="live" && session.player.mediaItemCount>0)
            positions.save(target.kind,target.id,PlaybackPolicy.positionToSave(
                session.player.currentPosition, session.player.duration,
                session.player.playbackState == androidx.media3.common.Player.STATE_ENDED))
    }
    DisposableEffect(session,lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_STOP -> { savePosition();session.onBackground();chat.close() }
                Lifecycle.Event.ON_START -> { session.onForeground();if(target.kind=="live" && currentChatEnabled) chat.connect(target.login) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(session) { onDispose {
        savePosition()
        chat.release();session.release()
    } }
    LaunchedEffect(session) {
        while(true) { delay(15_000);savePosition() }
    }
    LaunchedEffect(target.login, chatNeeded) {
        if(target.kind=="live" && chatNeeded && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) chat.connect(target.login) else chat.close()
    }
    LaunchedEffect(target,retry) {
        val requestStartedAtMs = SystemClock.elapsedRealtime()
        resolving=true;error=null
        try {
            val variants = if(target.kind=="clip") repository.resolveClipVariants(target.id) else emptyList()
            val url = when (target.kind) {
                "live" -> resolveLive(target.id, retry == 0)
                "clip" -> variants.first().url
                else -> repository.resolveVideo(target.id)
            }
            val restart = resume.takeIf { retry == 0 }
            session.play(url,"${target.kind}:${target.id}",target.kind=="live",restart?.positionMs ?: positions.read(target.kind,target.id),
                requestStartedAtMs=requestStartedAtMs, progressiveVariants=variants, initialPlayWhenReady=resume?.playWhenReady ?: true)
            if (target.kind == "vod") session.setPlaybackSpeed(positions.readSpeed(target.id))
            if(!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))session.onBackground()
        } catch(cancel:CancellationException){throw cancel}
        catch(e:Exception){error=if(e is TwitchException)e.message else "Не удалось подключиться. Проверьте сеть и повторите."}
        finally{resolving=false}
    }
    val replay = if (target.kind == "vod" && chatNeeded && !resolving && error == null)
        rememberVodChat(target.id, session, repository) else ReplayChat(emptyList(), "replay_loading")
    BackHandler(enabled=resolving||error!=null){onBack()}
    if(error!=null) MessageScreen("Видео недоступно",error!!,"Повторить"){retry++}
    else if(resolving) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Подключение к Twitch…",color=TvColors.Text)}
    else PlayerScreen(session,if(target.kind=="live") metadata?.title?.takeIf { it.isNotBlank() } ?: target.title else target.title,target.subtitle,onBack,
        chatContent=when (target.kind) {
            "live" -> { previewSettings -> ChatPanel(messages, previewSettings, status=chatStatus) }
            "vod" -> { previewSettings -> ChatPanel(replay.messages, previewSettings, status=replay.status) }
            else -> null
        },
        settings=settings,onSettingsChange={store.save(it)},
        isVod=target.kind=="vod", onSpeedChange={positions.saveSpeed(target.id,it)},
        loadStoryboard=if(target.kind=="vod") ({storyboards.load(target.id)}) else null,
        channelName=metadata?.name ?: target.subtitle, avatarUrl=metadata?.avatarUrl.orEmpty(),
        category=if(target.kind=="live") metadata?.category.orEmpty() else "",
        viewers=if(target.kind=="live") metadata?.viewers else null,
        onChatPreview={chatPreviewEnabled=it},onRetry={savePosition();retry++},
        onControlsVisibleChanged=onControlsVisibleChanged)
}

@Composable private fun MessageScreen(title:String,message:String,action:String?,autoFocus:Boolean=true,onAction:()->Unit) {
    val first=remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if(autoFocus) first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(40.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text(title,fontSize=28.sp,color=TvColors.Text);Text(message, modifier=if(action == null) Modifier.focusRequester(first).focusable() else Modifier,
            color=TvColors.Muted,fontSize=18.sp)
        if(action != null) TvAction(action,onAction,modifier=Modifier.focusRequester(first))
    }
}
