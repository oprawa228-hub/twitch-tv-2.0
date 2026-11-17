package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.LocalTextStyle
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Channel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** UI state only: one host-owned subscription monitor supplies both the rail and catalog. */
data class FollowingSidebarState(
    val channels: List<Channel> = emptyList(),
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val initialized: Boolean = false,
    val error: String? = null,
)

private data class RailEntry(val key: String, val label: String)

private val entries = listOf(RailEntry("search", "Поиск"), RailEntry("following", "Каналы"),
    RailEntry("recordings", "Записи"), RailEntry("categories", "Категории"),
    RailEntry("streams", "Рекомендации"))
private val railLabelSize = 14.sp
private val compactRailWidth = 84.dp
private val expandedRailWidth: Dp
    @Composable get() {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelStyle = LocalTextStyle.current.copy(fontSize = railLabelSize, fontWeight = FontWeight.SemiBold)
        return remember(measurer, density, labelStyle) {
            val labelPx = (entries.map { it.label } + "Настройки").maxOf {
                measurer.measure(it, labelStyle, maxLines = 1).size.width
            }
            // Outer padding 2×10, row padding 2×17, icon 30 and gap 12 dp.
            // Allow a little room for rounding across the separately measured children.
            maxOf(208.dp, 100.dp + with(density) { labelPx.toDp() })
        }
    }

