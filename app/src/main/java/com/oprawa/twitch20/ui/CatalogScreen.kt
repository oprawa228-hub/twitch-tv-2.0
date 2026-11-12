package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Category
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.data.SearchCatalogType
import com.oprawa.twitch20.data.Video

private enum class CatalogKind(val searchType: SearchCatalogType) {
    Channel(SearchCatalogType.Channels), Category(SearchCatalogType.Categories), Video(SearchCatalogType.Recordings),
}

private data class CatalogEntry(
    val key: String,
    val name: String,
    val description: String,
    val category: String,
    val image: String,
    val badge: String,
    val open: () -> Unit,
    val details: (() -> Unit)? = null,
    val kind: CatalogKind = CatalogKind.Channel,
    val typeLabel: String = "",
    val avatar: String = "",
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
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    categoryArt: String = "",
    searchQuery: String? = null,
    searchFilter: SearchFilter = SearchFilter.All,
    animationsEnabled: Boolean = true,
    loadLatestVideo: (suspend (String) -> Video?)? = null,
    limitReached: Boolean = false,
    guestLimited: Boolean = false,
    revision: Long = 0,
    allowFocusRestore: Boolean = true,
    previewEnabled: Boolean = true,
    previewSoundEnabled: Boolean = false,
    resolvePreview: (suspend (Channel) -> String)? = null,
    searchFailures: Set<SearchCatalogType> = emptySet(),
    onSearchRetry: () -> Unit = onRetry,
    searchFallbackFocus: FocusRequester? = null,
) {
    val mayRestoreFocus by rememberUpdatedState(allowFocusRestore)
    val motion = rememberPreviewMotionEnabled(animationsEnabled)
    val allEntries = buildList {
        channels.forEach { channel ->
            add(CatalogEntry("channel:${channel.id}", channel.name, if (channel.live) channel.title else "Записи и клипы канала",
                channel.category, channel.imageUrl,
                if (channel.live) viewerLabel(channel.viewers) else "Не в эфире",
                { onChannel(channel) }, { onChannelDetails(channel) },
                typeLabel = if (channel.live) "В ЭФИРЕ" else "", avatar = channel.avatarUrl))
        }
        categories.forEach { category ->
            add(CatalogEntry("category:${category.id}", category.name, "", "Категория",
                category.imageUrl, "", { onCategory(category) }, kind = CatalogKind.Category))
        }
        videos.forEach { video ->
            add(CatalogEntry("video:${video.id}", video.title, video.channelLogin,
                "", video.imageUrl, formatDuration(video.durationSeconds.toLong()),
                { onVideo(video) }, kind = CatalogKind.Video,
                typeLabel = if (video.kind == "clip") "Клип" else "Запись"))
        }
    }
    val showSections = searchQuery != null && searchFilter == SearchFilter.All
    val selectedKind = when (searchFilter) {
        SearchFilter.All -> null
        SearchFilter.Channels -> CatalogKind.Channel
        SearchFilter.Categories -> CatalogKind.Category
        SearchFilter.Recordings -> CatalogKind.Video
    }
    val visibleFailures = if (searchQuery.isNullOrBlank()) emptyList() else {
        searchFailures.filter { selectedKind == null || it == selectedKind.searchType }
    }
    val searchError = when (visibleFailures.size) {
        0 -> null
        SearchCatalogType.entries.size -> "Не удалось загрузить результаты поиска"
        else -> "Не удалось загрузить " + visibleFailures.joinToString(", ") {
            when (it) {
                SearchCatalogType.Channels -> "каналы"
                SearchCatalogType.Categories -> "категории"
                SearchCatalogType.Recordings -> "записи"
            }
        }
    }
    val entries = if (searchQuery != null && selectedKind != null)
        allEntries.filter { it.kind == selectedKind } else allEntries
    val keys = entries.map { it.key }
    val state = rememberLazyGridState()
    // The expanding rail remeasures card heights while this catalog is unfocused.
    // Keep the last browsing viewport separately from LazyGrid's live scroll anchor.
    var suspendedIndex by rememberSaveable { mutableStateOf(-1) }
    var suspendedOffset by rememberSaveable { mutableStateOf(0) }
    var previousFilter by rememberSaveable { mutableStateOf(searchFilter) }
    LaunchedEffect(searchFilter) {
        if (searchQuery != null && previousFilter != searchFilter) {
            previousFilter = searchFilter
            suspendedIndex = -1
            state.requestScrollToItem(0)
        }
    }
    var gridHasFocus by remember { mutableStateOf(false) }
    val requesterCache = remember { mutableMapOf<String, FocusRequester>() }
    val requesters = remember(keys) {
        requesterCache.apply { keys.forEach { getOrPut(it) { FocusRequester() } } }
    }
    val emptyRequester = remember { FocusRequester() }
    val searchRetryVisible by rememberUpdatedState(searchError != null)
    var searchRetryFocused by remember { mutableStateOf(false) }
    val searchRetryFocus = Modifier.onFocusChanged {
        // Retain the final focus state when the successful response removes this action.
        if (it.isFocused || searchRetryVisible) searchRetryFocused = it.isFocused
    }
    val retrySearch = { if (!busy) onSearchRetry() }
    var restored by remember(revision) { mutableStateOf(false) }
    var focusedEntryKey by remember { mutableStateOf<String?>(null) }
    val focusedChannel = channels.firstOrNull { "channel:${it.id}" == focusedEntryKey }
    var latest by remember(focusedChannel?.id) { mutableStateOf<Video?>(null) }
    LaunchedEffect(focusedChannel?.id, loadLatestVideo != null, previewEnabled) {
        val login = focusedChannel?.takeUnless { it.live }?.login ?: return@LaunchedEffect
        if (!previewEnabled) return@LaunchedEffect
        kotlinx.coroutines.delay(650)
        latest = try { loadLatestVideo?.invoke(login) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { null }
    }
    val preview = rememberCatalogVideoPreview(focusedChannel, previewEnabled,
        state.isScrollInProgress, resolvePreview, previewSoundEnabled)

    LaunchedEffect(gridHasFocus) {
        if (gridHasFocus && suspendedIndex >= 0) {
            // Requesting a card first moves focus out of the rail. Its collapsed width
            // must be measured before restoring the viewport, otherwise bring-into-view
            // retains an offset calculated for the narrower preview grid.
            withFrameNanos { }
            withFrameNanos { }
            state.scrollToItem(suspendedIndex, suspendedOffset)
            suspendedIndex = -1
        }
    }

    LaunchedEffect(selectedKey, entries.size, hasMore, busy, error) {
        if (hasMore && !busy && error == null && entries.isNotEmpty() &&
            keys.indexOf(selectedKey) >= (entries.size - 6).coerceAtLeast(0)) onLoadMore()
    }

    LaunchedEffect(keys, revision, searchError != null) {
        // A background refresh must not steal focus from the navigation rail.
        val restoreSearchFocus = searchError == null && searchRetryFocused
        if (restoreSearchFocus) searchRetryFocused = false
        if (entries.isNotEmpty() && (!restored || restoreSearchFocus)) {
            val index = keys.indexOf(selectedKey).coerceAtLeast(0)
            withFrameNanos { }
            if (state.layoutInfo.visibleItemsInfo.none { it.key == keys[index] }) state.scrollToItem(if (showSections) index + entries[index].kind.ordinal + 1 else index)
            // The target must be composed after scrolling before focus can be restored.
            withFrameNanos { }
            withFrameNanos { }
            if (mayRestoreFocus) requesters[keys[index]]?.requestFocus()
            restored = true
        } else if (restoreSearchFocus && mayRestoreFocus) {
            withFrameNanos { }
            searchFallbackFocus?.requestFocus()
        }
    }
    // OK on an already previewed rail section must enter its restored card as well.
    LaunchedEffect(allowFocusRestore) {
        if (searchQuery == null && allowFocusRestore && restored && keys.isNotEmpty()) {
            withFrameNanos { }
            val index = keys.indexOf(selectedKey).coerceAtLeast(0)
            requesters[keys[index]]?.requestFocus()
        }
    }
    LaunchedEffect(busy, error, searchError, entries.isEmpty(), allowFocusRestore) {
        if (!busy && entries.isEmpty() && (searchQuery == null || error != null || searchError != null)) {
            withFrameNanos { }
            if (mayRestoreFocus) emptyRequester.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 24.dp)) {
        if (searchQuery == null) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp, end = 84.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (categoryArt.isNotBlank()) {
                    AsyncImage(categoryArt, null, Modifier.size(42.dp, 56.dp).clip(TvShapes.Control),
                        contentScale = ContentScale.Crop)
                    Spacer(Modifier.size(14.dp))
                }
                Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    color = TvColors.Text, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (busy) Text("Обновление…", fontSize = 12.sp, color = TvColors.Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        val displayError = searchError ?: error
        if (displayError != null && entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(displayError, Modifier.weight(1f), color = TvColors.Error, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (searchError != null) {
                    TvAction(if (busy) "Повторяем…" else "Повторить", retrySearch, searchRetryFocus)
                } else TvAction("Повторить", onRetry)
            }
        }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    searchQuery?.isBlank() == true -> "Введите запрос или воспользуйтесь микрофоном"
                    searchError != null -> searchError
                    busy -> "Загружаем…"
                    error != null -> error
                    searchQuery != null -> searchFilter.emptyMessage
                    hasMore -> "В этой части списка записей нет"; else -> "Здесь пока ничего нет" },
                    color = if (displayError == null) TvColors.Muted else TvColors.Error,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp).then(
                        if (searchQuery == null && !busy && error == null && !hasMore) Modifier.focusRequester(emptyRequester).focusable() else Modifier), maxLines = 4)
                if (searchError != null) {
                    Spacer(Modifier.height(20.dp))
                    TvAction(if (busy) "Повторяем…" else "Повторить", retrySearch,
                        Modifier.focusRequester(emptyRequester).then(searchRetryFocus))
                } else if (!busy && (error != null || hasMore)) {
                    Spacer(Modifier.height(20.dp))
                    TvAction(if (error != null) "Повторить" else "Искать дальше",
                        if (error == null && hasMore) onLoadMore else onRetry,
                        Modifier.focusRequester(emptyRequester))
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).padding(bottom = 20.dp)) {
            val posterGrid = entries.all { it.kind == CatalogKind.Category }
            val channelGrid = entries.all { it.kind == CatalogKind.Channel }
            val largeText = LocalDensity.current.fontScale > 1.25f
            val columns = if (posterGrid) { if (largeText) 4 else 5 } else if (largeText) 2 else 3
            val thumbnailWidth = (maxWidth - 8.dp - 16.dp * (columns - 1)) / columns - 6.dp
            val naturalImageHeight = thumbnailWidth * if (posterGrid) 4f / 3f else 9f / 16f
            val channelMetadataHeight = (62f * LocalDensity.current.fontScale).dp + 14.dp
            // Two complete channel rows on the main TV canvas; denser search pages may scroll.
            // Fit the whole 16:9 image if this trims a few dp from the available image slot.
            val imageHeight = if (channelGrid && !largeText && maxHeight >= 400.dp)
                minOf(naturalImageHeight, ((maxHeight - 24.dp) / 2 - channelMetadataHeight).coerceAtLeast(104.dp))
                else naturalImageHeight
            LazyVerticalGrid(columns = GridCells.Fixed(columns), state = state,
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()
                    .onFocusChanged { focus ->
                        if (gridHasFocus && !focus.hasFocus) {
                            suspendedIndex = state.firstVisibleItemIndex
                            suspendedOffset = state.firstVisibleItemScrollOffset
                        }
                        gridHasFocus = focus.hasFocus
                    }.focusGroup()) {
                val sections = if (showSections) CatalogKind.entries.map { kind -> entries.filter { it.kind == kind } }
                    else listOf(entries)
                sections.forEachIndexed { index, section ->
                    if (showSections) item(key = "section:$index", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(listOf("Каналы", "Категории", "Записи")[index], color = TvColors.Text,
                                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            if (CatalogKind.entries[index].searchType in searchFailures) {
                                Text("Не удалось загрузить", color = TvColors.Error, fontSize = 14.sp)
                            } else if (section.isEmpty()) Text("Нет результатов", color = TvColors.Muted, fontSize = 14.sp)
                            else if (index == 2) Text("Записи Twitch по вашему запросу", color = TvColors.Muted, fontSize = 13.sp)
                        }
                    }
                    items(section, key = { it.key }) { entry ->
                        val archive = latest?.takeIf { entry.key == focusedEntryKey && previewEnabled }
                        val card = archive?.let { entry.copy(
                            image = it.imageUrl, description = it.title,
                            category = videoDate(it).ifBlank { "Последняя запись" }) } ?: entry
                        CatalogCard(card, imageHeight, requesters.getValue(entry.key), onFocused,
                            preview?.takeIf { "channel:${it.channelId}" == entry.key }, motion) { hasFocus ->
                            if (hasFocus) focusedEntryKey = entry.key
                            else if (focusedEntryKey == entry.key) focusedEntryKey = null
                        }
                    }
                }
                item(key = "catalog-footer", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val note = when {
                            limitReached -> "Показаны первые 1000 элементов. Выберите категорию или уточните поиск."
                            guestLimited -> "В гостевом режиме доступна первая страница. Полный каталог — после входа в Twitch."
                            else -> null
                        }
                        note?.let { Text(it, color = TvColors.Muted, fontSize = 14.sp) }
                        if (hasMore && error == null && !busy) TvAction("Загрузить ещё", onLoadMore)

                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CatalogCard(entry: CatalogEntry, imageHeight: Dp, requester: FocusRequester,
    onFocused: (String) -> Unit, preview: CatalogPreview?, animationsEnabled: Boolean, onFocusState: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longActivated by remember { mutableStateOf(false) }
    val shape = TvShapes.Control
    val animate = animationsEnabled
    val scale by animateFloatAsState(if (animate && focused && entry.kind == CatalogKind.Category) 1.025f else 1f,
        tween(if (animate) 170 else 0), label = "category focus")
    Column(Modifier.fillMaxWidth()
        .focusRequester(requester)
        .onFocusChanged {
            focused = it.isFocused
            onFocusState(it.isFocused)
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
            contentDescription = listOf(entry.name, entry.description, entry.category, entry.typeLabel, entry.badge)
                .filter { it.isNotBlank() }.joinToString(", ")
            onClick { entry.open(); true }
            if (entry.details != null) onLongClick("Открыть канал") { entry.details.invoke(); true }
        }
        .focusable()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .background(if (focused) TvColors.Raised else TvColors.Surface, shape)
        .border(2.dp, if (focused) TvColors.Focus else TvColors.Background, shape)
        .padding(3.dp)) {
        Box(Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(4.dp))
            .background(TvColors.Surface)) {
            // A real image may be unavailable; keep a quiet stable slot, never a fake stream image.
            Text(entry.name.take(2).uppercase(), modifier = Modifier.align(Alignment.Center),
                fontSize = 28.sp, color = TvColors.Muted)
            if (entry.kind == CatalogKind.Channel && entry.typeLabel != "В ЭФИРЕ" && entry.avatar.isNotBlank() && entry.image.isBlank())
                AsyncImage(entry.avatar, null, Modifier.align(Alignment.Center).size(78.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop)
            else AsyncImage(model = entry.image, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (preview != null) CatalogVideoPreview(preview, Modifier.fillMaxSize(), animationsEnabled)
            if (entry.typeLabel.isNotBlank() && entry.typeLabel != "В ЭФИРЕ") Text(entry.typeLabel,
                Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(TvColors.Background.copy(alpha = .9f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = TvColors.Text,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (entry.badge.isNotBlank()) Row(
                Modifier.align(if (entry.kind == CatalogKind.Video) Alignment.BottomEnd else Alignment.BottomStart).padding(6.dp)
                    .background(TvColors.Background.copy(alpha = .9f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (entry.typeLabel == "В ЭФИРЕ") Box(Modifier.size(6.dp).background(TvColors.Live, CircleShape))
                Text(entry.badge, color = TvColors.Text, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
            when (entry.kind) {
                CatalogKind.Channel -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (entry.avatar.isNotBlank()) AsyncImage(entry.avatar, null,
                            Modifier.size(26.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Text(entry.name, color = TvColors.Text, fontSize = 15.sp, lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(entry.description, color = TvColors.Text, fontSize = 12.sp,
                        lineHeight = 15.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.category, color = TvColors.Muted, fontSize = 11.sp, lineHeight = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CatalogKind.Video -> {
                    Text(entry.name, color = TvColors.Text, fontSize = 14.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.description, color = TvColors.Muted, fontSize = 12.sp, lineHeight = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CatalogKind.Category -> Text(entry.name, color = TvColors.Text, fontSize = 14.sp, lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
