package net.gozar.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Central router for every externally-opened config (file manager VIEW/SEND,
 * browser downloads, share targets). Goes straight to quick connect; the
 * separate "toolkit" chooser step was removed.
 */
object ConfigCenterRouter {

    fun chooserIntent(context: Context, uri: Uri): Intent =
        Intent(context, ConfigCenterActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun quickConnectIntent(context: Context, uri: Uri): Intent =
        Intent(context, ConfigQuickConnectActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