/** Both panes share a measured edge, including during a reversed D-pad transition. */
@Composable
internal fun CatalogNavigationLayout(
    modifier: Modifier = Modifier,
    waitingForContent: Boolean = false,
    rail: @Composable (needsInitialFocus: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var layoutHasFocus by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    Row(modifier.fillMaxSize().onFocusChanged { layoutHasFocus = it.hasFocus }.focusGroup()) {
        rail(waitingForContent && windowFocused && !layoutHasFocus)
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/** Search above the library, followed channels below it, Settings alone at the bottom. */
@Composable
fun NavigationRail(
    selected: String,
    onSelect: (String) -> Unit,
    accountName: String,
    modifier: Modifier = Modifier,
    onFocusOwnership: (Boolean) -> Unit = {},
    onPreview: (String) -> Unit = {},
    following: FollowingSidebarState = FollowingSidebarState(signedIn = accountName.isNotBlank()),
    onChannelPreview: (Channel) -> Unit = {},
    onChannelSelect: (Channel) -> Unit = {},
    onChannelDetails: (Channel) -> Unit = {},
    onChannelFocusChanged: (String?) -> Unit = {},
    rankedOfflineLogins: List<String> = emptyList(),
    allowExpand: Boolean = true,
    animationsEnabled: Boolean = true,
    needsInitialFocus: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
    // Five destinations + Settings + spacing/divider; only show complete shortcut rows.
    val capacity = ((maxHeight - 332.dp) / 46.dp).toInt().coerceIn(0, 4)
    var railFocused by remember { mutableStateOf(false) }
    val expanded = railFocused && allowExpand
    val motion = rememberPreviewMotionEnabled(animationsEnabled)
    val openWidth = expandedRailWidth
    val width = animateDpAsState(if (expanded) openWidth else compactRailWidth,
        animationSpec = if (motion) tween(160) else snap(), label = "navigation width")
    val labelsVisible by remember { derivedStateOf { width.value > compactRailWidth } }
    val labelWidth = openWidth - 96.dp
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val channelsById = remember(following.channels) { following.channels.associateBy { it.id } }
    val preferredIds = remember(following.channels, rankedOfflineLogins) {
        val offlineByLogin = following.channels.filterNot { it.live }.associateBy { it.login }
        val rankedOffline = rankedOfflineLogins.mapNotNull(offlineByLogin::get)
        following.channels.filter { it.live }.map { it.id } +
            (rankedOffline + offlineByLogin.values).distinctBy { it.id }.map { it.id }
    }
    // Keep existing D-pad targets in place while focused, but render current metadata.
    var order by remember(following.signedIn, accountName) { mutableStateOf<List<String>>(emptyList()) }
    val channelIds = remember(expanded, order, channelsById, preferredIds, capacity) {
        (if (expanded && order.isNotEmpty()) {
            order.filter { it in channelsById } + preferredIds.filterNot { it in order }
        } else preferredIds).take(capacity)
    }
    val channels = channelIds.mapNotNull(channelsById::get)
    val focusedChannelId = focusedKey?.takeIf { it.startsWith("live:") }?.removePrefix("live:")
    val removedIndex = if (expanded && focusedChannelId != null && focusedChannelId !in channelIds) {
        order.indexOf(focusedChannelId)
    } else -1
    val requesterCache = remember { mutableMapOf<String, FocusRequester>() }
    val focusKeys = entries.map { it.key } + channelIds.map { "live:$it" } + "settings"
    val requesters = remember(focusKeys) {
        requesterCache.keys.retainAll(focusKeys.toSet())
        focusKeys.associateWith { requesterCache.getOrPut(it) { FocusRequester() } }
    }
    val currentRequesters by rememberUpdatedState(requesters)
    LaunchedEffect(needsInitialFocus, selected) {
        if (needsInitialFocus) {
            // Let the mounted content claim its first button/card. During a slow load
            // there is no body target to receive the first remote key.
            withFrameNanos { }
            (currentRequesters[selected] ?: currentRequesters.getValue("streams")).requestFocus()
        }
    }
    fun focusChannel(id: String) {
        currentRequesters["live:$id"]?.requestFocus()
    }
    SideEffect { order = channelIds }
    LaunchedEffect(channelIds) {
        if (removedIndex >= 0) {
            if (channels.isEmpty()) requesters.getValue("following").requestFocus()
            else focusChannel(channelIds[removedIndex.coerceAtMost(channelIds.lastIndex)])
        }
    }
    Column(Modifier.layout { measurable, constraints ->
        // Read animation progress during measurement; focus and subscription rows
        // recompose only when their own state changes.
        val pixels = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
        val panel = measurable.measure(constraints.copy(minWidth = pixels, maxWidth = pixels))
        layout(panel.width, panel.height) { panel.placeRelative(0, 0) }
    }.fillMaxHeight()
        .background(TvColors.Surface)
        .onFocusChanged {
            railFocused = it.hasFocus
            onFocusOwnership(it.hasFocus)
            if (!it.hasFocus) onChannelFocusChanged(null)
        }
        .onPreviewKeyEvent {
            if (it.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                it.nativeKeyEvent.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) onFocusOwnership(true)
            false
        }
        .focusProperties {
            onEnter = {
                when (requestedFocusDirection) {
                    FocusDirection.Left -> {
                        val target = requesters[selected] ?: requesters.getValue("streams")
                        if (!target.requestFocus()) cancelFocusChange()
                    }
                    FocusDirection.Up, FocusDirection.Down -> cancelFocusChange()
                }
            }
        }
        .focusGroup().padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index == 1) Spacer(Modifier.height(12.dp))
            RailRow(entry.label, entry.key == selected, labelsVisible, labelWidth,
                modifier = Modifier.focusRequester(requesters.getValue(entry.key)),
                onFocused = { focusedKey = entry.key; onChannelFocusChanged(null) },
                onPreview = { if (entry.key != "search") onPreview(entry.key) },
                onSelect = { onSelect(entry.key) },
                onDirection = { direction ->
                    when {
                        direction == AndroidKeyEvent.KEYCODE_DPAD_UP && index == 0 -> true
                        direction == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            requesters.getValue(entries[index - 1].key).requestFocus(); true
                        }
                        direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN && index < entries.lastIndex -> {
                            requesters.getValue(entries[index + 1].key).requestFocus(); true
                        }
                        direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (channels.isNotEmpty()) focusChannel(channelIds.first()) else requesters.getValue("settings").requestFocus()
                            true
                        }
                        else -> false
                    }
                }) { ink -> RailIcon(entry.key, ink) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).height(1.dp).background(TvColors.Border))
        if (channels.isNotEmpty()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                channels.forEachIndexed { index, channel ->
                    androidx.compose.runtime.key(channel.id) {
                    RailRow(channel.name, selected == "live:${channel.id}", labelsVisible, labelWidth,
                        modifier = Modifier.focusRequester(requesters.getValue("live:${channel.id}")),
                        description = if (channel.live) "${channel.name}, в эфире, ${viewerLabel(channel.viewers)}" else "${channel.name}, офлайн",
                        onFocused = { focusedKey = "live:${channel.id}"; onChannelFocusChanged(channel.id) },
                        onPreview = { onChannelPreview(channel) }, previewDelay = 250, previewKey = channel.live,
                        onSelect = { if (channel.live) onChannelSelect(channel) else onChannelDetails(channel) },
                        onLongSelect = { onChannelDetails(channel) },
                        onDirection = { direction ->
                            when {
                                direction == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                    if (index == 0) requesters.getValue("streams").requestFocus()
                                    else focusChannel(channelIds[index - 1])
                                    true
                                }
                                direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (index == channels.lastIndex) requesters.getValue("settings").requestFocus()
                                    else focusChannel(channelIds[index + 1])
                                    true
                                }
                                else -> false
                            }
                        }) { _ -> RailChannelAvatar(channel) }
                    }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
            if (labelsVisible) Text(when {
                !following.signedIn -> "Войдите в Twitch в настройках"
                following.error != null -> "Каналы пока недоступны"
                following.loading || !following.initialized -> "Загрузка каналов…"
                else -> "Нет подписок"
            }, modifier = Modifier.revealedTextWidth(openWidth - 40.dp), color = TvColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        RailRow("Настройки", selected == "settings", labelsVisible, labelWidth,
            modifier = Modifier.focusRequester(requesters.getValue("settings")),
            onFocused = { focusedKey = "settings"; onChannelFocusChanged(null) }, onPreview = {},
            onSelect = { onSelect("settings") },
            onDirection = { direction -> when (direction) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (channels.isNotEmpty()) focusChannel(channelIds.last()) else requesters.getValue("streams").requestFocus()
                    true
                }
                else -> false
            } }) { ink -> RailIcon("settings", ink) }
    }
    }
}

