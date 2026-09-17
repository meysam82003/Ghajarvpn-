package net.gozar.app

import java.util.Locale

internal enum class GhajarLinkState {
    PENDING, LINKED, EXPIRED, NOT_FOUND, FORCE_JOIN, PHONE_REQUIRED,
    SERVER_ERROR, NETWORK_ERROR, STORAGE_ERROR, SUPERSEDED
}

/** Pure interpretation of the uploaded bot's WebLink/VerifyHandler contract. */
internal object GhajarLinkFlow {
    fun bearer(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("null", true) && !it.equals("undefined", true) &&
            it.none { char -> char.isWhitespace() || char.isISOControl() }
    }

    fun responseState(status: String?, gate: String?, token: String?): GhajarLinkState =
        when (status?.lowercase(Locale.ROOT)) {
            "pending" -> GhajarLinkState.PENDING
            "expired" -> GhajarLinkState.EXPIRED
            "not_found" -> GhajarLinkState.NOT_FOUND
            "linked" -> when (gate) {
                "force_join" -> GhajarLinkState.FORCE_JOIN
                "phone_required" -> GhajarLinkState.PHONE_REQUIRED
                null, "" -> if (bearer(token) != null) GhajarLinkState.LINKED else GhajarLinkState.SERVER_ERROR
                else -> GhajarLinkState.SERVER_ERROR
            }
            else -> GhajarLinkState.SERVER_ERROR
        }

    fun remainingSeconds(expiresAtMillis: Long, nowMillis: Long): Int =
        ((expiresAtMillis - nowMillis).coerceIn(0, 900_000) + 999).div(1_000).toInt()

    fun retryDelayMillis(failures: Int): Long = when (failures) {
        in Int.MIN_VALUE..0 -> 2_000L
        1 -> 4_000L
        2 -> 8_000L
        3 -> 16_000L
        else -> 30_000L
    }

    fun invalidatesAccount(authenticated: Boolean, httpCode: Int): Boolean =
        authenticated && (httpCode == 401 || httpCode == 403)

    fun verificationGate(previous: GhajarLinkState?, current: GhajarLinkState): GhajarLinkState? = when (current) {
        GhajarLinkState.FORCE_JOIN, GhajarLinkState.PHONE_REQUIRED -> current
        GhajarLinkState.NETWORK_ERROR, GhajarLinkState.SERVER_ERROR, GhajarLinkState.STORAGE_ERROR -> previous
        else -> null
    }

    fun message(state: GhajarLinkState): String = when (state) {
        GhajarLinkState.PENDING -> "منتظر تأیید ربات هستیم؛ در تلگرام «Start / شروع» را بزن و برگرد."
        GhajarLinkState.LINKED -> "حساب با موفقیت و به‌صورت امن متصل شد"
        GhajarLinkState.EXPIRED -> "مهلت کد اتصال تمام شد؛ یک کد تازه بگیر."
        GhajarLinkState.NOT_FOUND -> "این درخواست اتصال در سرور پیدا نشد؛ یک کد تازه بگیر."
        GhajarLinkState.FORCE_JOIN -> "ربات منتظر عضویت کانال است؛ مراحل عضویت را در ربات کامل کن و سپس «بررسی دوباره» را بزن."
        GhajarLinkState.PHONE_REQUIRED -> "ربات منتظر تأیید شماره است؛ تأیید را فقط داخل ربات انجام بده و سپس «بررسی دوباره» را بزن."
        GhajarLinkState.NETWORK_ERROR -> "ارتباط با سرور برقرار نشد؛ اینترنت را بررسی کن. تا پایان مهلت، دوباره تلاش می‌کنیم."
        GhajarLinkState.SERVER_ERROR -> "سرور هنوز ورود را کامل نکرده است؛ کد را نگه داشته‌ایم و دوباره بررسی می‌کنیم."
        GhajarLinkState.STORAGE_ERROR -> "ذخیرهٔ امن حساب در گوشی انجام نشد؛ دوباره بررسی کن."
        GhajarLinkState.SUPERSEDED -> "این درخواست اتصال دیگر فعال نیست؛ یک کد تازه بگیر."
    }
}
