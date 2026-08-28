package net.gozar.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun CardToCardCard(payment: GhajarPaymentInit, receipt: Uri?, busy: Boolean, sent: Boolean,
    onPickReceipt: () -> Unit, onUpload: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val preview by produceState<Bitmap?>(null, receipt) {
        value = withContext(Dispatchers.IO) {
            receipt?.let { uri -> runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull() }
        }
    }
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(value: String, label: String) { clipboard.setText(AnnotatedString(value)); copied = "$label کپی شد" }
    val money = remember { NumberFormat.getIntegerInstance(Locale("fa", "IR")) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp, color = Color(0xFF082F2B)) {
            Box {
                Image(painterResource(R.drawable.ghajar_payment_frame), contentDescription = null,
                    modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خزانهٔ قاجار", color = Color(0xFFFFE4A0), fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 36.dp))
                Text("کارت مقصد • اطلاعات صادرشده از پنل", color = Color(0xFFC6DCD4), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2066${payment.cardNumber.orEmpty().chunked(4).joinToString(" ")}\u2069",
                        Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { copy(payment.cardNumber.orEmpty(), "شماره کارت") }, enabled = !payment.cardNumber.isNullOrBlank()) {
                        Icon(Icons.Filled.ContentCopy, "کپی شماره کارت", tint = Color(0xFFE8C975))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(payment.cardHolder.orEmpty(), Modifier.weight(1f), color = Color.White)
                    IconButton(onClick = { copy(payment.cardHolder.orEmpty(), "نام صاحب کارت") }) {
                        Icon(Icons.Filled.ContentCopy, "کپی نام صاحب کارت", tint = Color(0xFFE8C975))
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .16f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مبلغ دقیق: ${money.format(payment.amount)} تومان", Modifier.weight(1f), color = Color(0xFFFFE4A0), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { copy(payment.amount.toString(), "مبلغ تومان") }) {
                        Icon(Icons.Filled.ContentCopy, "کپی مبلغ تومان", tint = Color(0xFFE8C975))
                    }
                }
                TextButton(onClick = { copy(payment.amountRial.toString(), "مبلغ ریال") }) {
                    Text("${money.format(payment.amountRial)} ریال • کپی", color = Color.White)
                }
            }
            }
        }
        copied?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        Text("همین مبلغ دقیق را واریز کن؛ ممکن است برای تطبیق خودکار با قیمت پایه تفاوت داشته باشد.",
            style = MaterialTheme.typography.bodySmall)
        preview?.let { Image(it.asImageBitmap(), "پیش‌نمایش رسید انتخاب‌شده",
            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit) }
        OutlinedButton(onClick = onPickReceipt, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("ghajar_pick_receipt")) {
            Text(if (receipt == null) "انتخاب عکس رسید از گوشی" else "تغییر عکس رسید")
        }
        Button(onClick = onUpload, enabled = receipt != null && !busy && !sent && payment.orderId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("ghajar_upload_receipt")) { Text(if (sent) "رسید ارسال شد؛ منتظر تأیید" else "ارسال رسید برای بررسی") }
        Text("JPEG، PNG یا WebP • حداکثر ۸ مگابایت. ارسال رسید به معنی تأیید پرداخت نیست.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun GhajarDeliveryDialog(delivery: GhajarDelivery, onDismiss: () -> Unit, onRetry: () -> Unit, busy: Boolean) {
    val service = delivery.service
    val payloads = remember(service) { (listOfNotNull(service.subscriptionUrl) + service.outputs).filter { it.isNotBlank() }.distinct() }
    var index by remember(payloads) { mutableIntStateOf(0) }
    val payload = payloads.getOrNull(index)
    var qrFailed by remember(payload) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, payload) {
        value = null // Never flash the previous profile's QR under a different label.
        value = withContext(Dispatchers.Default) { payload?.let { text ->
            runCatching {
                val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
                val pixels = IntArray(768 * 768) { i -> if (matrix[i % 768, i / 768]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                Bitmap.createBitmap(pixels, 768, 768, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        } }
        qrFailed = payload != null && value == null
    }
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (delivery.synced) "سرویس به قاجار VPN اضافه شد" else "سرویس صادر شد؛ در حال همگام‌سازی") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(service.productName, fontWeight = FontWeight.Bold)
                Text("\u2066${service.username}\u2069", style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                bitmap?.let { Image(it.asImageBitmap(), "QR اتصال همین سرویس",
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White), contentScale = ContentScale.Fit) }
                if (payload != null && bitmap == null && !busy) Text(if (qrFailed) "این خروجی در QR جا نمی‌شود؛ از کپی لینک استفاده کن." else "QR در حال آماده‌سازی است؛ لینک قابل کپی است.")
                if (payloads.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index-- }, enabled = index > 0) { Text("قبلی") }
                    Text("${index + 1} / ${payloads.size}")
                    TextButton(onClick = { index++ }, enabled = index < payloads.lastIndex) { Text("بعدی") }
                }
                Text(if (delivery.synced) "کانفیگ‌ها همگام شدند و در فهرست اتصال قرار دارند." else
                    "اگر همگام‌سازی کامل نشد، دریافت دوباره را بزن؛ خرید دوباره لازم نیست.", style = MaterialTheme.typography.bodySmall)
                Text("QR و لینک خصوصی‌اند؛ فقط با فرد مورد اعتماد به اشتراک بگذار.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("متوجه شدم") } },
        dismissButton = { Row {
            if (!delivery.synced) TextButton(onClick = onRetry, enabled = !busy) { Text("دریافت دوباره") }
            if (payload != null) TextButton(onClick = { clipboard.setText(AnnotatedString(payload)) }) { Text("کپی لینک") }
        } })
}