@Composable
private fun RailRow(label: String, active: Boolean, labelsVisible: Boolean, labelWidth: Dp, modifier: Modifier,
    description: String = label, previewDelay: Long = 120, previewKey: Any? = null, onFocused: () -> Unit,
    onPreview: () -> Unit, onSelect: () -> Unit, onLongSelect: (() -> Unit)? = null,
    onDirection: (Int) -> Boolean, icon: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longActivated by remember { mutableStateOf(false) }
    val preview by rememberUpdatedState(onPreview)
    LaunchedEffect(focused, previewKey) { if (focused) { delay(previewDelay); preview() } }
    val ink = when { focused -> TvColors.Background; active -> TvColors.Link; else -> TvColors.Text }
    Row(modifier.fillMaxWidth().height(42.dp)
        .focusProperties { left = FocusRequester.Cancel }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused() else longActivated = false
        }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (key.action == AndroidKeyEvent.ACTION_DOWN && key.repeatCount == 0) longActivated = false
                    val held = key.isLongPress || key.eventTime - key.downTime >= ViewConfiguration.getLongPressTimeout()
                    // Android's injected/platform long press may flag the repeated DOWN while
                    // the subsequent UP does not retain that flag or the same duration.
                    if (key.action == AndroidKeyEvent.ACTION_DOWN && held && onLongSelect != null) longActivated = true
                    if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) {
                        if (onLongSelect != null && (longActivated || held)) onLongSelect()
                        else onSelect()
                    }
                    if (key.action == AndroidKeyEvent.ACTION_UP) longActivated = false
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) onSelect()
                    true
                }
                else -> key.action == AndroidKeyEvent.ACTION_DOWN && onDirection(key.keyCode)
            }
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button; selected = active; contentDescription = description
            onClick { onSelect(); true }
            if (onLongSelect != null) onLongClick("Открыть канал") { onLongSelect(); true }
        }
        .focusable()
        .background(when { focused -> TvColors.Focus; active -> TvColors.Raised; else -> Color.Transparent }, TvShapes.Control)
        .border(1.dp, if (active && !focused) TvColors.Border else Color.Transparent, TvShapes.Control)
        .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { icon(ink) }
        if (labelsVisible) Text(label, modifier = Modifier.weight(1f).revealedTextWidth(labelWidth), color = ink, fontSize = railLabelSize,
            lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// Lay out the final line once and reveal it at the moving edge. Rewrapping or
// recomputing the ellipsis at every intermediate width makes labels flicker.
private fun Modifier.revealedTextWidth(width: Dp) =
    clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true).width(width)

