package com.oprawa.twitch20.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelExtrasTest {
    @Test fun onlyExplicitNonDefaultTextIsUsed() {
        val data = JSONObject(
            """{"user":{"broadcastSettings":{"liveUpNotificationInfo":{"isDefault":false,"liveUpNotification":"  Авторский текст  "}}}}""",
        )
        assertEquals("Авторский текст", ChannelExtras.parseLiveMessage(data))
        val info = data.getJSONObject("user").getJSONObject("broadcastSettings").getJSONObject("liveUpNotificationInfo")
        info.put("isDefault", true)
        assertEquals("", ChannelExtras.parseLiveMessage(data))
        info.remove("isDefault")
        assertEquals("", ChannelExtras.parseLiveMessage(data))
        assertEquals("", ChannelExtras.parseLiveMessage(JSONObject()))
    }

    @Test fun optionalRequestFallsBackOnServiceErrorAndTimeout() = runTest {
        assertEquals("", ChannelExtras.liveMessage("channel") { _, _ ->
            throw TwitchException(TwitchError.SERVICE)
        })
        assertEquals("", ChannelExtras.liveMessage("channel") { _, _ ->
            delay(10_000)
            error("Optional query exceeded its time budget")
        })
    }
}
