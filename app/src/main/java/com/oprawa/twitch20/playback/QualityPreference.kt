package com.oprawa.twitch20.playback

import com.oprawa.twitch20.data.VideoVariant

/** A choice for one media identity, never a retained TrackGroup or manifest index. */
internal class QualityPreference {
    private var mediaId: String? = null
    var preferredHls: QualityOption? = null
        private set
    private var progressiveId: String? = null

    fun beginMedia(mediaId: String) {
        if (this.mediaId != mediaId) {
            preferredHls = null
            progressiveId = null
            this.mediaId = mediaId
        }
    }

    fun rememberHls(option: QualityOption?) {
        // Indices identify only the current menu, not a choice across source refresh.
        preferredHls = option?.copy(id = "")
        progressiveId = null
    }

    fun rememberProgressive(id: String?) {
        progressiveId = id
        preferredHls = null
    }

    /** Presentation only: do not erase a preference before new tracks are available. */
    fun pendingHlsId(cachedOptions: List<QualityOption>): String? = matchHls(cachedOptions)?.id

    fun restoreHls(options: List<QualityOption>): QualityOption? {
        if (preferredHls == null) return null
        if (options.isEmpty()) return null // Preparation has not exposed its tracks yet.
        val match = matchHls(options)
        if (match == null) preferredHls = null
        return match
    }

    private fun matchHls(options: List<QualityOption>): QualityOption? {
        val preferred = preferredHls ?: return null
        return options.firstOrNull { option ->
            when {
                preferred.height != null && preferred.frameRate != null ->
                    option.height == preferred.height && option.frameRate == preferred.frameRate
                preferred.height != null -> option.height == preferred.height && option.label == preferred.label
                else -> option.label == preferred.label
            }
        }
    }

    fun restoreProgressive(variants: List<VideoVariant>): VideoVariant? {
        if (variants.isEmpty()) return null
        val preferred = variants.firstOrNull { it.id == progressiveId }
        if (preferred == null) progressiveId = null
        return preferred ?: variants.first()
    }
}

internal data class QualityRecoveryPlan(val state: PlaybackState, val seekPositionMs: Long?)

internal fun qualityRecoveryPlan(previous: PlaybackState, selectedId: String? = previous.selectedQualityId): QualityRecoveryPlan = QualityRecoveryPlan(
    previous.copy(status = PlaybackStatus.Buffering, error = null, isPlaying = false, selectedQualityId = selectedId),
    if (previous.isLive) null else previous.positionMs.coerceAtLeast(0),
)
