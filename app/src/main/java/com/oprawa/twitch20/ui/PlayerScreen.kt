package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.Text
import com.oprawa.twitch20.data.VodPlaybackSpeed
import com.oprawa.twitch20.data.VodStoryboard
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackState
import com.oprawa.twitch20.playback.PlaybackStatus
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ClockPlacement
import com.oprawa.twitch20.settings.ChatPosition
import kotlinx.coroutines.delay
import java.util.Locale

private val PlayerBackground get() = TvColors.Background
private val PlayerPanel get() = TvColors.Surface
private val PlayerFocus get() = TvColors.Focus
private val PlayerText get() = TvColors.Text
private val PlayerMuted get() = TvColors.Muted

/** Owns UI layers only; the caller owns and releases [session]. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: PlaybackSession,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    channelName: String = subtitle,
    avatarUrl: String = "",
    category: String = "",
    viewers: Int? = null,
    chatContent: (@Composable (AppSettings) -> Unit)? = null,
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onChatPreview: (Boolean?) -> Unit = {},
    onRetry: () -> Unit = { session.retry() },
    onControlsVisibleChanged: (Boolean) -> Unit = {},
    isVod: Boolean = false,
    onSpeedChange: (Float) -> Unit = {},
    loadStoryboard: (suspend () -> VodStoryboard?)? = null,
) {
    val state by session.state.collectAsStateWithLifecycle()
    var hudVisible by remember(session) { mutableStateOf(true) }
    var speedOpen by remember(session) { mutableStateOf(false) }
    var qualityOpen by remember(session) { mutableStateOf(false) }
    var chatSettingsOpen by remember(session) { mutableStateOf(false) }
    var draftSettings by remember(session) { mutableStateOf(settings) }
    var profileOrigin by remember { mutableStateOf("chat") }
    var interaction by remember { mutableLongStateOf(0) }
    var swallowedKey by remember { mutableStateOf<Int?>(null) }
    var returnFocus by remember { mutableStateOf<String?>(null) }
    val screenFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val selectedSpeedFocus = remember { FocusRequester() }
    val autoFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val theaterFocus = remember { FocusRequester() }
    val storyboardLoader by rememberUpdatedState(loadStoryboard)
    var storyboard by remember(session, state.mediaId) { mutableStateOf<VodStoryboard?>(null) }
    LaunchedEffect(session, state.mediaId, loadStoryboard != null) {
        if (state.mediaId.isNotBlank()) storyboard = storyboardLoader?.invoke()
    }
    var seek by remember(session) { mutableStateOf(SeekDraft()) }
    var seekWasPlaying by remember { mutableStateOf(false) }
    var timelineEnabled by remember(session) { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(140.dp) }
    var hudHeight by remember { mutableStateOf(200.dp) }
    val density=LocalDensity.current
    var timelineFocused by remember { mutableStateOf(false) }
    var focusCaption by remember { mutableStateOf("") }
    val retryFocus = remember { FocusRequester() }
    val chatFocus = remember { FocusRequester() }
    val clockFocus = remember { FocusRequester() }
    val draftFocus = remember { FocusRequester() }
    val editor = rememberSettingsEditorState()
    val effectiveSettings = if (chatSettingsOpen) draftSettings.let { preview ->
        when(editor.page) {
            SettingsEditorPage.OVERLAY -> preview.copy(chatMode=ChatMode.OVERLAY)
            SettingsEditorPage.THEATER -> preview.copy(chatMode=ChatMode.THEATER)
            else -> preview
        }
    } else settings
    LaunchedEffect(chatSettingsOpen, effectiveSettings.chatEnabled) {
        onChatPreview(if (chatSettingsOpen) effectiveSettings.chatEnabled else null)
    }
    DisposableEffect(Unit) { onDispose { onChatPreview(null) } }
    val panelOpen = qualityOpen || speedOpen || chatSettingsOpen
    val reportControls by rememberUpdatedState(onControlsVisibleChanged)
    LaunchedEffect(hudVisible, panelOpen, state.status == PlaybackStatus.Error) {
        reportControls(hudVisible || panelOpen || state.status == PlaybackStatus.Error)
    }
    DisposableEffect(session) { onDispose { reportControls(false) } }
    fun closeSpeed() { returnFocus = "speed"; speedOpen = false; hudVisible = true; interaction++ }
    fun closeQuality() { returnFocus = "quality"; qualityOpen = false; hudVisible = true; interaction++ }
    fun openProfile(page: SettingsEditorPage, origin: String) {
        draftSettings = settings
        editor.reset()
        editor.open(page)
        profileOrigin = origin
        qualityOpen = false
        chatSettingsOpen = true
        interaction++
    }
    fun closeProfile() {
        returnFocus = profileOrigin
        chatSettingsOpen = false
        hudVisible = true
        interaction++
    }

    fun cancelSeek() {
        seek = seek.cancel()
        if (seekWasPlaying) session.resume()
        interaction++
    }
    fun handleBack() {
        when {
            seek.active -> cancelSeek()
            chatSettingsOpen -> closeProfile()
            qualityOpen -> closeQuality()
            speedOpen -> closeSpeed()
            hudVisible && state.status != PlaybackStatus.Error -> { hudVisible = false; interaction++ }
            else -> onBack()
        }
    }
    BackHandler { handleBack() }

    LaunchedEffect(qualityOpen) {
        if (qualityOpen && (state.canAutoSelectQuality || state.qualityOptions.isNotEmpty())) autoFocus.requestFocus()
    }
    LaunchedEffect(speedOpen) {
        if (speedOpen) selectedSpeedFocus.requestFocus()
    }
    LaunchedEffect(chatSettingsOpen) {
        if (chatSettingsOpen) draftFocus.requestFocus()
    }
    LaunchedEffect(hudVisible, panelOpen, state.status == PlaybackStatus.Error) {
        // One focus owner for panel returns, including Quality opened from the error screen.
        androidx.compose.runtime.withFrameNanos { }
        val returning = if (!panelOpen) returnFocus else null
        when {
            panelOpen -> Unit
            returning == "quality" && (state.status != PlaybackStatus.Error || state.qualityOptions.isNotEmpty()) -> qualityFocus.requestFocus()
            returning == "speed" && state.status != PlaybackStatus.Error -> speedFocus.requestFocus()
            returning == "chat" && state.status != PlaybackStatus.Error -> chatFocus.requestFocus()
            returning == "theater" && state.status != PlaybackStatus.Error -> theaterFocus.requestFocus()
            returning == "clock" && state.status != PlaybackStatus.Error -> clockFocus.requestFocus()
            state.status == PlaybackStatus.Error -> retryFocus.requestFocus()
            hudVisible -> pauseFocus.requestFocus()
            else -> screenFocus.requestFocus()
        }
        if (!panelOpen) returnFocus = null
    }
    LaunchedEffect(hudVisible, interaction, panelOpen, state.isPlaying, timelineFocused, seek.active) {
        if (hudVisible && !panelOpen && state.isPlaying && !timelineFocused && !seek.active) {
            delay(6_000)
            hudVisible = false
        }
    }

    val theater = effectiveSettings.chatMode == ChatMode.THEATER
    val showChat = chatContent != null && effectiveSettings.chatEnabled && !qualityOpen && !speedOpen
    val showSideChat = showChat && theater
    val clockInChat = showChat && effectiveSettings.clockPlacement == ClockPlacement.CHAT
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val playbackAreaWidth = maxWidth - if (panelOpen) 300.dp else 0.dp
    Row(Modifier.fillMaxSize().background(PlayerBackground)
        .focusRequester(screenFocus)
        // The fullscreen key target is needed only while controls are hidden.
        // Otherwise Android's initial focus can land here and strand D-pad navigation.
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            if (code == AndroidKeyEvent.KEYCODE_BACK && !chatSettingsOpen) {
                // Consume before Compose focus navigation can move child focus to this root.
                // DOWN (including repeats) does nothing; one uncancelled UP closes one layer.
                if (native.action == AndroidKeyEvent.ACTION_UP && !native.isCanceled) handleBack()
                true
            } else if (swallowedKey == code) {
                if (native.action == AndroidKeyEvent.ACTION_UP) swallowedKey = null
                true
            } else if (!hudVisible && !panelOpen && state.status != PlaybackStatus.Error && code in revealKeys) {
                if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                    swallowedKey = code
                    hudVisible = true
                    interaction++
                }
                true
            } else {
                if (native.action == AndroidKeyEvent.ACTION_DOWN) interaction++
                false
            }
        }.focusable(enabled = !hudVisible && !panelOpen && state.status != PlaybackStatus.Error)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val contentWidth = maxWidth
            val contentHeight = maxHeight
            // The ContentFrame stays at the same composition location when panels change size.
            Column(Modifier.fillMaxSize().padding(if (panelOpen) 24.dp else 0.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ContentFrame(player = session.player, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit)
                }
                if (panelOpen) {
                    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                        Text(title, color = PlayerText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = PlayerMuted, fontSize = 13.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        if (chatSettingsOpen && effectiveSettings.chatMode != draftSettings.chatMode)
                            Text("Предпросмотр выключенного профиля", color=TvColors.Link, fontSize=12.sp)
                    }
                }
            }

            if (chatContent != null && showChat && !theater) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(
                    start = ChatLayout.inset, end = ChatLayout.inset,
                    top = if (hudVisible && !panelOpen) headerHeight else ChatLayout.inset,
                    bottom = if (hudVisible && !panelOpen) hudHeight else ChatLayout.inset,
                )) {
                    val animation = if (effectiveSettings.animationsEnabled) tween<androidx.compose.ui.unit.Dp>(220) else snap()
                    val targetWidth = (contentWidth * ChatLayout.overlayWidth(effectiveSettings)).coerceAtMost(maxWidth)
                    val targetHeight = (contentHeight * ChatLayout.overlayHeight(effectiveSettings)).coerceAtMost(maxHeight)
                    val alignment = ChatLayout.alignment(effectiveSettings.chatPosition)
                    val x by animateDpAsState(if (alignment == Alignment.TopEnd || alignment == Alignment.BottomEnd) maxWidth - targetWidth else 0.dp, animation)
                    val y by animateDpAsState(if (alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd) maxHeight - targetHeight else 0.dp, animation)
                    val width by animateDpAsState(targetWidth, animation)
                    val height by animateDpAsState(targetHeight, animation)
                    Box(Modifier.offset(x.coerceAtMost(maxWidth - width.coerceAtMost(maxWidth)),
                        y.coerceAtMost(maxHeight - height.coerceAtMost(maxHeight)))
                        .width(width).height(height)) {
                        chatContent(effectiveSettings)
                    }
                }
            }
            if (!clockInChat && !qualityOpen && !speedOpen) {
                val chatAtTopEnd = showChat && !theater && effectiveSettings.chatPosition == ChatPosition.TOP_RIGHT
                val alignment = if (!hudVisible && chatAtTopEnd) Alignment.TopStart else Alignment.TopEnd
                TvClock(effectiveSettings, Modifier.align(alignment).zIndex(1f).padding(20.dp))
            }

            if (state.status == PlaybackStatus.Buffering) {
                Text(if (state.firstFrameMs == null) "Подключение к видео…" else "Буферизация…",
                    color = PlayerText, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                        .background(PlayerPanel.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp))
            }

            if (state.status == PlaybackStatus.Error && !panelOpen) {
                Column(Modifier.align(Alignment.Center).width(440.dp)
                    .background(PlayerPanel, RoundedCornerShape(8.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Видео недоступно", color = PlayerText, fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(state.error ?: "Попробуйте открыть канал заново.", color = PlayerMuted,
                        fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerAction("Повторить", Modifier.focusRequester(retryFocus)) { onRetry() }
                        if (state.qualityOptions.isNotEmpty()) {
                            PlayerAction("Качество", Modifier.focusRequester(qualityFocus)) { qualityOpen = true }
                        }
                        PlayerAction("Назад") { onBack() }
                    }
                }
            } else if (hudVisible && !panelOpen) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.10f)))
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .onSizeChanged { headerHeight=with(density) { it.height.toDp() } }
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha=.92f),
                        .65f to Color.Black.copy(alpha=.70f), 1f to Color.Transparent))
                    .padding(start=28.dp, end=100.dp, top=22.dp, bottom=34.dp),
                    horizontalArrangement=Arrangement.spacedBy(14.dp), verticalAlignment=Alignment.Top) {
                    if (avatarUrl.isNotBlank()) AsyncImage(model=avatarUrl, contentDescription=null,
                        modifier=Modifier.size(48.dp).clip(CircleShape).background(TvColors.Raised), contentScale=ContentScale.Crop)
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(channelName, color=PlayerText, fontSize=16.sp, fontWeight=FontWeight.SemiBold,
                            maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text(title, color=PlayerText, fontSize=21.sp, fontWeight=FontWeight.SemiBold,
                            maxLines=2, overflow=TextOverflow.Ellipsis)
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp), verticalAlignment=Alignment.CenterVertically) {
                            if (category.isNotBlank()) Text(category, color=PlayerMuted, fontSize=14.sp,
                                maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f, fill=false))
                            viewers?.let { count -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                PlayerGlyph(PlayerGlyph.VIEWERS, PlayerMuted)
                                Text(java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru")).format(count),
                                    color=PlayerMuted, fontSize=14.sp,
                                    modifier=Modifier.semantics { contentDescription=viewerLabel(count) })
                            } }
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { hudHeight=with(density) { it.height.toDp() } }
                    .background(Brush.verticalGradient(0f to Color.Transparent, .28f to Color.Black.copy(alpha=.78f),
                        .65f to Color.Black.copy(alpha=.94f), 1f to Color.Black.copy(alpha=.96f)))
                    .padding(start=28.dp, end=28.dp, top=38.dp, bottom=16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if (!state.isLive) VodProgress(state, seek.targetMs, timelineEnabled,
                        Modifier.focusRequester(timelineFocus).focusProperties { down=pauseFocus },
                        storyboard = storyboard,
                        onFocus = { timelineFocused=it },
                        onMove = { direction, held ->
                            if (!seek.active && state.durationMs > 0) {
                                seekWasPlaying=state.showPauseAction
                                seek=seek.begin(session.player.currentPosition, state.durationMs)
                                session.pause()
                            }
                            seek=seek.move(direction,held)
                        }, onConfirm = {
                            val result=seek.confirm(); seek=result.next
                            result.targetMs?.let { session.seekTo(it, seekWasPlaying) }
                        })
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val actionWidth = if (maxWidth < 650.dp) 38.dp else 44.dp
                        val gap = if (maxWidth < 650.dp) 6.dp else 10.dp
                        val transportWidth = (actionWidth + gap) * 2 + 56.dp
                        val rightWidth = (actionWidth + gap) * ((if (isVod) 3 else 2) + (if (chatContent != null) 2 else 0)) - gap
                        val transportStart = if (state.isLive) 0.dp else
                            ((maxWidth - transportWidth) / 2).coerceAtMost((maxWidth - transportWidth - rightWidth - gap).coerceAtLeast(0.dp))
                        Row(Modifier.offset(x = transportStart).align(Alignment.CenterStart),
                            horizontalArrangement=Arrangement.spacedBy(gap), verticalAlignment=Alignment.CenterVertically) {
                            if (!state.isLive) PlayerAction("Назад на 10 секунд", icon=PlayerGlyph.BACK, iconSize=actionWidth,
                                onFocused={ focusCaption="Назад на 10 секунд" }) { session.seekBy(-10_000) }
                            PlayerAction(if (state.showPauseAction) "Пауза" else if (state.isLive) "К эфиру" else "Смотреть",
                                Modifier.focusRequester(pauseFocus), icon=if(state.showPauseAction) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                                prominent=true, onFocused={ timelineEnabled=true; focusCaption=if(state.showPauseAction) "Пауза" else if(state.isLive) "Вернуться к прямому эфиру" else "Продолжить просмотр" }) {
                                if (state.showPauseAction) session.pause() else session.resume()
                            }
                            if (!state.isLive) PlayerAction("Вперёд на 10 секунд", icon=PlayerGlyph.FORWARD, iconSize=actionWidth,
                                onFocused={ focusCaption="Вперёд на 10 секунд" }) { session.seekBy(10_000) }
                        }
                        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement=Arrangement.spacedBy(gap),
                            verticalAlignment=Alignment.CenterVertically) {
                            if (isVod && !state.isLive) PlayerAction("Скорость · ${VodPlaybackSpeed.label(state.playbackSpeed)}",
                                Modifier.focusRequester(speedFocus), badge=VodPlaybackSpeed.label(state.playbackSpeed),
                                speedIcon=true, iconSize=actionWidth, onFocused={ focusCaption="Скорость" }) { speedOpen=true }
                            PlayerAction("Качество", Modifier.focusRequester(qualityFocus), icon=PlayerGlyph.QUALITY, iconSize=actionWidth, badge=state.activeQuality.ifBlank { "Авто" },
                                onFocused={ focusCaption="Качество" }) { qualityOpen=true }
                            if (chatContent != null) {
                                PlayerAction(if(settings.overlayEnabled) "Чат: вкл" else "Чат: выкл",
                                    Modifier.focusRequester(chatFocus), active=settings.overlayEnabled, icon=PlayerGlyph.CHAT, iconSize=actionWidth,
                                    onFocused={ focusCaption="Чат · ${if(settings.overlayEnabled) "включён" else "выключен"} · Удерживайте OK для настройки" },
                                    onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.OVERLAY,"chat") }) {
                                    onSettingsChange(settings.toggleChat(ChatMode.OVERLAY))
                                }
                                PlayerAction(if(settings.theaterEnabled) "Кинотеатр: вкл" else "Кинотеатр: выкл",
                                    Modifier.focusRequester(theaterFocus), active=settings.theaterEnabled, icon=PlayerGlyph.THEATER, iconSize=actionWidth,
                                    onFocused={ focusCaption="Кинотеатр · ${if(settings.theaterEnabled) "включён" else "выключен"} · Удерживайте OK для настройки" },
                                    onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.THEATER,"theater") }) {
                                    onSettingsChange(settings.toggleChat(ChatMode.THEATER))
                                }
                            }
                            PlayerAction("Часы", Modifier.focusRequester(clockFocus), icon=PlayerGlyph.CLOCK, iconSize=actionWidth, active=settings.showClock,
                                onFocused={ focusCaption="Часы" },
                                onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.CLOCK,"clock") }) {
                                onSettingsChange(settings.copy(showClock=!settings.showClock))
                            }
                        }
                    }
                    Text(if(timelineFocused) {
                        if(seek.active) "OK — перейти к ${timeText(seek.targetMs ?: state.positionMs)}" else "Перемотка"
                    } else when (focusCaption) {
                        "Скорость" -> "Скорость · ${VodPlaybackSpeed.label(state.playbackSpeed)}"
                        "Качество" -> "Качество · ${state.activeQuality.ifBlank { "Авто" }}"
                        "Часы" -> "Часы · ${if(settings.showClock) "показаны" else "скрыты"} · Удерживайте OK для настройки"
                        else -> focusCaption
                    }, color=PlayerText, fontSize=12.sp, maxLines=1,
                        overflow=TextOverflow.Ellipsis, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth().heightIn(min=18.dp))
                }
            }
        }

        if (showSideChat && chatContent != null) {
            Box(Modifier.width(playbackAreaWidth * ChatLayout.theaterWidthFraction)
                .fillMaxHeight().background(PlayerPanel)) { chatContent(effectiveSettings) }
        }
        if (speedOpen) {
            Column(Modifier.width(300.dp).fillMaxHeight().background(PlayerPanel)
                .border(1.dp, TvColors.Border).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Скорость воспроизведения", color = PlayerText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VodPlaybackSpeed.options.forEach { speed ->
                        val selected = speed == VodPlaybackSpeed.sanitize(state.playbackSpeed)
                        PlayerAction(VodPlaybackSpeed.label(speed),
                            Modifier.fillMaxWidth().then(if (selected) Modifier.focusRequester(selectedSpeedFocus) else Modifier),
                            active = selected) {
                            session.setPlaybackSpeed(speed)
                            onSpeedChange(speed)
                            closeSpeed()
                        }
                    }
                }
                PlayerAction("Назад", Modifier.fillMaxWidth()) { closeSpeed() }
            }
        } else if (qualityOpen) {
            Column(Modifier.width(300.dp).fillMaxHeight().background(PlayerPanel)
                .border(1.dp, TvColors.Border).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Качество видео", color = PlayerText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(if (state.canAutoSelectQuality) "Авто подбирает качество по скорости сети"
                    else "Выберите качество клипа", color = PlayerMuted, fontSize = 12.sp)
                LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex =
                    if(state.selectedQualityId==null) 0 else (state.qualityOptions.indexOfFirst { it.id==state.selectedQualityId }.coerceAtLeast(0) + if(state.canAutoSelectQuality) 1 else 0)),
                    modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canAutoSelectQuality) item {
                        PlayerAction(if (state.selectedQualityId == null) "✓ Авто" else "Авто",
                            Modifier.fillMaxWidth().then(if (state.selectedQualityId == null) Modifier.focusRequester(autoFocus) else Modifier)) {
                            session.selectQuality(null); closeQuality()
                        }
                    }
                    items(state.qualityOptions, key = { it.id }) { quality ->
                        PlayerAction(if (quality.id == state.selectedQualityId) "✓ ${quality.label}" else quality.label,
                            Modifier.fillMaxWidth().then(if (quality.id == state.selectedQualityId || (!state.canAutoSelectQuality &&
                                state.selectedQualityId == null && quality.id == state.qualityOptions.firstOrNull()?.id)) Modifier.focusRequester(autoFocus)
                                else Modifier)) {
                            session.selectQuality(quality.id); closeQuality()
                        }
                    }
                    if (state.qualityOptions.isEmpty()) item {
                        Text("Варианты появятся после загрузки видео", color = PlayerMuted,
                            fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                PlayerAction("Назад", Modifier.fillMaxWidth()) { closeQuality() }
            }
        } else if (chatSettingsOpen) {
            Column(Modifier.width(300.dp).fillMaxHeight().background(PlayerPanel)
                .border(1.dp, TvColors.Border).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatSettingsControls(draftSettings, { draftSettings = it; onSettingsChange(it) }, draftFocus, state = editor,
                        onCloseProfile = { closeProfile() })
                }
            }
        }
    }
    }

}

@Composable
private fun VodProgress(state: PlaybackState, targetMs: Long?, enabled: Boolean, modifier: Modifier,
    storyboard: VodStoryboard?,
    onFocus: (Boolean) -> Unit, onMove: (Int, Long) -> Unit, onConfirm: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shown = targetMs ?: state.positionMs
    Column(modifier.onFocusChanged { focused=it.isFocused; onFocus(it.isFocused) }
        .semantics { contentDescription="Перемотка записи: ${if(targetMs != null) "Перейти к " else ""}${timeText(shown)}" }
        .onPreviewKeyEvent { event ->
            val key=event.nativeKeyEvent
            when(key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if(key.action==AndroidKeyEvent.ACTION_DOWN) onMove(
                        if(key.keyCode==AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1, key.eventTime-key.downTime)
                    true
                }
                in confirmKeys -> { if(key.action==AndroidKeyEvent.ACTION_UP && !key.isCanceled) onConfirm(); true }
                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> targetMs != null
                else -> false
            }
        }.focusable(enabled=enabled), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if (targetMs != null && storyboard != null) {
            VodStoryboardPreview(storyboard, targetMs, state.durationMs)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            val delta = shown - state.positionMs
            val deltaText = (if (delta < 0) "−" else "+") + timeText(kotlin.math.abs(delta))
            Text(if(targetMs != null) "Перейти к ${timeText(shown)} · $deltaText" else timeText(shown),
                color=if(focused) PlayerFocus else PlayerText, fontSize=14.sp)
            Text(if(state.durationMs>0) timeText(state.durationMs) else "—", color=PlayerMuted,fontSize=14.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val y=size.height/2
            val width=size.width-12.dp.toPx()
            val start=6.dp.toPx()
            fun x(time:Long)=start+width*(if(state.durationMs>0) time.toFloat()/state.durationMs else 0f).coerceIn(0f,1f)
            drawLine(Color.White.copy(alpha=.25f), Offset(start,y),Offset(start+width,y),4.dp.toPx())
            drawLine(Color.White.copy(alpha=.35f),Offset(start,y),Offset(x(state.bufferedPositionMs),y),4.dp.toPx())
            drawLine(TvColors.Accent,Offset(start,y),Offset(x(state.positionMs),y),4.dp.toPx())
            if(targetMs!=null) drawCircle(Color.White,3.dp.toPx(),Offset(x(state.positionMs),y))
            drawCircle(if(focused) PlayerFocus else PlayerText, (if(focused) 6 else 4).dp.toPx(),Offset(x(shown),y))
        }
    }
}

@Composable
private fun PlayerAction(label: String, modifier: Modifier = Modifier,
    active: Boolean = false, icon: PlayerGlyph? = null, prominent: Boolean = false, badge: String? = null,
    iconSize: androidx.compose.ui.unit.Dp = 44.dp, speedIcon: Boolean = false,
    onFocused: () -> Unit = {}, onLongClick: ((Int?) -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(focused, label) { if(focused) onFocused() }
    val hasIcon = icon != null || speedIcon
    Box(modifier.then(if(hasIcon) Modifier.size(if(prominent) 56.dp else iconSize) else Modifier)
        .onFocusChanged { focused = it.isFocused; if(it.isFocused) onFocused(); if (!it.isFocused) longTriggered = false }
        .background(when { focused -> PlayerFocus; active -> TvColors.Accent.copy(alpha = .22f); else -> TvColors.Raised }, TvShapes.Control)
        .border(1.dp, when { focused -> PlayerFocus; active -> TvColors.Link; else -> TvColors.Border }, TvShapes.Control)
        .onKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in confirmKeys) {
                val held = key.isLongPress || key.eventTime - key.downTime >= ViewConfiguration.getLongPressTimeout()
                if (key.action == AndroidKeyEvent.ACTION_DOWN && held && !longTriggered) {
                    longTriggered = true
                    onLongClick?.invoke(key.keyCode)
                }
                if (key.action == AndroidKeyEvent.ACTION_UP) {
                    if (!key.isCanceled && !longTriggered) { if (held) onLongClick?.invoke(null) else onClick() }
                    longTriggered = false
                }
                true // A held OK never produces a second short action on release.
            } else false
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if(hasIcon) contentDescription=label
            selected = active
            onClick { onClick(); true }
        }
        .focusable().then(if(!hasIcon) Modifier.padding(horizontal = 14.dp, vertical = 11.dp) else Modifier),
        contentAlignment = if(hasIcon) Alignment.Center else Alignment.CenterStart) {
        if(hasIcon) {
            val color = if (focused) PlayerBackground else PlayerText
            if (badge == null && icon != null) PlayerGlyph(icon, color, prominent)
            else Column(horizontalAlignment=Alignment.CenterHorizontally) {
                if (speedIcon) Canvas(Modifier.size(20.dp)) {
                    drawArc(color, 180f, 180f, useCenter = false,
                        topLeft = Offset(size.width * .12f, size.height * .2f),
                        size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .76f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
                    drawLine(color, Offset(size.width * .5f, size.height * .58f),
                        Offset(size.width * .73f, size.height * .38f), 1.6.dp.toPx())
                } else if (icon != null) PlayerGlyph(icon, color)
                Text(badge.orEmpty(), color=if(focused) PlayerBackground else PlayerText, fontSize=9.sp,
                    lineHeight=10.sp, fontWeight=FontWeight.Bold, maxLines=1)
            }
            if(active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp).width(12.dp).height(3.dp)
                .background(if(focused) PlayerBackground else TvColors.Link, TvShapes.Control))
        } else Text(label, color = if (focused) PlayerBackground else PlayerText, fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val confirmKeys = setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
private val revealKeys = confirmKeys + setOf(AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

private fun timeText(timeMs: Long): String {
    val seconds = (timeMs / 1_000).coerceAtLeast(0)
    return if (seconds >= 3_600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600,
        (seconds / 60) % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
