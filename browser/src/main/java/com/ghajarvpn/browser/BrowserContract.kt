package com.ghajarvpn.browser

import android.content.Context
import android.content.Intent

object BrowserContract {
    const val ACTION_ENQUEUE_DOWNLOAD = "com.ghajarvpn.downloads.ENQUEUE"
    const val EXTRA_URL = "url"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_REFERER = "referer"
    const val EXTRA_USER_AGENT = "user_agent"
    const val EXTRA_CONTENT_DISPOSITION = "content_disposition"
    const val EXTRA_CONTENT_TYPE = "content_type"
    const val EXTRA_PRIVATE = "private"

    fun open(context: Context, url: String? = null, private: Boolean = false) {
        context.startActivity(Intent(context, GhajarBrowserActivity::class.java).apply {
            url?.let { putExtra(EXTRA_URL, it) }
            putExtra(EXTRA_PRIVATE, private)
        })
    }
}
