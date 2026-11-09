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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import com.oprawa.twitch20.auth.TwitchAuth
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

private data class Route(val kind: String, val value: String = "", val title: String = "") {
    val key get() = "$kind:$value"
}
private data class Watching(val id: String, val kind: String, val login: String, val title: String, val subtitle: String, val channel: Channel? = null)

@Composable
private fun TwitchApp() {
    var railOwnsFocus by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { TwitchRepository() }
    val settingsStore = remember { SettingsStore(context) }
    val settings by settingsStore.state.collectAsStateWithLifecycle()
    val legacyLive = BuildConfig.LEGACY_TV_CLIENT_ID.isNotEmpty()
    val auth = remember {
        TwitchAuth(context, if (legacyLive) BuildConfig.LEGACY_TV_CLIENT_ID else BuildConfig.TWITCH_CLIENT_ID)
    }
    val authState by auth.state.collectAsStateWithLifecycle()
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
    val catalogState = rememberSaveableStateHolder()
    var route by remember { mutableStateOf(Route("streams", title = "Рекомендации")) }
    val history = remember { mutableStateListOf<Route>() }
    val selections = remember { mutableStateMapOf<String,String>() }
    var watching by remember { mutableStateOf<Watching?>(null) }
    var playerControlsVisible by remember { mutableStateOf(false) }
    var railChannelPreview by remember { mutableStateOf<Channel?>(null) }
    var hasInteracted by remember { mutableStateOf(false) }
    var catalogEntryGeneration by remember { mutableLongStateOf(0L) }
    var settingsOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var railPreviewOrigin by remember { mutableStateOf<Route?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    var exitOpen by remember { mutableStateOf(false) }
    // Android may place initial focus in the first rail item before data arrives.
    // Only a deliberate remote move should prevent the catalog's initial restoration.
    var userNavigated by remember(watching, settingsOpen, aboutOpen, accountOpen, exitOpen) { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Identity is part of the cache boundary: logout cannot leave a personal catalog behind.
    val catalogCache = remember(authState.loggedIn, authState.displayName) { linkedMapOf<String, CatalogPager<CatalogItem>>() }
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
                "search" -> CatalogPage(repository.search(requested.value).map { CatalogItem.Stream(it) }, null)
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
                    it != requested.key && it != railPreviewOrigin?.key && it != history.lastOrNull()?.key
                } ?: break
                catalogCache.remove(oldest)?.cancel()
                channelInfo.remove(oldest)
                selections.remove(oldest)
                catalogState.removeState(oldest)
            }
        }
        result
    }
    val page by pager.state.collectAsStateWithLifecycle()
    val isFollowing = route.kind == "following"
    val channels = if (isFollowing) followingState.channels
        else page.items.filterIsInstance<CatalogItem.Stream>().map { it.channel }
    val categories = page.items.filterIsInstance<CatalogItem.Game>().map { it.category }
    val videos = page.items.filterIsInstance<CatalogItem.Recording>().map { it.video }
    val channel = channelInfo[route.key]
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

    fun navigate(next: Route, push: Boolean = true) {
        // Explicit navigation restores content focus. Only a deliberate rail hover keeps it.
        userNavigated = false
        railPreviewOrigin = null
        railChannelPreview = null
        catalogEntryGeneration++
        if (push) history.add(route) else history.clear()
        route = next
    }
    fun back() {
        if (railPreviewOrigin != null && railOwnsFocus) {
            restoreRailOrigin();railOwnsFocus=false
            return
        }
        if (history.isNotEmpty()) {
            userNavigated=false
            catalogEntryGeneration++
            route = history.removeAt(history.lastIndex)
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
    DisposableEffect(auth) { onDispose { auth.close() } }
    DisposableEffect(updater) { onDispose { updater.close() } }
    LaunchedEffect(settings.showClock) {
        while(settings.showClock) { clock=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()); delay(15_000) }
    }
    LaunchedEffect(pager, route.kind != "recordings" || followingState.initialized) {
        val usesPager = route.kind !in setOf("following", "rail-channel", "account") &&
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
                    onControlsVisibleChanged={playerControlsVisible=it})
            }
        } else Row(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) { userNavigated = true; hasInteracted = true }
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) hasInteracted = true
            false
        }) {
            NavigationRail(selected=when (route.kind) {
                    "category" -> "categories"
                    "rail-channel" -> "live:${railChannelPreview?.id}"
                    else -> route.kind
                },
                // Only explicit commit/back owns the preview origin. Removing an offline
                // quick channel may briefly report false before its neighbour gains focus.
                onFocusOwnership={ owns -> railOwnsFocus=owns },
                onPreview={ section -> if(userNavigated && route.kind != section) {
                    if(railPreviewOrigin==null) railPreviewOrigin=route
                    railChannelPreview=null
                    route=sectionRoute(section)
                } },
                accountName=if(authState.loggedIn)authState.displayName else "",
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
                        "search" -> { restoreRailOrigin();navigate(sectionRoute("search")) }
                        else -> navigate(sectionRoute(section),false)
                    }
                })
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if(route.kind=="account") {
                    AccountScreen(auth)
                } else if(route.kind=="rail-channel" && railChannelPreview != null) {
                    SidebarChannelPreview(railChannelPreview!!, settings.cardPreviewEnabled) { liveSources.preview(it.login) }
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
                    MessageScreen("Каналы", "Здесь появятся каналы, которые вы отслеживаете в Twitch.", "Обновить",
                        autoFocus=!railOwnsFocus || !userNavigated) { refreshFollowing() }
                } else {
                    if(route.kind=="search") {
                        val searchFieldFocus = remember { FocusRequester() }
                        LaunchedEffect(route.key, catalogEntryGeneration) {
                            if (route.value.isBlank() && !userNavigated) {
                                withFrameNanos { }
                                searchFieldFocus.requestFocus()
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(24.dp,8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedTextField(value=query,onValueChange={query=it.take(100)},singleLine=true,
                                label={androidx.compose.material3.Text("Название канала")},
                                modifier=Modifier.weight(1f).focusRequester(searchFieldFocus),
                                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={navigate(Route("search",query,"Результаты поиска"),false)}))
                            TvAction("Найти",{navigate(Route("search",query,"Результаты поиска"),false)})
                        }
                    }
                    channel?.let { ch ->
                        Row(Modifier.padding(horizontal=24.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(ch.live) TvAction("Смотреть эфир",{openChannel(ch)})
                            TvAction("Клипы",{navigate(Route("clips",ch.login,"Клипы · ${ch.name}"))})
                            TvAction("Назад",{back()})
                        }
                    }
                    Box(Modifier.weight(1f)) { catalogState.SaveableStateProvider(route.key) { key(pager) {
                        CatalogScreen(title=route.title.ifBlank {"Twitch"},channels=channels,categories=categories,videos=videos,
                            busy=if(isFollowing) followingState.loading || !followingState.initialized && followingState.error==null else page.loading || !page.initialized,
                            error=if(isFollowing) followingState.error else page.error,
                            selectedKey=selections[route.key],onChannel={openChannel(it)},
                            onCategory={navigate(Route("category",it.id,it.name))},
                            onVideo={watching=Watching(it.id,it.kind,it.channelLogin,it.title,it.channelLogin)},
                            onRetry={ if(isFollowing) refreshFollowing() else { if (!page.hasMore) pager.reset(); loadMore() } },
                            onFocused={selections[route.key]=it},
                            hasMore=!isFollowing && page.hasMore,onLoadMore={loadMore()},limitReached=!isFollowing && page.limitReached,
                            onRefresh={if(isFollowing) refreshFollowing() else {pageJob?.cancel();pager.reset();loadMore()}},
                            guestLimited=!authState.loggedIn && route.kind in setOf("streams","category","categories","channel","clips"),
                            revision=page.revision + catalogEntryGeneration,
                            previewEnabled=settings.cardPreviewEnabled, resolvePreview={ liveSources.preview(it.login) },
                            allowFocusRestore=(!railOwnsFocus || !userNavigated) && (route.kind!="search" || route.value.isNotBlank()),
                            onChannelDetails={navigate(Route("channel",it.login,it.name))})
                    } } }
                }
            }
        }
        }
        FollowNotifications(followingMonitor, enabled=settings.followNotificationsEnabled && authState.loggedIn,
            blocked=!appForeground || settingsOpen || aboutOpen || accountOpen || exitOpen || watching!=null && playerControlsVisible,
            currentLogin=watching?.login ?: railChannelPreview?.login.takeIf { route.kind=="rail-channel" },
            modifier=Modifier.align(Alignment.TopCenter).padding(top=20.dp))
        if(settings.showClock && watching==null && !settingsOpen && !aboutOpen && !accountOpen && !exitOpen) Text(clock,color=TvColors.Muted,fontSize=16.sp,modifier=Modifier.align(Alignment.TopEnd).padding(end=28.dp,top=18.dp))
        if(settingsOpen && !aboutOpen && !accountOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            catalogState.SaveableStateProvider("settings-panel") {
                SettingsScreen(settings,onSave={settingsStore.save(it)},onBack={closeSettings()},onCheckUpdates={aboutOpen=true},
                    onAccount={accountOpen=true},onAbout={aboutOpen=true})
            }
        }
        if(accountOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            BackHandler { accountOpen=false }
            AccountScreen(auth)
        }
        if(aboutOpen) Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            BackHandler { aboutOpen=false }
            Column(Modifier.padding(48.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                Text("Twitch 2.0",fontSize=32.sp,color=TvColors.Text)
                Text("${BuildConfig.VERSION_NAME} · Android TV",color=TvColors.Muted)
                Text("Собственный клиент Oprawa. Не является официальным приложением Twitch.",color=TvColors.Text)
                Text("Kotlin · AndroidX Compose TV · Media3 · OkHttp · Coil\nЛицензии: Apache-2.0 / MPL-2.0. Атрибуция включена в APK и исходники.\nАвторские иллюстрации: Oprawa.",color=TvColors.Muted)
                UpdateControls(updater)
                TvAction("Назад",{aboutOpen=false})
            }
        }
        if(exitOpen) Box(Modifier.fillMaxSize().background(TvColors.Background.copy(alpha=.96f)),contentAlignment=Alignment.Center) {
            BackHandler { exitOpen=false }
            Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("Выйти из Twitch 2.0?",color=TvColors.Text,fontSize=26.sp)
                TvAction("Остаться",{exitOpen=false})
                TvAction("Выйти",{(context as? android.app.Activity)?.finish()})
            }
        }
    }
}

