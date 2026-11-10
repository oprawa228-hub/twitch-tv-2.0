package com.oprawa.twitch20.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.data.FollowingMonitor
import com.oprawa.twitch20.data.notificationClockMs
import kotlinx.coroutines.delay

/** The real foreground notification and the settings illustration share this component. */
@Composable
fun LiveNotificationBanner(channel: Channel, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 420.dp).fillMaxWidth()
        .background(TvColors.Surface, RoundedCornerShape(12.dp))
        .border(1.dp, TvColors.Link.copy(alpha = .6f), RoundedCornerShape(12.dp))
        .semantics { liveRegion = LiveRegionMode.Polite }
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
            if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(channel.name.take(2).uppercase(), color = TvColors.Link, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(channel.name, color = TvColors.Text, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.liveMessage.ifBlank { "Начал трансляцию" }, color = TvColors.Text,
                fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (channel.category.isNotBlank()) Text(channel.category, color = TvColors.Muted,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Never requests focus, interrupts playback or shows queued stale starts on returning to TV. */
@Composable
fun FollowNotifications(
    monitor: FollowingMonitor,
    enabled: Boolean,
    blocked: Boolean,
    currentLogin: String?,
    modifier: Modifier = Modifier,
    eligible: Boolean = true,
    soundEnabled: Boolean = false,
    animationsEnabled: Boolean = true,
    loadLiveMessage: suspend (String) -> String = { "" },
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isBlocked by rememberUpdatedState(blocked)
    val watching by rememberUpdatedState(currentLogin)
    val messageLoader by rememberUpdatedState(loadLiveMessage)
    var shown by remember(monitor, eligible, enabled) { mutableStateOf<Channel?>(null) }
    var soundedFor by remember(monitor, eligible, enabled) { mutableStateOf<Channel?>(null) }
    SideEffect {
        // Opening the announced channel dismisses this event permanently, not just its drawing.
        // Otherwise leaving that channel during the display interval would reveal the old banner again.
        if (shown?.login?.equals(currentLogin, ignoreCase = true) == true) shown = null
    }
    LaunchedEffect(monitor, enabled, eligible, lifecycle) {
        if (!enabled || !eligible) return@LaunchedEffect
        monitor.starts.collect { event ->
            fun foreground() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            fun fresh() = notificationClockMs() - event.observedAtMs < 20_000
            fun watchingAnnouncedChannel() = event.channel.login.equals(watching, ignoreCase = true)
            suspend fun waitUntilUnblocked() {
                while ((isBlocked || !foreground()) && fresh() && !watchingAnnouncedChannel()) delay(100)
            }
            // Compose can suspend its frame clock on STOP before UpdatedState sees blocked=true.
            // Read Lifecycle synchronously so a late source completion cannot consume the visible interval.
            waitUntilUnblocked()
            if (!fresh() || watchingAnnouncedChannel()) return@collect
            val channel = event.channel
            val message = channel.liveMessage.ifBlank { messageLoader(channel.login) }
            waitUntilUnblocked()
            if (!fresh() || watchingAnnouncedChannel()) return@collect
            val announcement = channel.copy(liveMessage = message)
            shown = announcement
            val visibleUntil = notificationClockMs() + 5_000
            while (shown === announcement && notificationClockMs() < visibleUntil) {
                delay((visibleUntil - notificationClockMs()).coerceIn(1L, 100L))
            }
            if (shown === announcement) shown = null
        }
    }
    if (enabled && eligible && !blocked) {
        val visible = shown?.takeUnless { it.login.equals(currentLogin, ignoreCase = true) }
        LaunchedEffect(visible) {
            if (visible != null && soundedFor !== visible) {
                soundedFor = visible
                if (soundEnabled) playLiveNotificationSound()
            }
        }
        if (animationsEnabled) {
            var previous by remember(monitor) { mutableStateOf<Channel?>(null) }
            if (visible != null) SideEffect { previous = visible }
            val content = visible ?: previous?.takeUnless { it.login.equals(currentLogin, ignoreCase = true) }
            AnimatedVisibility(visible != null, modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                content?.let { LiveNotificationBanner(it) }
            }
        } else visible?.let { LiveNotificationBanner(it, modifier) }
    }
}
