package net.gozar.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import net.gozar.app.configcenter.ConfigExtractor
import net.gozar.app.configcenter.ConfigNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "اتصال به کانفیگ" — reads the shared content, imports every valid profile
 * through the existing toolkit pipeline and immediately launches the first
 * healthy one. Unsupported protocols report honestly; nothing fakes success.
 */
class ConfigQuickConnectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) { finish(); return }
        val store = ConfigStore.get(applicationContext)
        lifecycleScope.launch {
            store.awaitReady()
            val summary = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching Result.fail("خواندن فایل ممکن نشد")
                    val result = ConfigNormalizer.normalizeFile(uri.lastPathSegment, bytes, mutableSetOf())
                    if (result.validCount == 0) {
                        val locked = ConfigExtractor.extract(uri.lastPathSegment, bytes).locked
                        return@runCatching if (locked) Result.fail("🔒 این فایل نیازمند رمز عبور است")
                        else Result.fail("هیچ کانفیگ سالمی پیدا نشد")
                    }
                    val proxies = result.items.mapNotNull { it.profile?.toProxyConfig() }
                    val added = store.addImported(proxies)
                    Result.ok(added, result)
                }.getOrElse { Result.fail(it.message ?: "پردازش فایل ناموفق بود") }
            }
            when {
                summary.added > 0 || summary.result.validCount > 0 -> {
                    val first = summary.result.items.firstOrNull { it.outcome == ConfigNormalizer.Outcome.VALID && it.profile != null }
                        ?: summary.result.items.firstOrNull { it.profile != null }
                    if (first != null) {
                        Toast.makeText(this@ConfigQuickConnectActivity, "«${first.profile?.name}» برای اتصال آماده شد", Toast.LENGTH_SHORT).show()
                        val config = store.configs.value.firstOrNull { it.name == first.profile?.name }
                            ?: store.configs.value.firstOrNull()
                        if (config != null) {
                            ConfigQuickConnectBridge.activity?.quickConnect(config)
                            ?: Toast.makeText(this@ConfigQuickConnectActivity, "برای اتصال، برنامه را باز کنید", Toast.LENGTH_LONG).show()
                            finish()
                            return@launch
                        }
                    }
                    Toast.makeText(this@ConfigQuickConnectActivity, "کانفیگ قابل اجرا پیدا نشد", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@ConfigQuickConnectActivity, summary.message, Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }


    private data class Result(val added: Int, val result: ConfigNormalizer.Result, val message: String) {
        companion object {
            fun ok(added: Int, result: ConfigNormalizer.Result) = Result(added, result, "")
            fun fail(message: String) = Result(0, ConfigNormalizer.Result(emptyList()), message)
        }
    }
}

/** MainActivity hand-off used only for the guarded quick-connect entry point. */
object ConfigQuickConnectBridge {
    @Volatile var activity: MainActivity? = null
}
