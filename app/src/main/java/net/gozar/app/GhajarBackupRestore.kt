package net.gozar.app

import android.content.Context
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Validate first, keep an in-memory rollback, and never include account/payment storage. */
internal object GhajarBackupRestore {
    const val MAX_BYTES = 32 * 1024 * 1024

    fun readBounded(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "حجم فایل بکاپ بیش از حد مجاز است" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun restoreOpenVpnSelection(context: Context, store: ConfigStore, backup: ConfigFile.Backup) {
        val selected = backup.settings?.optString("selectedId").orEmpty()
        if (selected.startsWith("ovpn:") && GhajarOpenVpnBridge.profiles(context).any {
                it.uuid == selected.removePrefix("ovpn:")
            }) store.setSelectedId(selected)
    }

    suspend fun apply(context: Context, store: ConfigStore, backup: ConfigFile.Backup, merge: Boolean): String {
        check(VpnState.state.value == Connection.DISCONNECTED || VpnState.state.value == Connection.ERROR) {
            "پیش از بازیابی، اتصال VPN را قطع کنید."
        }
        val previousConfigs = store.configs.value
        val previousSubs = store.subscriptions.value
        val previousSettings = store.settingsSnapshot()
        val previous = withContext(Dispatchers.IO) {
            // Existing AES-GCM format carries all supported local data for rollback.
            val bytes = ConfigFile.encodeBackup(context, previousConfigs, previousSubs, previousSettings, null)
            ConfigFile.decodeBackup(context, bytes, null)
        }
        check(previous.openVpnProfiles.size == GhajarOpenVpnBridge.profiles(context).size) {
            "تهیهٔ نسخهٔ بازگشت از پروفایل‌های فعلی کامل نشد؛ اطلاعات تغییر نکرد."
        }
        try {
            val profiles = withContext(Dispatchers.IO) {
                GhajarOpenVpnBridge.importProfiles(context, backup.openVpnProfiles, merge)
            }
            check(profiles.failed == 0) { "بازیابی پروفایل‌های OpenVPN کامل نشد." }
            if (merge) {
                val report = store.mergeBackup(backup.configs, backup.subs)
                return "افزوده شد: ${report.addedConfigs} کانفیگ، ${report.addedSubscriptions} اشتراک، ${profiles.added} پروفایل OpenVPN"
            }
            store.restoreBackup(backup.configs, backup.subs, backup.settings)
            restoreOpenVpnSelection(context, store, backup)
            withContext(Dispatchers.IO) {
                GhajarOpenVpnSettings.restore(context, backup.openVpnSettings)
                NetworkRules.restore(context, backup.networkRules)
            }
            return "بازیابی شد: ${backup.configs.size} کانفیگ، ${backup.subs.size} اشتراک، ${profiles.added} پروفایل OpenVPN"
        } catch (failure: Exception) {
            val rollback = withContext(NonCancellable) { runCatching {
                store.restoreBackup(previous.configs, previous.subs, previous.settings)
                withContext(Dispatchers.IO) {
                    GhajarOpenVpnSettings.restore(context, previous.openVpnSettings)
                    NetworkRules.restore(context, previous.networkRules)
                    check(GhajarOpenVpnBridge.importProfiles(context, previous.openVpnProfiles, false).failed == 0)
                    restoreOpenVpnSelection(context, store, previous)
                }
            } }
            if (failure is CancellationException) throw failure
            throw IllegalStateException(if (rollback.isSuccess)
                "بازیابی ناموفق بود؛ اطلاعات قبلی بازگردانده شد."
                else "بازیابی و بازگرداندن خودکار کامل نشد؛ فایل بکاپ را نگه دارید و دوباره بررسی کنید.", failure)
        }
    }
}
