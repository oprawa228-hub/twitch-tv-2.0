package com.oprawa.twitch20.ui

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.oprawa.twitch20.chat.ChatMessage
import com.oprawa.twitch20.data.TimedChatMessage
import com.oprawa.twitch20.data.TwitchRepository
import com.oprawa.twitch20.data.replayMessagesAt
import com.oprawa.twitch20.playback.PlaybackSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal data class ReplayChat(val messages: List<ChatMessage>, val status: String)

@Composable
internal fun rememberVodChat(id: String, session: PlaybackSession, repository: TwitchRepository): ReplayChat {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playback by session.state.collectAsStateWithLifecycle()
    var seek by remember(session) { mutableIntStateOf(0) }
    DisposableEffect(session) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    kotlin.math.abs(newPosition.positionMs - oldPosition.positionMs) > 1500) seek++
            }
        }
        session.player.addListener(listener)
        onDispose { session.player.removeListener(listener) }
    }
    var buffer by remember(id, seek) { mutableStateOf<List<TimedChatMessage>>(emptyList()) }
    var status by remember(id, seek) { mutableStateOf("replay_loading") }
    LaunchedEffect(id, seek, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            buffer = emptyList()
            status = "replay_loading"
            var offset = ((session.player.currentPosition - 15_000).coerceAtLeast(0) / 1000).toInt()
            var hasMore = true
            var first = true
            var through = 0L
            while (hasMore) {
                val position = session.player.currentPosition
                if (!first && through > position + 8_000) {
                    delay(300)
                    continue
                }
                val page = try {
                    withTimeoutOrNull(7000) { repository.replayChat(id, offset) }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
                if (page == null) {
                    status = "replay_unavailable"
                    break
                }
                first = false
                hasMore = page.hasMore
                offset = maxOf(offset + 1, (page.throughMs / 1000).toInt())
                through = maxOf(through, page.throughMs)
                buffer = (buffer + page.messages).distinctBy { it.message.id }.sortedBy { it.offsetMs }
                    .filter { it.offsetMs >= position - 60_000 }.takeLast(1200)
                status = if (buffer.isEmpty() && !hasMore) "replay_empty" else ""
                delay(250)
            }
        }
    }
    val shown = remember(playback.positionMs, buffer) {
        replayMessagesAt(buffer, playback.positionMs)
    }
    return ReplayChat(shown, status)
}