@Composable
private fun RailChannelAvatar(channel: Channel) {
    Box(Modifier.size(30.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
            Text(channel.name.take(1).uppercase(), color = TvColors.Text, fontSize = 14.sp)
            if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.align(Alignment.BottomEnd).size(7.dp).background(if (channel.live) TvColors.Live else TvColors.Muted, CircleShape)
            .border(1.dp, TvColors.Surface, CircleShape))
    }
}

/** Simple, consistent 24-unit outlined symbols; no extra icon font or remote assets. */
@Composable
private fun RailIcon(kind: String, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val unit = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        when (kind) {
            "recordings" -> {
                drawRoundRect(color, point(3f, 5f), Size(18 * unit, 14 * unit), CornerRadius(2 * unit), style = stroke)
                drawPath(Path().apply { moveTo(10 * unit, 8 * unit); lineTo(16 * unit, 12 * unit); lineTo(10 * unit, 16 * unit); close() }, color, style = stroke)
            }
            "streams" -> {
                drawCircle(color, 9 * unit, center, style = stroke)
                drawPath(Path().apply {
                    moveTo(16 * unit, 8 * unit); lineTo(13.5f * unit, 13.5f * unit)
                    lineTo(8 * unit, 16 * unit); lineTo(10.5f * unit, 10.5f * unit); close()
                }, color, style = stroke)
            }
            "search" -> {
                drawCircle(color, 7 * unit, point(10f, 10f), style = stroke)
                line(15f, 15f, 21f, 21f)
            }
            "categories" -> {
                listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x, y) ->
                    drawRoundRect(color, point(x, y), Size(7 * unit, 7 * unit), CornerRadius(unit), style = stroke)
                }
            }
            "following" -> {
                val heart = Path().apply {
                    moveTo(12 * unit, 21 * unit)
                    cubicTo(9 * unit, 18 * unit, 2 * unit, 13 * unit, 2 * unit, 8 * unit)
                    cubicTo(2 * unit, 2 * unit, 9 * unit, 1 * unit, 12 * unit, 7 * unit)
                    cubicTo(15 * unit, 1 * unit, 22 * unit, 2 * unit, 22 * unit, 8 * unit)
                    cubicTo(22 * unit, 13 * unit, 15 * unit, 18 * unit, 12 * unit, 21 * unit)
                }
                drawPath(heart, color, style = stroke)
            }
            "account" -> {
                drawCircle(color, 4 * unit, point(12f, 7f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(4 * unit, 21 * unit)
                    cubicTo(4 * unit, 11 * unit, 20 * unit, 11 * unit, 20 * unit, 21 * unit)
                }
                drawPath(shoulders, color, style = stroke)
            }
            "settings" -> {
                drawCircle(color, 6.5f * unit, center, style = stroke)
                drawCircle(color, 2.5f * unit, center, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    line(12 + cos(angle).toFloat() * 6.5f, 12 + sin(angle).toFloat() * 6.5f,
                        12 + cos(angle).toFloat() * 9f, 12 + sin(angle).toFloat() * 9f)
                }
            }
            "about" -> {
                drawCircle(color, 9 * unit, center, style = stroke)
                drawCircle(color, unit, point(12f, 7f))
                line(12f, 11f, 12f, 17f)
            }
        }
    }
}
