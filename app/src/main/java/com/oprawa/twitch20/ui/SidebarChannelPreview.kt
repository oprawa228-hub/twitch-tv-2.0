package com.oprawa.twitch20.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    resolvePreview: suspend (Channel) -> String,
) {
    val preview = rememberCatalogVideoPreview(channel, videoEnabled, false, resolvePreview)
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
                    Box(Modifier.size(6.dp).background(TvColors.Live, CircleShape))
                    Text(listOf(channel.category, "${channel.viewers} зрителей").filter(String::isNotBlank).joinToString(" · "),
                        color = TvColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(TvShapes.Panel).background(TvColors.Surface)) {
            AsyncImage(channel.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            preview?.let { CatalogVideoPreview(it, Modifier.fillMaxSize()) }
        }
        Text(channel.title, color = TvColors.Text, fontSize = 17.sp, lineHeight = 23.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
