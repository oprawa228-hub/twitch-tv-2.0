package com.oprawa.twitch20.ui

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.delay

/** Mixes into the existing media stream without requesting audio focus. */
suspend fun playLiveNotificationSound() {
    val tone = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 16)
    } catch (_: RuntimeException) {
        return
    }
    try {
        tone.startTone(ToneGenerator.TONE_DTMF_1, 75)
        delay(95)
        tone.startTone(ToneGenerator.TONE_DTMF_3, 85)
        delay(85)
    } finally {
        tone.release()
    }
}
