package com.ghajarvpn.browser.qr

import android.app.Activity

import com.ghajarvpn.browser.BrowserUi
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** QR surface: scan from camera-less gallery decode, generate and share page QRs. */
class BrowserQrActivity : Activity() {

    private var mode = Mode.GENERATE
    private var payload: String = ""
    private var resultHostView: LinearLayout? = null

    private enum class Mode { GENERATE, SCAN }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        payload = intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()
        mode = if (intent.getBooleanExtra(EXTRA_SCAN, false)) Mode.SCAN else Mode.GENERATE
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(BrowserUi.IVORY)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.NAVY)
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        header.addView(BrowserUi.label(this, if (mode == Mode.SCAN) "اسکن QR از گالری" else "QR همین صفحه", 18f, BrowserUi.IVORY).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { setMargins(-dp(20), 0, -dp(20), dp(16)) })

        if (mode == Mode.SCAN) {
            root.addView(BrowserUi.label(this, "تصویر QR را از گالری انتخاب کنید", 13f, BrowserUi.MUTED_ON_LIGHT))
            val pick = BrowserUi.label(this, "انتخاب از گالری", 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                background = BrowserUi.pill(this@BrowserQrActivity, BrowserUi.GOLD, 14)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener { launchGallery() }
            }
            root.addView(pick, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        } else {
            if (BrowserQr.sanitize(payload) == null) {
                root.addView(BrowserUi.label(this, "این صفحه QR امنی ندارد", 14f, BrowserUi.RED))
            } else {
                val qr = ImageView(this).apply {
                    background = BrowserUi.pill(this@BrowserQrActivity, Color.WHITE, 18)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setImageBitmap(BrowserQr.encode(payload))
                }
                root.addView(qr, LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(8) })
                root.addView(BrowserUi.label(this, payload, 12f, BrowserUi.MUTED_ON_LIGHT).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, 0)
                    maxLines = 3
                })
                val share = BrowserUi.label(this, "اشتراک‌گذاری QR", 14f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = BrowserUi.pill(this@BrowserQrActivity, BrowserUi.GOLD, 14)
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                    setOnClickListener { shareQr() }
                }
                root.addView(share, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            }
        }

        val resultHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        root.addView(resultHost, LinearLayout.LayoutParams(-1, -2))
        resultHostView = resultHost

        val scroll = ScrollView(this).apply { addView(root) }
        return scroll
    }

    private fun launchGallery() {
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_IMAGE)
        }.onFailure { android.widget.Toast.makeText(this, "گالری در دسترس نیست", android.widget.Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data
            val bitmap = uri?.let { decodeScaled(it) }
            val scanned = bitmap?.let { BrowserQr.decode(it) }
            val host = resultHostView ?: return
            host.removeAllViews()
            if (scanned != null) {
                val open = BrowserUi.label(this, "باز کردن نشانی اسکن‌شده", 14f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = BrowserUi.pill(this@BrowserQrActivity, BrowserUi.GREEN, 14)
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                    setOnClickListener {
                        val result = Intent().putExtra(EXTRA_RESULT_URL, scanned)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }
                host.addView(open, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
                host.addView(BrowserUi.label(this, scanned, 12f, BrowserUi.MUTED_ON_LIGHT).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0); maxLines = 3
                })
            } else {
                host.addView(BrowserUi.label(this, "QR امنی پیدا نشد", 14f, BrowserUi.RED).apply {
                    setPadding(0, dp(16), 0, 0)
                })
            }
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val source = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val maxSide = 1024
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
        val scaled = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled != source) source.recycle()
        scaled
    }.getOrNull()

    private fun shareQr() {
        val dir = externalCacheDir ?: return
        val file = java.io.File(dir, "ghajar-page-qr.png")
        val bitmap = BrowserQr.encode(payload) ?: return
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.ghajar_downloads", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "اشتراک‌گذاری QR"))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAYLOAD = "qr_payload"
        const val EXTRA_SCAN = "qr_scan"
        const val EXTRA_RESULT_URL = "qr_result_url"
        private const val PICK_IMAGE = 841
    }
}
