package com.ghajarvpn.downloads

import android.content.Context
import android.content.Intent

object DownloadContract {
    const val ACTION_ENQUEUE = "com.ghajarvpn.downloads.ENQUEUE"
    const val ACTION_RUN = "com.ghajarvpn.downloads.RUN"
    const val ACTION_PAUSE = "com.ghajarvpn.downloads.PAUSE"
    const val ACTION_RESUME = "com.ghajarvpn.downloads.RESUME"
    const val ACTION_CANCEL = "com.ghajarvpn.downloads.CANCEL"
    const val EXTRA_ID = "download_id"
    const val EXTRA_URL = "url"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_REFERER = "referer"
    const val EXTRA_USER_AGENT = "user_agent"
    const val EXTRA_CONTENT_DISPOSITION = "content_disposition"
    const val EXTRA_CONTENT_TYPE = "content_type"

    fun open(context: Context) = context.startActivity(Intent(context, DownloadManagerActivity::class.java))
}
