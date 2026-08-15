package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.PlaybackBufferMode
import org.junit.Test

class PlaybackBufferPoliciesTest {

    private fun assertValid(policy: PlaybackBufferPolicy) {
        assertThat(policy.minBufferMs).isAtLeast(policy.playbackBufferMs)
        assertThat(policy.minBufferMs).isAtLeast(policy.rebufferMs)
        assertThat(policy.maxBufferMs).isAtLeast(policy.minBufferMs)
        assertThat(policy.playbackBufferMs).isAtLeast(250)
    }

    @Test
    fun `normal live is exoplayer valid and fast`() {
        val policy = PlaybackBufferPolicies.forPlayback(isLive = true, compatibilityMode = false)
        assertThat(policy.label).isEqualTo("stable-live")
        assertValid(policy)
        assertThat(policy.playbackBufferMs).isAtMost(2_000)
    }

    @Test
    fun `vod is exoplayer valid and fast`() {
        val policy = PlaybackBufferPolicies.forPlayback(isLive = false, compatibilityMode = false)
        assertThat(policy.label).isEqualTo("stable-vod")
        assertValid(policy)
        assertThat(policy.playbackBufferMs).isAtMost(2_500)
    }

    @Test
    fun `medium and large live remain valid`() {
        assertValid(PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.MEDIUM
        ))
        assertValid(PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.LARGE
        ))
    }
}
