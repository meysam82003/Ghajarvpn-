package net.gozar.app.configtoolkit

import net.gozar.app.ProxyConfig

/**
 * The one decoding path for every imported file, used by the main screen and
 * the config toolkit alike.
 *
 * Its outcomes are distinct on purpose. Only [Outcome.NeedsPasskey] may open a
 * password dialog: a file locked with another app's own key, a file sealed to
 * a recipient, an unsupported format or a damaged file each gets its own
 * message - never "this file has a password".
 */
object ImportRouter {

    sealed class Outcome {
        data class Imported(val configs: List<ProxyConfig>, val format: ConfigFormat, val warnings: List<String>) : Outcome()
        object NeedsPasskey : Outcome()
        object WrongPasskey : Outcome()
        /** Locked with the issuing app's key or to a recipient; no password can open it. */
        data class Locked(val message: String) : Outcome()
        data class Unsupported(val message: String) : Outcome()
        data class Invalid(val message: String) : Outcome()
    }

    /** Ghajar's own export/backup container ("GRT1"), which keeps its own path. */
    fun isGhajarConfigFile(bytes: ByteArray): Boolean =
        bytes.size >= 5 && String(bytes, 0, 4, Charsets.US_ASCII) == "GRT1"

    fun decode(bytes: ByteArray, displayName: String = "import", passkey: CharArray? = null,
               registry: DecoderRegistry = DecoderRegistry()): Outcome = try {
        val parsed = registry.decode(ConfigInput(bytes, displayName, passkey = passkey))
        val configs = parsed.profiles.map { it.toProxyConfig() }
        if (configs.isEmpty()) Outcome.Invalid("کانفیگ قابل استفاده‌ای در فایل پیدا نشد.")
        else Outcome.Imported(configs, parsed.sourceFormat, parsed.warnings)
    } catch (e: ConfigToolkitException.PasskeyRequired) {
        Outcome.NeedsPasskey
    } catch (e: ConfigToolkitException.WrongPasskey) {
        Outcome.WrongPasskey
    } catch (e: ConfigToolkitException.VendorLocked) {
        Outcome.Locked(e.message.orEmpty())
    } catch (e: ConfigToolkitException.Locked) {
        Outcome.Locked(e.message.orEmpty())
    } catch (e: ConfigToolkitException.UnsupportedFormat) {
        Outcome.Unsupported(e.message.orEmpty())
    } catch (e: ConfigToolkitException) {
        Outcome.Invalid(e.message.orEmpty())
    } catch (e: Exception) {
        Outcome.Invalid("فایل خوانده نشد (${e.javaClass.simpleName}).")
    }
}
