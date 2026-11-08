package com.oprawa.twitch20

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.settings.SettingsStore
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses an isolated preference file; never reads, clears or migrates the user's settings. */
@RunWith(AndroidJUnit4::class)
class ChatSettingsPersistenceTest {
    @Test fun independentProfilesSurviveSaveAndReopenWithoutOverwritingLegacyKeys() = withPreferences { context, prefs ->
        prefs.edit().putInt("chatTextSizeSp", 20).putFloat("chatOpacity", .4f).commit()
        val store = SettingsStore(context)
        store.save(store.state.value.withStyle(ChatMode.OVERLAY, ChatStyle(12, .1f))
            .withStyle(ChatMode.THEATER, ChatStyle(24, .9f)).copy(chatMode = ChatMode.THEATER))
        val reopened = SettingsStore(context).state.value
        assertEquals(ChatStyle(12, .1f), reopened.overlayStyle)
        assertEquals(ChatStyle(24, .9f), reopened.theaterStyle)
        assertEquals(ChatMode.THEATER, reopened.chatMode)
        assertEquals(20, prefs.getInt("chatTextSizeSp", -1))
        assertEquals(.4f, prefs.getFloat("chatOpacity", -1f), .0001f)
    }

    @Test fun switchingOffAndBackOnDoesNotEraseEitherPersistedStyle() = withPreferences { context, _ ->
        val store = SettingsStore(context)
        val configured = store.state.value.withStyle(ChatMode.OVERLAY, ChatStyle(14, .2f))
            .withStyle(ChatMode.THEATER, ChatStyle(22, .8f)).copy(chatMode = ChatMode.THEATER)
        store.save(configured.toggleChat(ChatMode.THEATER))
        val reopened = SettingsStore(context)
        assertEquals(ChatMode.OFF, reopened.state.value.chatMode)
        assertEquals(configured.overlayStyle, reopened.state.value.overlayStyle)
        assertEquals(configured.theaterStyle, reopened.state.value.theaterStyle)
        reopened.save(reopened.state.value.toggleChat(ChatMode.OVERLAY))
        val enabled = SettingsStore(context).state.value
        assertEquals(ChatMode.OVERLAY, enabled.chatMode)
        assertEquals(configured.overlayStyle, enabled.overlayStyle)
        assertEquals(configured.theaterStyle, enabled.theaterStyle)
    }

    @Test fun newTheaterProfileOverridesLegacySharedStyle() = withPreferences { context, prefs ->
        prefs.edit().putString("activeChatMode", "THEATER")
            .putInt("overlayTextSizeSp", 14).putFloat("overlayOpacity", .2f)
            .putInt("theaterTextSizeSp", 22).putFloat("theaterOpacity", .9f)
            .putInt("chatTextSizeSp", 16).putFloat("chatOpacity", .65f).commit()
        val settings = SettingsStore(context).state.value
        assertEquals("THEATER", settings.chatMode.name)
        assertEquals(22, settings.chatTextSizeSp)
        assertEquals(.9f, settings.chatOpacity, .0001f)
        assertTrue(settings.chatEnabled)
    }

    @Test fun disabledLegacyChatBecomesOffRegardlessOfRememberedMode() = withPreferences { context, prefs ->
        prefs.edit().putBoolean("chatEnabled", false).putString("chatMode", "THEATER").commit()
        val settings = SettingsStore(context).state.value
        assertFalse(settings.chatEnabled)
        assertEquals("OFF", settings.chatMode.name)
    }

    @Test fun savingLegacySettingsSeedsBothProfilesAndPreservesGlobals() = withPreferences { context, prefs ->
        prefs.edit().putBoolean("chatEnabled", true).putString("chatMode", "THEATER")
            .putInt("chatTextSizeSp", 20).putFloat("chatOpacity", .4f)
            .putString("chatPosition", "BOTTOM_LEFT").putBoolean("showClock", false)
            .putBoolean("animationsEnabled", false).commit()
        val store = SettingsStore(context)
        store.save(store.state.value)
        assertEquals("THEATER", prefs.getString("activeChatMode", null))
        assertEquals(20, prefs.getInt("overlayTextSizeSp", -1))
        assertEquals(20, prefs.getInt("theaterTextSizeSp", -1))
        assertEquals(.4f, prefs.getFloat("overlayOpacity", -1f), .0001f)
        assertEquals(.4f, prefs.getFloat("theaterOpacity", -1f), .0001f)
        assertEquals("BOTTOM_LEFT", store.state.value.chatPosition.name)
        assertFalse(store.state.value.showClock)
        assertFalse(store.state.value.animationsEnabled)
        assertEquals(store.state.value, SettingsStore(context).state.value)
    }

    @Test fun explicitOffSurvivesReopeningAndUsesOverlayOnlyForStyleFallback() = withPreferences { context, prefs ->
        prefs.edit().putString("activeChatMode", "OFF").putBoolean("chatEnabled", true)
            .putString("chatMode", "THEATER").putInt("overlayTextSizeSp", 12)
            .putFloat("overlayOpacity", .1f).putInt("theaterTextSizeSp", 24)
            .putFloat("theaterOpacity", 1f).commit()
        val store = SettingsStore(context)
        store.save(store.state.value)
        val reopened = SettingsStore(context).state.value
        assertEquals("OFF", reopened.chatMode.name)
        assertFalse(reopened.chatEnabled)
        assertEquals(12, reopened.chatTextSizeSp)
        assertEquals(.1f, reopened.chatOpacity, .0001f)
    }

    private fun withPreferences(block: (Context, SharedPreferences) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val name = "qa_chat_profiles_" + UUID.randomUUID().toString()
        val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences {
                check(requested == "settings")
                return prefs
            }
        }
        try { block(isolated, prefs) }
        finally {
            check(name.startsWith("qa_chat_profiles_"))
            prefs.edit().clear().commit()
        }
    }
}
