package com.ghajarvpn.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.URLUtil

class DownloadEnqueueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadContract.ACTION_ENQUEUE) return
        val pending = goAsync()
        Thread {
            try {
                val url = intent.getStringExtra(DownloadContract.EXTRA_URL).orEmpty()
                val mime = intent.getStringExtra(DownloadContract.EXTRA_CONTENT_TYPE).orEmpty()
                val disposition = intent.getStringExtra(DownloadContract.EXTRA_CONTENT_DISPOSITION).orEmpty()
                val request = EnqueueRequest(
                    url = url,
                    fileName = URLUtil.guessFileName(url, disposition, mime),
                    mimeType = mime,
                    headers = mapOf(
                        "Cookie" to intent.getStringExtra(DownloadContract.EXTRA_COOKIES).orEmpty(),
                        "Referer" to intent.getStringExtra(DownloadContract.EXTRA_REFERER).orEmpty(),
                        "User-Agent" to intent.getStringExtra(DownloadContract.EXTRA_USER_AGENT).orEmpty()
                    )
                )
                if (DownloadRepository.get(context).enqueue(request) != null) start(context)
            } finally { pending.finish() }
        }.start()
    }

    private fun start(context: Context) {
        val service = Intent(context, GhajarDownloadService::class.java).setAction(DownloadContract.ACTION_RUN)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
    }
}
