package net.gozar.app

/** Error UI accepts product copy, never Throwable.message, URLs, hosts or response bodies. */
object StorePublicError {
    fun message(error: Throwable?, fallback: String = "عملیات کامل نشد؛ اتصال اینترنت را بررسی کن و دوباره تلاش کن."): String = when (error) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "اتصال به فروشگاه برقرار نشد؛ اینترنت را بررسی کن و دوباره تلاش کن."
        is java.net.SocketTimeoutException -> "پاسخ فروشگاه به‌موقع دریافت نشد؛ دوباره تلاش کن."
        is javax.net.ssl.SSLException -> "اتصال امن به فروشگاه تأیید نشد؛ بعداً دوباره تلاش کن."
        else -> fallback
    }
}
