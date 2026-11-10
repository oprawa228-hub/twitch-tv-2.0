package com.oprawa.twitch20.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.data.Video

/** Non-interactive companion to the focused quick channel; Right/OK belongs to the rail. */
@Composable
fun SidebarChannelPreview(
    channel: Channel,
    videoEnabled: Boolean,
    soundEnabled: Boolean = false,
    animationsEnabled: Boolean = true,
    latest: Video? = null,
    resolvePreview: suspend (Channel) -> String,
) {
    val preview = rememberCatalogVideoPreview(channel, videoEnabled, false, resolvePreview, soundEnabled)
    val motion = rememberPreviewMotionEnabled(animationsEnabled)
    var settled by remember(channel.id, preview?.rendered) { mutableStateOf(false) }
    LaunchedEffect(channel.id, preview?.rendered, motion) {
        settled = false
        if (motion && preview?.rendered == true) { delay(2200); settled = true }
    }
    val scale by animateFloatAsState(if (motion && settled) 1.014f else 1f,
        tween(if (motion) 900 else 0), label = "sidebar video")
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().clip(TvShapes.Panel).background(TvColors.Surface)) {
            if (channel.bannerUrl.isNotBlank()) AsyncImage(channel.bannerUrl, null,
                Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
                TvColors.Surface, TvColors.Surface.copy(alpha = .78f), TvColors.Surface.copy(alpha = .48f))))
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
                    Text(channel.name.take(1).uppercase(), color = TvColors.Text, fontSize = 22.sp)
                    if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${channel.login}", color = TvColors.Muted, fontSize = 13.sp)
                }
                if (channel.live) {
                    Box(Modifier.size(8.dp).background(TvColors.Live, CircleShape))
                    Text(viewerLabel(channel.viewers), color = TvColors.Text, fontSize = 14.sp)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(TvShapes.Panel).background(TvColors.Surface)) {
            val art = if (channel.live) channel.imageUrl else latest?.imageUrl.orEmpty().ifBlank { channel.bannerUrl }
            if (art.isNotBlank()) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            preview?.let { CatalogVideoPreview(it, Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
            }, animationsEnabled) }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .96f))))
                .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label = if (channel.live) channel.category else if (latest != null)
                    "Последняя запись${videoDate(latest).takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
                    else "Сейчас не в эфире"
                if (label.isNotBlank()) Text(label, color = TvColors.Link, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val title = if (channel.live) channel.title else latest?.title
                    ?: channel.description.ifBlank { "Откройте архив канала" }
                Text(title, color = TvColors.Text, fontSize = 21.sp, lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun videoDate(video: Video): String =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(video.createdAt)?.destructured?.let { (year, month, day) ->
        "$day.$month.$year"
    }.orEmpty()

@Composable
fun ChannelHeader(channel: Channel) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).clip(TvShapes.Panel)
        .background(TvColors.Surface)) {
        if (channel.bannerUrl.isNotBlank()) AsyncImage(channel.bannerUrl, null,
            Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(TvColors.Surface,
            TvColors.Surface.copy(alpha = .9f), TvColors.Surface.copy(alpha = .65f)))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, null,
                    Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${channel.login}", color = TvColors.Muted, fontSize = 13.sp)
                }
            }
            if (channel.description.isNotBlank()) Text(channel.description, color = TvColors.Text,
                fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (channel.live) "${channel.category}  ·  ${viewerLabel(channel.viewers)}" else "Сейчас не в эфире · записи и клипы ниже",
                color = TvColors.Muted, fontSize = 12.sp)
        }
    }
}
