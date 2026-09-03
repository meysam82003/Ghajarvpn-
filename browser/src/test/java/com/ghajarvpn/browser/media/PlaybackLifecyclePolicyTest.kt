package com.ghajarvpn.browser.media

import org.junit.Assert.assertFalse
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
}
