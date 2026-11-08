package com.oprawa.twitch20.chat

import org.junit.Assert.*
import org.junit.Test

class ChatClientTest {
    @Test fun parsesEscapedDisplayNameAndMessageInsteadOfProtocolText() {
        val line = "@id=message-1;display-name=Viewer\\sName;color=#AABBCC :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :Hello :there"
        val event = IrcParser.parse(line) as IrcEvent.Message
        assertEquals("Viewer Name", event.value.nickname)
        assertEquals("Hello :there", event.value.text)
        assertEquals("#AABBCC", event.value.color)
    }
    @Test fun pingIsNotDisplayedAsAChatMessage() {
        assertEquals(IrcEvent.Ping("tmi.twitch.tv"), IrcParser.parse("PING :tmi.twitch.tv"))
    }
    @Test fun oversizedChatBodyIsBounded() {
        val event = IrcParser.parse(":nick!nick@host PRIVMSG #channel :" + "x".repeat(900)) as IrcEvent.Message
        assertEquals(500, event.value.text.length)
    }
    @Test fun malformedAndUnrelatedMessagesAreIgnored() {
        assertNull(IrcParser.parse("garbage"))
        assertNull(IrcParser.parse(":tmi.twitch.tv 001 justinfan :Welcome"))
        assertNull(IrcParser.parse(":nick!host PRIVMSG #channel"))
    }
    @Test fun deletedMessageProducesRemovalEvent() {
        val event = IrcParser.parse("@target-msg-id=gone :tmi.twitch.tv CLEARMSG #channel :deleted text")
        assertEquals(IrcEvent.Delete("gone"), event)
    }
    @Test fun invalidColorFallsBackToReadableDefault() {
        val event = IrcParser.parse("@color=javascript:bad :nick!host PRIVMSG #channel :hello") as IrcEvent.Message
        assertEquals("#A6B3C6", event.value.color)
    }

    @Test fun viewerTextCannotTriggerReconnectOrRoomState() {
        val event = IrcParser.parse(":alice!alice@alice.tmi.twitch.tv PRIVMSG #channel :hello RECONNECT please ROOMSTATE #channel")
        assertTrue(event is IrcEvent.Message)
        assertEquals("hello RECONNECT please ROOMSTATE #channel", (event as IrcEvent.Message).value.text)
    }

    @Test fun realServerReconnectAndRoomStateAreProtocolEvents() {
        assertEquals(IrcEvent.Reconnect, IrcParser.parse(":tmi.twitch.tv RECONNECT"))
        assertEquals(IrcEvent.RoomState, IrcParser.parse("@room-id=123 :tmi.twitch.tv ROOMSTATE #channel"))
    }
}
