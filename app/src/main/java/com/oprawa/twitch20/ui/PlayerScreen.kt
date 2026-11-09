package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackState
import com.oprawa.twitch20.playback.PlaybackStatus
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
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
) {
    val state by session.state.collectAsStateWithLifecycle()
    var hudVisible by remember(session) { mutableStateOf(true) }
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
    val autoFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val theaterFocus = remember { FocusRequester() }
    var seek by remember(session) { mutableStateOf(SeekDraft()) }
    var seekWasPlaying by remember { mutableStateOf(false) }
    var timelineEnabled by remember(session) { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(140.dp) }
    var hudHeight by remember { mutableStateOf(200.dp) }
    val density=LocalDensity.current
    var timelineFocused by remember { mutableStateOf(false) }
    var focusCaption by remember { mutableStateOf("") }
    var transientPreview by remember { mutableStateOf<AppSettings?>(null) }
    val retryFocus = remember { FocusRequester() }
    val chatFocus = remember { FocusRequester() }
    val clockFocus = remember { FocusRequester() }
    val draftFocus = remember { FocusRequester() }
    val editor = rememberSettingsEditorState()
    val effectiveSettings = if (chatSettingsOpen) (transientPreview ?: draftSettings).let { preview ->
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
    val panelOpen = qualityOpen || chatSettingsOpen
    val reportControls by rememberUpdatedState(onControlsVisibleChanged)
    LaunchedEffect(hudVisible, panelOpen, state.status == PlaybackStatus.Error) {
        reportControls(hudVisible || panelOpen || state.status == PlaybackStatus.Error)
    }
    DisposableEffect(session) { onDispose { reportControls(false) } }
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(effectiveSettings.showClock) {
        if (effectiveSettings.showClock) while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    fun closeQuality() { returnFocus = "quality"; qualityOpen = false; hudVisible = true; interaction++ }
    fun openProfile(page: SettingsEditorPage, origin: String) {
        draftSettings = settings
        transientPreview = null
        editor.reset()
        editor.open(page)
        profileOrigin = origin
        qualityOpen = false
        chatSettingsOpen = true
        interaction++
    }
    fun closeProfile() {
        returnFocus = profileOrigin
        transientPreview = null
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
            hudVisible && state.status != PlaybackStatus.Error -> { hudVisible = false; interaction++ }
            else -> onBack()
        }
    }
    BackHandler { handleBack() }

    LaunchedEffect(qualityOpen) {
        if (qualityOpen && (state.canAutoSelectQuality || state.qualityOptions.isNotEmpty())) autoFocus.requestFocus()
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
    val showSideChat = chatContent != null && effectiveSettings.chatEnabled && theater && !qualityOpen
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

            if (chatContent != null && effectiveSettings.chatEnabled && !theater && !qualityOpen) {
                Box(Modifier.fillMaxSize().padding(start = ChatLayout.inset, end = ChatLayout.inset,
                    top = if (hudVisible && !panelOpen) headerHeight else 52.dp,
                    bottom = if (hudVisible && !panelOpen) hudHeight else ChatLayout.inset)) {
                    Box(Modifier.align(ChatLayout.alignment(effectiveSettings.chatPosition))
                        .width(contentWidth * ChatLayout.overlayWidth(effectiveSettings))
                        .height(contentHeight * ChatLayout.overlayHeight(effectiveSettings))) { chatContent(effectiveSettings) }
                }
            }
            if (effectiveSettings.showClock && !qualityOpen) {
                val clockAlignment = Alignment.TopEnd
                Text(clockText, color = PlayerText, fontSize = 17.sp,
                    modifier = Modifier.align(clockAlignment).zIndex(1f).padding(20.dp)
                        .background(PlayerBackground.copy(alpha = .65f), RoundedCornerShape(4.dp)).padding(4.dp))
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
                                    modifier=Modifier.semantics { contentDescription="$count зрителей" })
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
                    if (!state.isLive) VodProgress(state, seek.targetMs, timelineEnabled, Modifier.focusRequester(timelineFocus).focusProperties { down=pauseFocus },
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        if (!state.isLive) PlayerAction("Назад на 10 секунд", icon=PlayerGlyph.BACK,
                            onFocused={ focusCaption="Назад на 10 секунд" }) { session.seekBy(-10_000) }
                        PlayerAction(if (state.showPauseAction) "Пауза" else if (state.isLive) "К эфиру" else "Смотреть",
                            Modifier.focusRequester(pauseFocus), icon=if(state.showPauseAction) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                            prominent=true, onFocused={ timelineEnabled=true; focusCaption=if(state.showPauseAction) "Пауза" else if(state.isLive) "Вернуться к прямому эфиру" else "Продолжить просмотр" }) {
                            if (state.showPauseAction) session.pause() else session.resume()
                        }
                        if (!state.isLive) PlayerAction("Вперёд на 10 секунд", icon=PlayerGlyph.FORWARD,
                            onFocused={ focusCaption="Вперёд на 10 секунд" }) { session.seekBy(10_000) }
                        Spacer(Modifier.weight(1f))
                        PlayerAction("Качество", Modifier.focusRequester(qualityFocus), icon=PlayerGlyph.QUALITY, badge=state.activeQuality.ifBlank { "Авто" },
                            onFocused={ focusCaption="Качество" }) { qualityOpen=true }
                        if (chatContent != null) {
                            PlayerAction(if(settings.overlayEnabled) "Чат: вкл" else "Чат: выкл",
                                Modifier.focusRequester(chatFocus), active=settings.overlayEnabled, icon=PlayerGlyph.CHAT,
                                onFocused={ focusCaption="Чат · ${if(settings.overlayEnabled) "включён" else "выключен"} · Удерживайте OK для настройки" },
                                onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.OVERLAY,"chat") }) {
                                onSettingsChange(settings.toggleChat(ChatMode.OVERLAY))
                            }
                            PlayerAction(if(settings.theaterEnabled) "Кинотеатр: вкл" else "Кинотеатр: выкл",
                                Modifier.focusRequester(theaterFocus), active=settings.theaterEnabled, icon=PlayerGlyph.THEATER,
                                onFocused={ focusCaption="Кинотеатр · ${if(settings.theaterEnabled) "включён" else "выключен"} · Удерживайте OK для настройки" },
                                onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.THEATER,"theater") }) {
                                onSettingsChange(settings.toggleChat(ChatMode.THEATER))
                            }
                        }
                        PlayerAction("Часы", Modifier.focusRequester(clockFocus), icon=PlayerGlyph.CLOCK, active=settings.showClock,
                            onFocused={ focusCaption="Часы" },
                            onLongClick={ pending -> swallowedKey=pending; openProfile(SettingsEditorPage.CLOCK,"clock") }) {
                            onSettingsChange(settings.copy(showClock=!settings.showClock))
                        }
                    }
                    Text(if(timelineFocused) {
                        if(seek.active) "OK — перейти к ${timeText(seek.targetMs ?: state.positionMs)}" else "Перемотка"
                    } else when (focusCaption) {
                        "Качество" -> "Качество · ${state.activeQuality.ifBlank { "Авто" }}"
                        "Часы" -> "Часы · ${if(settings.showClock) "показаны" else "скрыты"} · Удерживайте OK для настройки"
                        else -> focusCaption
                    }, color=PlayerText, fontSize=12.sp, maxLines=1,
                        overflow=TextOverflow.Ellipsis, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth().heightIn(min=18.dp))
                }
            }
        }

        if (showSideChat) {
            Box(Modifier.width(playbackAreaWidth * ChatLayout.theaterWidthFraction)
                .fillMaxHeight().background(PlayerPanel)) { chatContent(effectiveSettings) }
        }
        if (qualityOpen) {
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
                        onPreviewSettings = { transientPreview = it }, onCloseProfile = { closeProfile() })
                }
            }
        }
    }
    }

}

@Composable
private fun VodProgress(state: PlaybackState, targetMs: Long?, enabled: Boolean, modifier: Modifier,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(targetMs != null) "Перейти к ${timeText(shown)}" else timeText(shown),
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
    onFocused: () -> Unit = {}, onLongClick: ((Int?) -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(focused, label) { if(focused) onFocused() }
    Box(modifier.then(if(icon!=null) Modifier.size(if(prominent) 56.dp else 44.dp) else Modifier)
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
            if(icon!=null) contentDescription=label
            selected = active
            onClick { onClick(); true }
        }
        .focusable().then(if(icon==null) Modifier.padding(horizontal = 14.dp, vertical = 11.dp) else Modifier),
        contentAlignment = if(icon!=null) Alignment.Center else Alignment.CenterStart) {
        if(icon!=null) {
            if (badge == null) PlayerGlyph(icon, if(focused) PlayerBackground else PlayerText, prominent)
            else Column(horizontalAlignment=Alignment.CenterHorizontally) {
                PlayerGlyph(icon, if(focused) PlayerBackground else PlayerText)
                Text(badge, color=if(focused) PlayerBackground else PlayerText, fontSize=9.sp,
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
