package com.ghajarvpn.browser.media

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/** Media3 session used by system controls while the dedicated player is alive. */
class GhajarMediaSession(context: Context, player: Player) {
    private val session = MediaSession.Builder(context, player).build()
    fun release() = session.release()
}
