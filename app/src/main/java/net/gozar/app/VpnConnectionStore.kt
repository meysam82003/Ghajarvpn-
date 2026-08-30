package net.gozar.app

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** File-backed connection state shared by the UI and :vpn processes.
 * SharedPreferences is intentionally avoided because Android does not provide
 * coherent multi-process preference caches. */
internal object VpnConnectionStore {
    data class Snapshot(
        val state: Connection = Connection.DISCONNECTED,
        val activeId: String? = null,
        val error: String? = null,
        val updatedAt: Long = 0L
    )

    @Synchronized
    fun write(context: Context, state: Connection, activeId: String?, error: String?) {
        val file = atomic(context)
        val output = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            val json = JSONObject().put("state", state.name)
                .put("active_id", activeId ?: JSONObject.NULL)
                .put("error", error ?: JSONObject.NULL)
                .put("updated_at", System.currentTimeMillis())
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            file.finishWrite(output)
        } catch (_: Exception) {
            file.failWrite(output)
        }
        requestTile(context)
        runCatching { GhajarWidgetProvider.updateEveryWidget(context.applicationContext) }
    }

    @Synchronized
    fun read(context: Context): Snapshot = runCatching {
        val bytes = atomic(context).readFully()
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        Snapshot(
            state = runCatching { Connection.valueOf(json.optString("state")) }
                .getOrDefault(Connection.DISCONNECTED),
            activeId = json.optString("active_id").takeUnless { it.isBlank() || it == "null" },
            error = json.optString("error").takeUnless { it.isBlank() || it == "null" },
            updatedAt = json.optLong("updated_at")
        )
    }.getOrDefault(Snapshot())

    private fun atomic(context: Context) = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    private fun requestTile(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching {
            TileService.requestListeningState(context,
                ComponentName(context, QsTileService::class.java))
        }
    }

    private const val FILE_NAME = "ghajar-vpn-state.json"
}
