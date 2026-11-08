package com.oprawa.twitch20.data

/** Stable identities shared by retained pages and the TV grid. */
sealed interface CatalogItem {
    val key: String
    data class Stream(val channel: Channel) : CatalogItem { override val key get() = "channel:${channel.id}" }
    data class Game(val category: Category) : CatalogItem { override val key get() = "category:${category.id}" }
    data class Recording(val video: Video) : CatalogItem { override val key get() = "video:${video.id}" }
}
