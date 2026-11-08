package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Category
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.data.Video

private data class CatalogEntry(
    val key: String,
    val name: String,
    val description: String,
    val category: String,
    val image: String,
    val badge: String,
    val open: () -> Unit,
    val details: (() -> Unit)? = null,
)

/** Production content only. The host owns navigation and stores selectedKey per route. */
@Composable
fun CatalogScreen(
    title: String,
    channels: List<Channel>,
    categories: List<Category> = emptyList(),
    videos: List<Video> = emptyList(),
    busy: Boolean,
    error: String?,
    selectedKey: String?,
    onChannel: (Channel) -> Unit,
    onCategory: (Category) -> Unit,
    onVideo: (Video) -> Unit,
    onRetry: () -> Unit,
    onFocused: (String) -> Unit,
    onChannelDetails: (Channel) -> Unit = onChannel,
) {
    val entries = buildList {
        channels.forEach { channel ->
            add(CatalogEntry("channel:${channel.id}", channel.name, channel.title,
                channel.category, channel.imageUrl,
                if (channel.live) "● ${channel.viewers}" else "Не в эфире",
                { onChannel(channel) }, { onChannelDetails(channel) }))
        }
        categories.forEach { category ->
            add(CatalogEntry("category:${category.id}", category.name, "", "Категория",
                category.imageUrl, "", { onCategory(category) }))
        }
        videos.forEach { video ->
            add(CatalogEntry("video:${video.id}", video.title, video.channelLogin,
                "${video.kind}", video.imageUrl, formatDuration(video.durationSeconds.toLong()),
                { onVideo(video) }))
        }
    }
    val keys = entries.map { it.key }
    val state = rememberLazyGridState()
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    val emptyRequester = remember { FocusRequester() }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(keys) {
        // A background refresh must not steal focus from the navigation rail.
        if (entries.isNotEmpty() && !restored) {
            val index = keys.indexOf(selectedKey).coerceAtLeast(0)
            withFrameNanos { }
            if (state.layoutInfo.visibleItemsInfo.none { it.key == keys[index] }) state.scrollToItem(index)
            // The target must be composed after scrolling before focus can be restored.
            withFrameNanos { }
            withFrameNanos { }
            requesters[keys[index]]?.requestFocus()
            restored = true
        }
    }
    LaunchedEffect(busy, error, entries.isEmpty()) {
        if (!busy && entries.isEmpty()) {
            withFrameNanos { }
            emptyRequester.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                color = TvColors.Text, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (busy) Text("Обновление…", fontSize = 12.sp, color = TvColors.Muted)
        }
        if (error != null && entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(error, Modifier.weight(1f), color = TvColors.Error, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                TvAction("Повторить", onRetry)
            }
        }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when { busy -> "Загружаем каналы…"; error != null -> error; else -> "Здесь пока ничего нет" },
                    color = if (error == null) TvColors.Muted else TvColors.Error,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp), maxLines = 4)
                if (!busy) {
                    Spacer(Modifier.height(20.dp))
                    TvAction("Повторить", onRetry, Modifier.focusRequester(emptyRequester))
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), state = state,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { entry ->
                    CatalogCard(entry, requesters.getValue(entry.key), onFocused)
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(entry: CatalogEntry, requester: FocusRequester, onFocused: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longActivated by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.fillMaxWidth()
        .focusRequester(requester)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused(entry.key) else longActivated = false
        }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode !in setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)) false
            else {
                if (key.action == AndroidKeyEvent.ACTION_DOWN && key.repeatCount == 0) longActivated = false
                val held = key.isLongPress || key.eventTime - key.downTime >= ViewConfiguration.getLongPressTimeout()
                if (key.action == AndroidKeyEvent.ACTION_DOWN && held && !longActivated && entry.details != null) {
                    longActivated = true
                }
                if (key.action == AndroidKeyEvent.ACTION_UP) {
                    if (!key.isCanceled) {
                        if ((longActivated || held) && entry.details != null) entry.details.invoke() else entry.open()
                    }
                    longActivated = false
                }
                true
            }
        }
        .semantics {
            role = Role.Button
            contentDescription = listOf(entry.name, entry.description, entry.category, entry.badge)
                .filter { it.isNotBlank() }.joinToString(", ")
            onClick { entry.open(); true }
            if (entry.details != null) onLongClick("Открыть канал") { entry.details.invoke(); true }
        }
        .focusable()
        .background(if (focused) TvColors.Raised else TvColors.Background, shape)
        .border(2.dp, if (focused) TvColors.Focus else TvColors.Background, shape)
        .padding(3.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp))
            .background(TvColors.Surface)) {
            // A real image may be unavailable; keep a quiet stable slot, never a fake stream image.
            Text(entry.name.take(2).uppercase(), modifier = Modifier.align(Alignment.Center),
                fontSize = 28.sp, color = TvColors.Muted)
            AsyncImage(model = entry.image, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (entry.badge.isNotBlank()) Text(entry.badge,
                Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(TvColors.Background.copy(alpha = .9f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = TvColors.Text, fontSize = 11.sp, maxLines = 1)
        }
        Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
            Text(entry.name, color = TvColors.Text, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.description, color = TvColors.Text, fontSize = 12.sp,
                lineHeight = 15.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.category, color = TvColors.Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
