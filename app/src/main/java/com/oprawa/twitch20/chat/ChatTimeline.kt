package com.oprawa.twitch20.chat

internal object ChatTimeline {
    fun apply(messages: List<ChatMessage>, event: IrcEvent?): List<ChatMessage> = when (event) {
        is IrcEvent.Message -> if (messages.any { it.id == event.value.id }) messages
            else (messages + event.value).takeLast(150)
        is IrcEvent.Delete -> messages.filterNot { it.id == event.id }
        is IrcEvent.Clear -> {
            if (event.userId == null && event.nickname == null) emptyList()
            else messages.filterNot { message ->
                if (event.userId != null && message.userId != null) message.userId == event.userId
                else event.nickname != null && message.login.equals(event.nickname, ignoreCase = true)
            }
        }
        else -> messages
    }
}

internal object ChatPresentation {
    fun statusLabel(status: String, hasMessages: Boolean): String? = when {
        status.isBlank() || status == "Чат · только чтение" -> if (hasMessages) null else "Сообщений пока нет"
        else -> status
    }
}
