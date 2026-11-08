package com.oprawa.twitch20.data

/** One catalog response; a null cursor means the service confirmed exhaustion. */
data class CatalogPage<T>(val items: List<T>, val nextCursor: String?)
