package com.oprawa.twitch20.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SettingsCodec.decode(prefs.all))
    val state = mutable.asStateFlow()

    fun save(value: AppSettings) {
        val safe = value.normalized()
        val editor = prefs.edit()
        SettingsCodec.encode(safe).forEach { (key, stored) ->
            when (stored) {
                is String -> editor.putString(key, stored)
                is Int -> editor.putInt(key, stored)
                is Float -> editor.putFloat(key, stored)
                is Boolean -> editor.putBoolean(key, stored)
                else -> error("Unsupported settings value")
            }
        }
        // Legacy shared-style keys remain untouched: neither profile overwrites the other.
        editor.apply()
        mutable.value = safe
    }
}
