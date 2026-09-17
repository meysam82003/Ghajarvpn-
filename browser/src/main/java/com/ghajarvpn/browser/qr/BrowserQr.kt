package com.ghajarvpn.browser.qr

import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumSet

/**
 * QR encode/decode for browser pages. Encoding is pure zxing; decoding takes a
 * decoded Bitmap (from the gallery picker) and returns only http(s) payloads —
 * anything else is rejected so a crafted QR cannot open arbitrary intents.
 */
object BrowserQr {

    const val MAX_PAYLOAD = 2_048

    fun encode(payload: String, size: Int = 512): Bitmap? {
        if (payload.isBlank() || payload.length > MAX_PAYLOAD) return null
        return runCatching {
            val matrix = QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, size, size,
                mapOf(EncodeHintType.MARGIN to 1)
            )
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix.get(x, y)) 0xFF12202B.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    /** Returns the scanned payload only when it is a safe web URL. */
    fun decode(bitmap: Bitmap): String? = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        sanitize(result.text)
    }.getOrNull()

    /** Whitelists http(s) QR payloads; blocks javascript:, data:, intent: and friends. */
    fun sanitize(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > MAX_PAYLOAD) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return value
    }
}
