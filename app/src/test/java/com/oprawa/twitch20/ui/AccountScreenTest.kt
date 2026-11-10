package com.oprawa.twitch20.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScreenTest {
    @Test fun acceptsTwitchDeviceActivation() {
        assertTrue(isTwitchActivationUri("https://www.twitch.tv/activate?public=true&device-code=EXAMPLE"))
        assertTrue(isTwitchActivationUri("https://twitch.tv/activate"))
    }

    @Test fun rejectsOtherDestinations() {
        listOf(
            "http://www.twitch.tv/activate",
            "https://www.twitch.tv.example.com/activate",
            "https://example.com@www.twitch.tv/activate",
            "https://www.twitch.tv:8443/activate",
            "https://www.twitch.tv/other",
            "https://www.twitch.tv/activate#fragment",
        ).forEach { assertFalse(it, isTwitchActivationUri(it)) }
    }
}
