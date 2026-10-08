package net.gozar.app.configtoolkit

import net.gozar.app.gsb2.Gsb2

/** Imports a GSB2 secure share; the share's limits travel with each config. */
class Gsb2Decoder : ConfigDecoder {
    override val format = ConfigFormat.GSB2

    override fun decode(input: ConfigInput): ParsedConfig {
        val share = when (val r = Gsb2.open(input.bytes, input.passkey)) {
            is Gsb2.OpenResult.Ok -> r.share
            Gsb2.OpenResult.NeedsPassword -> throw ConfigToolkitException.PasskeyRequired()
            Gsb2.OpenResult.WrongPassword -> throw ConfigToolkitException.WrongPasskey()
            is Gsb2.OpenResult.Invalid -> throw ConfigToolkitException.InvalidConfig(r.reason)
        }
        if (share.expiresAt in 1..System.currentTimeMillis()) throw ConfigToolkitException.InvalidConfig("اعتبار این اشتراک GSB2 تمام شده است.")
        val profiles = Gsb2.receivedConfigs(share).map { NormalizedProfile.from(it, ConfigFormat.GSB2) }
        val warnings = listOfNotNull(share.note.takeIf { it.isNotBlank() }?.let { "یادداشت سازنده: $it" })
        return ParsedConfig(ConfigFormat.GSB2, profiles, null, null, warnings)
    }

    override fun validate(parsed: ParsedConfig): ValidationResult = ValidationResult(emptyList())
}
