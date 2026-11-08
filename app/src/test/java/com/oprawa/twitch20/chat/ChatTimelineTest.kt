package com.oprawa.twitch20.chat

import org.junit.Assert.*
import org.junit.Test

class ChatTimelineTest {
    @Test fun retainsAuthorIdAndLoginSeparatelyFromLocalizedDisplayName() {
        val event = IrcParser.parse("@id=m1;user-id=42;display-name=Зритель;color=#AABBCC :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #room :hello") as IrcEvent.Message
        assertEquals("Зритель", event.value.nickname)
        assertEquals("viewer", event.value.login)
        assertEquals("42", event.value.userId)
    }

    // Timeout carries login/ID, not the localized name shown on screen.
    @Test fun timeoutRemovesTargetIdWithoutRemovingAnotherAuthorsDisplayNameMatch() {
        val messages = listOf(message("a", "Зритель", "viewer", "42"), message("b", "viewer", "other", "99"))
        val clear = IrcParser.parse("@ban-duration=60;target-user-id=42 :tmi.twitch.tv CLEARCHAT #room :viewer")
        val remaining = ChatTimeline.apply(messages, clear)
        assertEquals(listOf("b"), remaining.map { it.id })
    }

    @Test fun clearFallsBackToLoginWhenUserIdIsAbsent() {
        val messages = listOf(message("a", "Зритель", "Viewer", null), message("b", "Other", "other", null))
        val clear = IrcParser.parse(":tmi.twitch.tv CLEARCHAT #room :viewer")
        assertEquals(listOf("b"), ChatTimeline.apply(messages, clear).map { it.id })
    }

    @Test fun userIdClearWithoutTrailingLoginDoesNotClearTheWholeRoom() {
        val messages = listOf(message("a", "A", "a", "42"), message("b", "B", "b", "99"))
        val clear = IrcParser.parse("@target-user-id=42 :tmi.twitch.tv CLEARCHAT #room")
        assertEquals(listOf("b"), ChatTimeline.apply(messages, clear).map { it.id })
    }

    @Test fun fullClearAndSingleMessageDeleteKeepTheirDistinctMeanings() {
        val messages = listOf(message("a", "A", "a", "1"), message("b", "B", "b", "2"))
        val delete = IrcParser.parse("@target-msg-id=a :tmi.twitch.tv CLEARMSG #room :removed")
        assertEquals(listOf("b"), ChatTimeline.apply(messages, delete).map { it.id })
        assertTrue(ChatTimeline.apply(messages, IrcParser.parse(":tmi.twitch.tv CLEARCHAT #room")).isEmpty())
    }

    // A subscriber's actual message belongs in the chat; the system announcement does not.
    @Test fun userNoticePreservesRealTextAndAuthorInsteadOfServerPrefix() {
        val event = IrcParser.parse("@id=notice-1;msg-id=resub;user-id=42;login=viewer;display-name=Зритель;system-msg=System\\sannouncement :tmi.twitch.tv USERNOTICE #room :Спасибо за эфир")
        assertTrue(event is IrcEvent.Message)
        val value = (event as IrcEvent.Message).value
        assertEquals("notice-1", value.id)
        assertEquals("Зритель", value.nickname)
        assertEquals("viewer", value.login)
        assertEquals("42", value.userId)
        assertEquals("Спасибо за эфир", value.text)
    }

    @Test fun userNoticeWithoutUserTextDoesNotInventASyntheticMessage() {
        assertNull(IrcParser.parse("@id=notice-2;msg-id=subgift;login=viewer;system-msg=Gift\\ssubscription :tmi.twitch.tv USERNOTICE #room"))
        assertNull(IrcParser.parse("@id=notice-3;msg-id=resub;login=viewer;system-msg=Subscription :tmi.twitch.tv USERNOTICE #room :   "))
    }

    @Test fun userNoticeUsesLoginFallbackAndBoundsActionText() {
        val event = IrcParser.parse("@id=n;login=viewer;display-name= :tmi.twitch.tv USERNOTICE #room :\u0001ACTION " + "x".repeat(600) + "\u0001")
        assertTrue(event is IrcEvent.Message)
        val value = (event as IrcEvent.Message).value
        assertEquals("viewer", value.nickname)
        assertEquals("x".repeat(500), value.text)
    }

    @Test fun timelineDeduplicatesAndRetainsOnlyTheNewest150Messages() {
        var messages = emptyList<ChatMessage>()
        for (id in 0..150) messages = ChatTimeline.apply(messages, IrcEvent.Message(message(id.toString(), "A", "a", "1")))
        messages = ChatTimeline.apply(messages, IrcEvent.Message(message("150", "A", "a", "1")))
        assertEquals(150, messages.size)
        assertEquals("1", messages.first().id)
        assertEquals("150", messages.last().id)
    }

    @Test fun quietConnectedRoomIsNotShownAsStillConnecting() {
        assertEquals("Сообщений пока нет", ChatPresentation.statusLabel("Чат · только чтение", false))
        assertNull(ChatPresentation.statusLabel("Чат · только чтение", true))
    }

    @Test fun reconnectStatusRemainsVisibleAbovePreviouslyReceivedMessages() {
        assertEquals("Нет связи с чатом · повтор через 4 с", ChatPresentation.statusLabel("Нет связи с чатом · повтор через 4 с", true))
        assertEquals("Подключение к чату…", ChatPresentation.statusLabel("Подключение к чату…", false))
    }

    @Test fun terminalChannelAndAuthenticationNoticesHaveSafeLocalErrors() {
        val channel = IrcParser.parse("@msg-id=msg_channel_suspended :tmi.twitch.tv NOTICE #room :RAW_SERVER_DETAIL")
        assertEquals(IrcEvent.Rejected("Чат канала недоступен."), channel)
        val auth = IrcParser.parse(":tmi.twitch.tv NOTICE * :Login authentication failed")
        assertEquals(IrcEvent.Rejected("Twitch отклонил подключение к чату."), auth)
        val malformedAuth = IrcParser.parse(":tmi.twitch.tv NOTICE * :Improperly formatted auth")
        assertEquals(IrcEvent.Rejected("Twitch отклонил подключение к чату."), malformedAuth)
        assertFalse((channel as IrcEvent.Rejected).message.contains("RAW_SERVER_DETAIL"))
    }

    @Test fun ordinaryNoticesAndViewerTextDoNotStopReadOnlyChat() {
        assertNull(IrcParser.parse("@msg-id=slow_on :tmi.twitch.tv NOTICE #room :Slow mode enabled"))
        assertNull(IrcParser.parse("@msg-id=msg_banned :tmi.twitch.tv NOTICE #room :You cannot send messages"))
        assertTrue(IrcParser.parse(":viewer!viewer@host PRIVMSG #room :Login authentication failed") is IrcEvent.Message)
    }

    private fun message(id: String, display: String, login: String, userId: String?) =
        ChatMessage(id, display, "text", "#AABBCC", login, userId)
}
