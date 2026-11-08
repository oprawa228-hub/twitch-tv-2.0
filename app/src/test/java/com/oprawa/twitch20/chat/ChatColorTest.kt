package com.oprawa.twitch20.chat

import org.junit.Assert.*
import org.junit.Test

class ChatColorTest {
    @Test fun darkNamesRemainReadableOnTheOpaqueChatSurface() {
        listOf("#000000", "#000080", "#230038", "#008000", "#444444").forEach {
            assertTrue(it, ChatColor.contrast(ChatColor.readable(it), ChatColor.background) >= 4.5)
        }
    }
    @Test fun alreadyReadableColoursStayUnchanged() {
        assertEquals(0xFFFFCC00.toInt(), ChatColor.readable("#ffcc00"))
        assertEquals(0xFFFFFFFF.toInt(), ChatColor.readable("#ffffff"))
    }
    @Test fun invalidAndTransparentInputCannotHideNicknames() {
        listOf("", "transparent", "#00000000", "#GGGGGG").forEach {
            assertEquals(0xFFC6A3FF.toInt(), ChatColor.readable(it))
        }
    }
}
