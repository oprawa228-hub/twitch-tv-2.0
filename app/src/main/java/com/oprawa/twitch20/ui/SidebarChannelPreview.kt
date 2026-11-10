package com.oprawa.twitch20.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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

/** Non-interactive companion to the focused quick channel; Right/OK belongs to the rail. */
@Composable
fun SidebarChannelPreview(
    channel: Channel,
    videoEnabled: Boolean,
    soundEnabled: Boolean = false,
    resolvePreview: suspend (Channel) -> String,
) {
    val preview = rememberCatalogVideoPreview(channel, videoEnabled, false, resolvePreview, soundEnabled)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
                Text(channel.name.take(1).uppercase(), color = TvColors.Text, fontSize = 22.sp)
                if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(channel.name, color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@${channel.login}",
                        color = TvColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(TvShapes.Panel).background(TvColors.Surface)) {
            AsyncImage(if (channel.live) channel.imageUrl else channel.bannerUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            preview?.let { CatalogVideoPreview(it, Modifier.fillMaxSize()) }
            if (!channel.live) Column(Modifier.align(Alignment.Center).padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сейчас не в эфире", color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Откройте канал, чтобы посмотреть записи и клипы", color = TvColors.Muted, fontSize = 16.sp)
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .94f))))
                .padding(start = 22.dp, end = 22.dp, top = 42.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (channel.live) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(TvColors.Live, CircleShape))
                        Text(viewerLabel(channel.viewers), color = TvColors.Text, fontSize = 13.sp)
                        if (channel.category.isNotBlank()) Text("·  ${channel.category}", color = TvColors.Muted,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(channel.title, color = TvColors.Text, fontSize = 21.sp, lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else if (channel.description.isNotBlank()) Text(channel.description, color = TvColors.Text,
                    fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

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