@Composable private fun Viewing(target:Watching,repository:TwitchRepository,store:SettingsStore,resolveLive:suspend (String,Boolean)->String,onBack:()->Unit,
    onControlsVisibleChanged:(Boolean)->Unit) {
    val context=LocalContext.current
    val session=remember { PlaybackSession(context) }
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
    val positions=remember { PlaybackHistory(context) }
    fun savePosition() {
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
            session.play(url,"${target.kind}:${target.id}",target.kind=="live",positions.read(target.kind,target.id),
                requestStartedAtMs=requestStartedAtMs, progressiveVariants=variants)
            if(!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))session.onBackground()
        } catch(cancel:CancellationException){throw cancel}
        catch(e:Exception){error=if(e is TwitchException)e.message else "Не удалось подключиться. Проверьте сеть и повторите."}
        finally{resolving=false}
    }
    BackHandler(enabled=resolving||error!=null){onBack()}
    if(error!=null) MessageScreen("Видео недоступно",error!!,"Повторить"){retry++}
    else if(resolving) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Подключение к Twitch…",color=TvColors.Text)}
    else PlayerScreen(session,if(target.kind=="live") metadata?.title?.takeIf { it.isNotBlank() } ?: target.title else target.title,target.subtitle,onBack,
        chatContent=if(target.kind=="live") ({previewSettings -> ChatPanel(messages,previewSettings,status=chatStatus)}) else null,
        settings=settings,onSettingsChange={store.save(it)},
        channelName=metadata?.name ?: target.subtitle, avatarUrl=metadata?.avatarUrl.orEmpty(),
        category=if(target.kind=="live") metadata?.category.orEmpty() else "",
        viewers=if(target.kind=="live") metadata?.viewers else null,
        onChatPreview={chatPreviewEnabled=it},onRetry={savePosition();retry++},
        onControlsVisibleChanged=onControlsVisibleChanged)
}

