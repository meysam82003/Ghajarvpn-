package com.ghajarvpn.browser.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLifecyclePolicyTest {
    @Test fun keeps_one_player_for_pip_background_or_browser_mini_player() {
        assertFalse(PlaybackLifecyclePolicy.shouldPause(false, true, false, false))
        assertFalse(PlaybackLifecyclePolicy.shouldPause(true, false, false, false))
        assertFalse(PlaybackLifecyclePolicy.shouldPause(false, false, true, true))
    }

    @Test fun pauses_when_the_user_leaves_without_permission() {
        assertTrue(PlaybackLifecyclePolicy.shouldPause(false, false, false, true))
        assertTrue(PlaybackLifecyclePolicy.shouldPause(false, false, true, false))
    }

    @Test fun media_recovery_is_bounded_and_backed_off() {
        assertEquals(500L, MediaRecoveryPolicy.retryDelay(0))
        assertEquals(1_500L, MediaRecoveryPolicy.retryDelay(1))
        assertEquals(3_000L, MediaRecoveryPolicy.retryDelay(2))
        assertNull(MediaRecoveryPolicy.retryDelay(3))
    }

    @Test fun network_switch_only_recovers_active_stalled_playback() {
        assertTrue(MediaRecoveryPolicy.shouldRecoverAfterPathChange(true, Player.STATE_BUFFERING, false))
        assertTrue(MediaRecoveryPolicy.shouldRecoverAfterPathChange(true, Player.STATE_IDLE, true))
        assertFalse(MediaRecoveryPolicy.shouldRecoverAfterPathChange(false, Player.STATE_BUFFERING, true))
        assertFalse(MediaRecoveryPolicy.shouldRecoverAfterPathChange(true, Player.STATE_READY, false))
    }

    @Test fun resume_storage_never_contains_the_raw_media_url() {
        val key = MediaPrivacyPolicy.resumeStorageKey("https://user.example/private/video.mp4?token=secret")
        assertTrue(MediaPrivacyPolicy.isResumeStorageKey(key))
        assertFalse(key.contains("user.example"))
        assertFalse(key.contains("secret"))
        assertFalse(MediaPrivacyPolicy.isResumeStorageKey("speed"))
    }
}
