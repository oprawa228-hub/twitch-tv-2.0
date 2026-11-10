package com.oprawa.twitch20.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Twitch's own global emote; decoration has no remote-control focus or spoken label. */
@Composable
internal fun DinoDecoration(animationsEnabled: Boolean) {
    val moving = rememberPreviewMotionEnabled(animationsEnabled)
    val format = if (moving) "animated" else "static"
    val image = "https://static-cdn.jtvnw.net/emoticons/v2/emotesv2_dcd06b30a5c24f6eb871e8f5edbd44f7/$format/dark/2.0"
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }.padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(image, null, Modifier.size(36.dp))
        AsyncImage(image, null, Modifier.size(36.dp))
    }
}
