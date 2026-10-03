package net.gozar.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ghajar-styled landing for externally opened configs: content is detected,
 * imported and connected immediately through the quick-connect pipeline —
 * no extra chooser step (the separate "toolkit" path was removed).
 */
class ConfigCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        if (uri != null) {
            startActivity(android.content.Intent(this, MainActivity::class.java).setAction(android.content.Intent.ACTION_VIEW).setData(uri)
                .addFlags(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        finish()
    }
}
