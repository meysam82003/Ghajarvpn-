package net.gozar.app

import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GhajarShopScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ConfigStore.get(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<GhajarStoreSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val token = remember {
        context.getSharedPreferences("ghajar_store", 0).getString("telegram_init_data", null)
    }

    val checkout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                loading = true
                val fresh = runCatching { GhajarStoreApi.loadSnapshot(token) }.getOrNull()
                if (fresh != null) {
                    snapshot = fresh
                    val count = GhajarStoreApi.importNewDeliveredServices(context, store, fresh.services)
                    message = if (count > 0) "$count پیکربندی خریداری‌شده خودکار اضافه شد" else "پرداخت ثبت شد؛ تحویل سرویس در حال همگام‌سازی است"
                } else {
                    message = "پرداخت ثبت شد؛ برای دریافت سرویس دوباره به‌روزرسانی کن"
                }
                loading = false
            }
        }
    }

    fun embeddedCheckout(url: String) {
        checkout.launch(
            Intent(context, SecurePaymentActivity::class.java)
                .putExtra(SecurePaymentActivity.EXTRA_URL, url)
        )
    }

    fun reload() {
        scope.launch {
            loading = true
            snapshot = runCatching { GhajarStoreApi.loadSnapshot(token) }
                .onFailure { message = "فروشگاه موقتاً پاسخ نمی‌دهد؛ نسخهٔ داخلی باز می‌شود." }
                .getOrNull()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("فروشگاه قاجار وی پی ان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("قیمت‌ها و سرویس‌ها مستقیم از پنل به‌روز می‌شوند", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = ::reload) { Icon(Icons.Filled.Refresh, contentDescription = "به‌روزرسانی") }
            }
        }

        snapshot?.announcement?.let { notice ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD5AD4A).copy(alpha = .18f)),
                    border = BorderStroke(1.dp, Color(0xFFD5AD4A)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(notice, Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center)
                }
            }
        }

        message?.let { info ->
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(info, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { embeddedCheckout(BrandConfig.STORE_BASE_URL) }) {
                            Text("باز کردن فروشگاه داخل برنامه")
                        }
                    }
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF91BCC7))
                }
            }
        }

        val owned = snapshot?.services.orEmpty()
        if (owned.isNotEmpty()) {
            item { Text("سرویس‌های من", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(owned, key = { it.username }) { service ->
                OwnedServiceCard(service) {
                    val count = GhajarStoreApi.importDeliveredService(store, service)
                    message = if (count > 0) "$count پیکربندی به برنامه اضافه شد" else "پیکربندی این سرویس هنوز تحویل نشده"
                }
            }
        }

        val products = snapshot?.products.orEmpty()
        if (products.isNotEmpty()) {
            item { Text("خرید سرویس", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(products, key = { it.id }) { product ->
                ProductCard(product) {
                    scope.launch {
                        loading = true
                        val url = product.checkoutUrl ?: runCatching {
                            GhajarStoreApi.createCheckout(product.id, token)
                        }.getOrNull()
                        loading = false
                        if (url != null) embeddedCheckout(url)
                        else embeddedCheckout(BrandConfig.STORE_BASE_URL)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        loading = true
                        val result = runCatching { GhajarStoreApi.requestTrial(token) }.getOrNull()
                        val fresh = if (result != null) runCatching { GhajarStoreApi.loadSnapshot(token) }.getOrNull() else null
                        loading = false
                        if (fresh != null) {
                            snapshot = fresh
                            val count = GhajarStoreApi.importNewDeliveredServices(context, store, fresh.services)
                            message = if (count > 0) "$count پیکربندی تست خودکار اضافه شد" else "درخواست تست ثبت شد؛ تحویل در حال همگام‌سازی است"
                        } else {
                            message = if (result != null) "درخواست تست ثبت شد؛ سرویس‌ها را به‌روزرسانی کن" else "برای دریافت تست، فروشگاه داخلی را باز کن"
                        }
                        if (result == null) embeddedCheckout(BrandConfig.STORE_BASE_URL)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
            ) {
                Icon(Icons.Filled.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("دریافت تست رایگان")
            }
        }
    }
}

@Composable
private fun ProductCard(product: GhajarProduct, onBuy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF91BCC7).copy(alpha = .55f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFFD5AD4A))
                Spacer(Modifier.width(10.dp))
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (product.price > 0) Text("${formatPrice(product.price)} تومان", fontWeight = FontWeight.Black, color = Color(0xFF0D8065))
            }
            val details = listOfNotNull(
                product.trafficGb?.let { "${it.toString().removeSuffix(".0")} گیگابایت" },
                product.days?.let { "$it روز" }
            ).joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (product.description.isNotBlank()) Text(product.description, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("خرید و فعال‌سازی") }
        }
    }
}

@Composable
private fun OwnedServiceCard(service: GhajarOwnedService, onImport: () -> Unit) {
    val progress = if (service.totalGb > 0) (service.usedGb / service.totalGb).coerceIn(0.0, 1.0) else 0.0
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(service.productName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(service.status, color = if (service.status.contains("active", true)) Color(0xFF0D8065) else MaterialTheme.colorScheme.error)
            }
            Text("${service.usedGb} از ${service.totalGb} گیگابایت · باقی‌مانده ${service.remainingGb}")
            Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF91BCC7).copy(alpha = .25f), RoundedCornerShape(10.dp))) {
                Box(Modifier.fillMaxWidth(progress.toFloat()).height(7.dp).background(Color(0xFF0D8065), RoundedCornerShape(10.dp)))
            }
            if (service.expiresAt.isNotBlank()) Text("پایان سرویس: ${service.expiresAt}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("افزودن به سرویس‌های برنامه")
            }
        }
    }
}

private fun formatPrice(price: Long): String = NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(price)