@Composable private fun AccountScreen(auth:TwitchAuth) {
    val state by auth.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var device by remember { mutableStateOf<DeviceAuthorization?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loginJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit){onDispose{loginJob?.cancel()}}
    Column(Modifier.fillMaxSize().padding(36.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text("Аккаунт Twitch",fontSize=30.sp,color=TvColors.Text)
        if(state.loggedIn){Text("Вы вошли: ${state.displayName}",color=TvColors.Text);TvAction("Выйти",{device=null;auth.logout()})}
        else {
            Text("Вход подтверждается на телефоне или компьютере.\nПароль на телевизоре вводить не нужно.",color=TvColors.Muted,fontSize=18.sp)
            device?.let { Text("Откройте ${it.verificationUri}",color=TvColors.Text);Text(it.userCode,color=TvColors.Focus,fontSize=42.sp);Text("Подтвердите вход на сайте Twitch",color=TvColors.Muted) }
            (error?:state.error)?.let{Text(it,color=TvColors.Error)}
            TvAction(if(busy)"Отменить" else "Получить код", onClick = {
                if(busy){loginJob?.cancel();busy=false;device=null}
                else loginJob=scope.launch {
                    busy=true;error=null
                    try{ val d=auth.beginLogin();device=d;auth.awaitLogin(d);device=null }
                    catch(cancel:CancellationException){throw cancel}
                    catch(e:Exception){error=e.message?:"Вход не выполнен. Повторите."}
                    finally{busy=false}
                }
            })
        }
    }
}

@Composable private fun UpdateControls(updater:UpdateManager) {
    val state by updater.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val activity=checkNotNull(LocalActivity.current)
    val prefs=remember { activity.getSharedPreferences("updates",android.content.Context.MODE_PRIVATE) }
    var testChannel by remember { mutableStateOf(prefs.getBoolean("testChannel",true)) }
    val first=remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(state.message,color=TvColors.Text,fontSize=18.sp)
        if(state.status=="downloading") Text("${(state.progress*100).toInt()}%",color=TvColors.Focus)
        Row(Modifier.focusRequester(first),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            when(state.status) {
                "checking","downloading" -> TvAction("Отменить",{updater.cancel()})
                "available" -> TvAction("Скачать ${state.available?.versionName.orEmpty()}",{scope.launch{updater.download()}})
                "ready","permission","installing" -> TvAction("Установить обновление",{updater.install(activity)})
                else -> TvAction("Проверить обновления",{scope.launch{updater.check(testChannel)}})
            }
            TvAction(if(testChannel)"Канал: тестовый" else "Канал: обычный",{
                testChannel=!testChannel
                prefs.edit().putBoolean("testChannel",testChannel).apply()
                updater.cancel()
                scope.launch { updater.check(testChannel) }
            })
        }
    }
}

@Composable private fun MessageScreen(title:String,message:String,action:String,autoFocus:Boolean=true,onAction:()->Unit) {
    val first=remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if(autoFocus) first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(40.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text(title,fontSize=28.sp,color=TvColors.Text);Text(message,color=TvColors.Muted,fontSize=18.sp);TvAction(action,onAction,modifier=Modifier.focusRequester(first))
    }
}
