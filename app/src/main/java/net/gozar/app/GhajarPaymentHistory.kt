package net.gozar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class GhajarPendingPayment(val orderId: String, val method: String, val label: String,
    val amount: Long, val expiresAt: Long, val status: String) {
    companion object {
        fun from(o: JSONObject) = GhajarPendingPayment(o.optString("order_id"), o.optString("method"),
            BrandConfig.sanitizePublicText(o.optString("method_label")), o.optLong("amount"),
            o.optLong("expires_at"), o.optString("status"))
    }
}

@Composable
fun GhajarPendingPaymentCard(item: GhajarPendingPayment, busy: Boolean, onResume: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    var confirmCancel by remember { mutableStateOf(false) }
    LaunchedEffect(item.expiresAt) {
        while (true) { now = System.currentTimeMillis() / 1000; delay(1000) }
    }
    val left = (item.expiresAt - now).coerceAtLeast(0)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .3f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("پرداخت در انتظار تأیید — ${item.label}", fontWeight = FontWeight.Bold)
            Text("کد فاکتور: ${item.orderId}")
            Text("مبلغ: ${paymentMoney(item.amount)} تومان")
            Text(if (item.expiresAt <= 0) "در انتظار بررسی وضعیت سرور"
                else if (left == 0L) "زمان پرداخت تمام شده؛ وضعیت را پیگیری کن"
                else "باقی‌مانده: %02d:%02d".format(left / 60, left % 60))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, enabled = !busy, modifier = Modifier.weight(1f)) { Text("ادامه پیگیری") }
                OutlinedButton(onClick = { confirmCancel = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("انصراف") }
            }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("انصراف از فاکتور") },
        text = { Text("اگر مبلغ را پرداخت کرده‌ای، پیگیری را ادامه بده. انصراف فقط پس از تأیید سرور انجام می‌شود.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onCancel() }) { Text("انصراف از فاکتور") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("بازگشت") } })
}

@Composable
fun GhajarTransactionHistory(api: GhajarStoreApi, revision: Int) {
    var page by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, refresh, revision) {
        busy = true; error = null
        try {
            val result = api.transactions(page)
            pages = result.optInt("total_pages")
            val array = result.optJSONArray("items")
            items = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { error = GhajarCommerceRules.publicMessage(e.message.orEmpty()) }
        finally { busy = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تاریخچه تراکنش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("گردش کیف پول در ۳۰ روز گذشته", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { refresh++ }, enabled = !busy) { Text("بروزرسانی تاریخچه") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!busy && error == null && items.isEmpty()) Text("تراکنشی ثبت نشده است.")
        items.forEach { item ->
            val credit = item.optString("direction") == "credit"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(BrandConfig.sanitizePublicText(item.optString("category_label")), fontWeight = FontWeight.Bold)
                    Text("${if (credit) "+" else "−"}${paymentMoney(item.optLong("amount"))} تومان",
                        color = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (!item.isNull("balance_after")) Text("موجودی پس از تراکنش: ${paymentMoney(item.optLong("balance_after"))} تومان")
                    item.optString("description").takeUnless { it.isBlank() || it == "null" }?.let { Text(BrandConfig.sanitizePublicText(it)) }
                    item.optString("order_id").takeUnless { it.isBlank() || it == "null" }?.let { Text("کد فاکتور: $it") }
                    Text(item.optString("created_at"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { page-- }, enabled = page > 1 && !busy) { Text("قبلی") }
            Text("$page / ${pages.coerceAtLeast(1)}", Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { page++ }, enabled = page < pages && !busy) { Text("بعدی") }
        }
    }
}

private fun paymentMoney(n: Long) = NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(n)
