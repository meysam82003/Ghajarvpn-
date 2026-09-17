package com.ghajarvpn.browser.download

import com.ghajarvpn.browser.BrowserContract

import android.content.Context
import android.content.Intent

/** Broadcast receiver handed off from the browser: persists the request then enqueues. */
class GhajarDownloadReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BrowserContract.ACTION_ENQUEUE_DOWNLOAD) return
        val url = intent.getStringExtra(BrowserContract.EXTRA_URL) ?: return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val service = Intent(context, GhajarDownloadService::class.java)
            .setAction(GhajarDownloadService.ACTION_ENQUEUE)
            .putExtra(BrowserContract.EXTRA_URL, url)
            .putExtra(BrowserContract.EXTRA_COOKIES, intent.getStringExtra(BrowserContract.EXTRA_COOKIES).orEmpty())
            .putExtra(BrowserContract.EXTRA_REFERER, intent.getStringExtra(BrowserContract.EXTRA_REFERER).orEmpty())
            .putExtra(BrowserContract.EXTRA_USER_AGENT, intent.getStringExtra(BrowserContract.EXTRA_USER_AGENT).orEmpty())
            .putExtra(BrowserContract.EXTRA_CONTENT_DISPOSITION, intent.getStringExtra(BrowserContract.EXTRA_CONTENT_DISPOSITION).orEmpty())
            .putExtra(BrowserContract.EXTRA_CONTENT_TYPE, intent.getStringExtra(BrowserContract.EXTRA_CONTENT_TYPE).orEmpty())
        context.startForegroundService(service)
    }
}
