package com.streamvault.player.playback

import com.streamvault.domain.model.StreamInfo
import com.streamvault.domain.model.StreamType

enum class PlayerTimeoutProfile(
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long
) {
    LIVE(
        connectTimeoutMs = 8_000L,
        readTimeoutMs = 15_000L,
        writeTimeoutMs = 15_000L
    ),
    VOD(
        connectTimeoutMs = 12_000L,
        readTimeoutMs = 40_000L,
        writeTimeoutMs = 25_000L
    ),
    PROGRESSIVE(
        connectTimeoutMs = 6_000L,
        readTimeoutMs = 12_000L,
        writeTimeoutMs = 20_000L
    ),
    PRELOAD(
        connectTimeoutMs = 8_000L,
        readTimeoutMs = 12_000L,
        writeTimeoutMs = 12_000L
    );

    companion object {
        fun resolve(
            streamInfo: StreamInfo,
            resolvedStreamType: ResolvedStreamType,
            preload: Boolean
        ): PlayerTimeoutProfile {
            if (preload) return PRELOAD
            if (streamInfo.streamType == StreamType.RTSP) return LIVE
            return when {
                resolvedStreamType == ResolvedStreamType.HLS -> LIVE
                resolvedStreamType == ResolvedStreamType.SMOOTH_STREAMING -> LIVE
                resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE -> LIVE
                resolvedStreamType == ResolvedStreamType.RTSP -> LIVE
                resolvedStreamType == ResolvedStreamType.PROGRESSIVE -> PROGRESSIVE
                streamInfo.streamType == StreamType.PROGRESSIVE -> PROGRESSIVE
                else -> VOD
            }
        }
    }
}
