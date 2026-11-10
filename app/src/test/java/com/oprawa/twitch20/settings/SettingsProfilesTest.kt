package com.oprawa.twitch20.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsProfilesTest {
    @Test fun selectingEitherModeIsExclusiveFromEveryStartingMode() {
        for (initial in ChatMode.entries) {
            for (requested in listOf(ChatMode.OVERLAY, ChatMode.THEATER)) {
                val result = AppSettings(chatMode = initial).toggleChat(requested)
                assertEquals(if (initial == requested) ChatMode.OFF else requested, result.chatMode)
                assertFalse(result.overlayEnabled && result.theaterEnabled)
                assertEquals(result.overlayEnabled || result.theaterEnabled, result.chatEnabled)
            }
        }
    }

    @Test fun disablingChatRetainsBothProfilesAndGlobalSettings() {
        val original = AppSettings(chatMode = ChatMode.THEATER,
            overlayStyle = ChatStyle(12, .1f), theaterStyle = ChatStyle(24, .9f),
            chatPosition = ChatPosition.BOTTOM_LEFT, showClock = false, animationsEnabled = false)
        assertEquals(original.copy(chatMode = ChatMode.OFF), original.toggleChat(ChatMode.THEATER))
        assertEquals(original.copy(chatMode = ChatMode.OFF), original.toggleChat(ChatMode.OFF))
        assertEquals(original, original.toggleChat(ChatMode.OFF).toggleChat(ChatMode.THEATER))
    }

    @Test fun editingOneStyleDoesNotChangeTheOtherOrEnableChat() {
        val original = AppSettings(chatMode = ChatMode.OFF, theaterStyle = ChatStyle(22, .8f))
        val changed = original.withStyle(ChatMode.OVERLAY, ChatStyle(14, .2f))
        assertEquals(ChatStyle(14, .2f), changed.overlayStyle)
        assertEquals(original.theaterStyle, changed.theaterStyle)
        assertEquals(ChatMode.OFF, changed.chatMode)
        assertEquals(AppSettings().overlayStyle, original.overlayStyle)
    }

    @Test fun activeStyleGettersUseTheaterAndOffFallsBackToOverlayWithoutEnablingIt() {
        val original = AppSettings(chatMode = ChatMode.THEATER,
            overlayStyle = ChatStyle(12, .1f), theaterStyle = ChatStyle(24, .9f))
        assertEquals(24, original.chatTextSizeSp)
        assertEquals(1f, original.chatOpacity, 0f)
        assertEquals(.9f, original.theaterStyle.opacity, 0f)
        val off = original.toggleChat(ChatMode.OFF)
        assertEquals(12, off.chatTextSizeSp)
        assertEquals(.1f, off.chatOpacity, 0f)
        assertFalse(off.chatEnabled)
        assertEquals(off.withStyle(ChatMode.OVERLAY, ChatStyle(16, .3f)),
            off.withStyle(ChatMode.OFF, ChatStyle(16, .3f)))
    }

    @Test fun disabledLegacyModeSeedsBothProfilesWithoutLosingPositionOrGlobals() {
        val settings = SettingsCodec.decode(mapOf("chatEnabled" to false, "chatMode" to "THEATER",
            "chatTextSizeSp" to 20, "chatOpacity" to .4f, "chatPosition" to "BOTTOM_LEFT",
            "showClock" to false, "animationsEnabled" to false))
        assertEquals(ChatMode.OFF, settings.chatMode)
        assertEquals(ChatStyle(20, .4f), settings.overlayStyle)
        assertEquals(ChatStyle(20, .4f), settings.theaterStyle)
        assertEquals(ChatPosition.BOTTOM_LEFT, settings.chatPosition)
        assertFalse(settings.showClock)
        assertFalse(settings.animationsEnabled)
    }

    @Test fun enabledLegacyTheaterRemainsTheater() {
        assertEquals(ChatMode.THEATER, SettingsCodec.decode(mapOf("chatEnabled" to true,
            "chatMode" to "THEATER")).chatMode)
        assertEquals(ChatMode.OVERLAY, SettingsCodec.decode(emptyMap<String, Any>()).chatMode)
    }

    @Test fun savedNewModeAndProfilesTakePrecedenceOverLegacyValues() {
        val settings = SettingsCodec.decode(mapOf("activeChatMode" to "THEATER",
            "chatEnabled" to false, "chatMode" to "OVERLAY", "chatTextSizeSp" to 16,
            "chatOpacity" to .65f, "overlayTextSizeSp" to 12, "overlayOpacity" to .1f,
            "theaterTextSizeSp" to 24, "theaterOpacity" to .9f))
        assertTrue(settings.theaterEnabled)
        assertEquals(ChatStyle(12, .1f), settings.overlayStyle)
        assertEquals(ChatStyle(24, .9f), settings.theaterStyle)
    }

    @Test fun missingNewStyleFieldsFallBackIndividuallyToLegacy() {
        val settings = SettingsCodec.decode(mapOf("chatTextSizeSp" to 18, "chatOpacity" to .4f,
            "overlayTextSizeSp" to 12, "theaterOpacity" to .8f))
        assertEquals(ChatStyle(12, .4f), settings.overlayStyle)
        assertEquals(ChatStyle(18, .8f), settings.theaterStyle)
    }

    @Test fun invalidStoredAndEditedValuesRemainBoundedAndFinite() {
        val decoded = SettingsCodec.decode(mapOf("activeChatMode" to "invalid", "chatMode" to "invalid",
            "overlayTextSizeSp" to -100, "overlayOpacity" to Float.NaN,
            "theaterTextSizeSp" to 100, "theaterOpacity" to 2f, "chatPosition" to "invalid"))
        assertEquals(ChatStyle(12, .65f), decoded.overlayStyle)
        assertEquals(ChatStyle(24, 1f), decoded.theaterStyle)
        assertEquals(ChatMode.OVERLAY, decoded.chatMode)
        assertEquals(ChatPosition.TOP_RIGHT, decoded.chatPosition)
        assertEquals(ChatStyle(24, 0f), AppSettings().withStyle(ChatMode.THEATER,
            ChatStyle(99, -1f)).theaterStyle)
    }

    @Test fun encodedOffRoundTripRetainsProfilesAndDoesNotRewriteLegacyStyleKeys() {
        val original = AppSettings(chatMode = ChatMode.OFF,
            overlayStyle = ChatStyle(12, .1f), theaterStyle = ChatStyle(24, .9f),
            chatPosition = ChatPosition.BOTTOM_RIGHT, showClock = false)
        val values = SettingsCodec.encode(original)
        assertEquals(original, SettingsCodec.decode(values))
        assertEquals("OFF", values["activeChatMode"])
        assertFalse(values.containsKey("chatEnabled"))
        assertFalse(values.containsKey("chatMode"))
        assertFalse(values.containsKey("chatTextSizeSp"))
        assertFalse(values.containsKey("chatOpacity"))
    }

    @Test fun legacySettingsGetDefaultGeometryAndPreviewWithoutResettingProfiles() {
        val settings = SettingsCodec.decode(mapOf("activeChatMode" to "THEATER",
            "theaterOpacity" to .2f, "overlayOpacity" to .4f, "showClock" to false))
        assertEquals(OverlaySize.MEDIUM, settings.overlaySize)
        assertTrue(settings.cardPreviewEnabled)
        assertTrue(settings.previewSoundEnabled)
        assertFalse(settings.notificationSoundEnabled)
        assertEquals(ClockStyle.CLASSIC, settings.clockStyle)
        assertEquals(ClockPlacement.VIDEO, settings.clockPlacement)
        assertEquals(1f, settings.chatOpacity, 0f)
        assertEquals(.2f, settings.theaterStyle.opacity, 0f)
        assertFalse(settings.showClock)
        assertEquals(OverlaySize.MEDIUM, SettingsCodec.decode(mapOf("overlaySize" to "unknown")).overlaySize)
    }

    @Test fun newSettingsRoundTripPreservesSizePreviewClockAndInactiveProfiles() {
        val settings = AppSettings(chatMode = ChatMode.OFF, overlaySize = OverlaySize.TALL,
            cardPreviewEnabled = false, showClock = false, overlayStyle = ChatStyle(14, .25f),
            theaterStyle = ChatStyle(22, .1f), clockStyle = ClockStyle.SEGMENTS,
            clockPlacement = ClockPlacement.CHAT, previewSoundEnabled = false, notificationSoundEnabled = true)
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test fun notificationToggleMigratesEnabledAndPersistsWithoutChangingProfiles() {
        val legacy = mapOf("activeChatMode" to "THEATER", "overlayTextSizeSp" to 17,
            "overlayOpacity" to .35f, "theaterTextSizeSp" to 20, "showClock" to false)
        val initial = SettingsCodec.decode(legacy)
        assertTrue(initial.followNotificationsEnabled)
        val changed = initial.copy(followNotificationsEnabled = false)
        val reopened = SettingsCodec.decode(SettingsCodec.encode(changed))
        assertEquals(changed, reopened)
        assertEquals(initial.overlayStyle, reopened.overlayStyle)
        assertEquals(initial.theaterStyle, reopened.theaterStyle)
        assertEquals(ChatMode.THEATER, reopened.chatMode)
        assertFalse(reopened.showClock)
    }
}
