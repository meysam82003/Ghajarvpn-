package net.gozar.app.security.vault

/** Fixed user-facing errors; never interpolate payloads, credentials, paths or exception messages. */
object VaultMessages {
    fun describe(error: Throwable): String = when((error as? VaultException)?.kind) {
        VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY -> "رمز نادرست است یا بخش کلید فایل آسیب دیده است."
        VaultException.Kind.TAMPERED -> "اعتبار رمزنگاری فایل تأیید نشد؛ داده تغییر کرده یا آسیب دیده است."
        VaultException.Kind.UNSUPPORTED_VERSION -> "این نسخهٔ صندوق پشتیبانی نمی‌شود."
        VaultException.Kind.CORRUPT -> "ساختار فایل صندوق معتبر نیست."
        VaultException.Kind.INVALID_CONFIG -> "یکی از پروفایل‌های صندوق معتبر نیست."
        VaultException.Kind.EXPIRED -> "زمان این دسترسی به پایان رسیده است."
        VaultException.Kind.DUPLICATE -> "این پروفایل در صندوق وجود دارد."
        VaultException.Kind.LOCKED -> "ابتدا صندوق را باز کنید."
        VaultException.Kind.POLICY -> "این عملیات طبق سیاست فایل مجاز نیست؛ پروفایل محدودشده به فهرست عادی منتقل نمی‌شود."
        VaultException.Kind.ENGINE_UNAVAILABLE -> "موتور لازم برای این پروفایل در دسترس نیست."
        else -> "خواندن یا ذخیرهٔ صندوق انجام نشد. فایل اصلی را نگه دارید و دوباره تلاش کنید."
    }
    inline fun <T> password(value: String, block:(CharArray)->T):T {
        val chars=value.toCharArray(); return try { block(chars) } finally { chars.fill('\u0000') }
    }
}
