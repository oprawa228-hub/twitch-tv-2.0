package com.oprawa.twitch20.ui

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
            Text("${channel.name} в эфире", color = TvColors.Text, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.title.ifBlank { "Начал трансляцию" }, color = TvColors.Text,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.category.isNotBlank()) Text(channel.category, color = TvColors.Muted,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Never requests focus, interrupts playback or shows queued stale starts on returning to TV. */
@Composable
fun FollowNotifications(monitor: FollowingMonitor, enabled: Boolean, blocked: Boolean,
    currentLogin: String?, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isBlocked by rememberUpdatedState(blocked)
    val watching by rememberUpdatedState(currentLogin)
    var shown by remember(monitor) { mutableStateOf<Channel?>(null) }
    SideEffect {
        // Opening the announced channel dismisses this event permanently, not just its drawing.
        // Otherwise leaving that channel inside 4.5 seconds would reveal the old banner again.
        if (shown?.login?.equals(currentLogin, ignoreCase = true) == true) shown = null
    }
    LaunchedEffect(monitor, enabled, lifecycle) {
        shown = null
        if (!enabled) return@LaunchedEffect
        monitor.starts.collect { event ->
            fun foreground() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event.channel.login.equals(watching, ignoreCase = true)) {
                return@collect
            }
            // Compose can suspend its frame clock on STOP before UpdatedState sees blocked=true.
            // Read Lifecycle synchronously so a late source completion cannot consume the visible interval.
            while ((isBlocked || !foreground()) && notificationClockMs() - event.observedAtMs < 20_000) delay(100)
            if (!isBlocked && foreground() && notificationClockMs() - event.observedAtMs < 20_000 &&
                !event.channel.login.equals(watching, ignoreCase = true)) {
                shown = event.channel
                val visibleUntil = notificationClockMs() + 4_500
                while (shown === event.channel && notificationClockMs() < visibleUntil) {
                    delay((visibleUntil - notificationClockMs()).coerceIn(1L, 100L))
                }
                if (shown === event.channel) shown = null
            }
        }
    }
    if (enabled && !blocked) shown?.takeUnless { it.login.equals(currentLogin, ignoreCase = true) }
        ?.let { LiveNotificationBanner(it, modifier) }
}
