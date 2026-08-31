package net.gozar.app

import android.content.Context

/** Offline artwork only: no host, bot gallery or remote boot request. */
internal object GhajarWelcomeAssets {
    data class Poster(val name: String, val resourceId: Int, val dark: Boolean)
    data class Content(val title: String, val body: String)
    val posters = listOf(
        Poster("ghajar_welcome_world", R.drawable.ghajar_welcome_world, false),
        Poster("ghajar_welcome_connection", R.drawable.ghajar_welcome_connection, false),
        Poster("ghajar_welcome_locations", R.drawable.ghajar_welcome_locations, false),
        Poster("ghajar_welcome_royal", R.drawable.ghajar_welcome_royal, true),
        Poster("ghajar_welcome_queen_phone", R.drawable.ghajar_welcome_queen_phone, false),
        Poster("ghajar_welcome_king_night", R.drawable.ghajar_welcome_king_night, true),
        Poster("ghajar_welcome_king_light", R.drawable.ghajar_welcome_king_light, false),
        Poster("ghajar_welcome_queen_shield", R.drawable.ghajar_welcome_queen_shield, true),
        Poster("ghajar_welcome_queen_night", R.drawable.ghajar_welcome_queen_night, true),
        Poster("ghajar_welcome_king_world", R.drawable.ghajar_welcome_king_world, false),
        Poster("ghajar_welcome_queen_light", R.drawable.ghajar_welcome_queen_light, false),
        Poster("ghajar_welcome_extra_01", R.drawable.ghajar_welcome_extra_01, true),
        Poster("ghajar_welcome_extra_02", R.drawable.ghajar_welcome_extra_02, true),
        Poster("ghajar_welcome_extra_03", R.drawable.ghajar_welcome_extra_03, true),
        Poster("ghajar_welcome_extra_04", R.drawable.ghajar_welcome_extra_04, true),
        Poster("ghajar_welcome_extra_05", R.drawable.ghajar_welcome_extra_05, false),
        Poster("ghajar_welcome_extra_06", R.drawable.ghajar_welcome_extra_06, true),
        Poster("ghajar_welcome_extra_07", R.drawable.ghajar_welcome_extra_07, true),
        Poster("ghajar_welcome_extra_08", R.drawable.ghajar_welcome_extra_08, false),
        Poster("ghajar_welcome_extra_09", R.drawable.ghajar_welcome_extra_09, true),
        Poster("ghajar_welcome_extra_10", R.drawable.ghajar_welcome_extra_10, true),
        Poster("ghajar_welcome_extra_11", R.drawable.ghajar_welcome_extra_11, false),
        Poster("ghajar_welcome_extra_12", R.drawable.ghajar_welcome_extra_12, true),
        Poster("ghajar_welcome_extra_13", R.drawable.ghajar_welcome_extra_13, true),
        Poster("ghajar_welcome_extra_14", R.drawable.ghajar_welcome_extra_14, true),
        Poster("ghajar_welcome_extra_15", R.drawable.ghajar_welcome_extra_15, true),
        Poster("ghajar_welcome_extra_16", R.drawable.ghajar_welcome_extra_16, false),
        Poster("ghajar_welcome_extra_17", R.drawable.ghajar_welcome_extra_17, false),
        Poster("ghajar_welcome_extra_18", R.drawable.ghajar_welcome_extra_18, false),
        Poster("ghajar_welcome_extra_19", R.drawable.ghajar_welcome_extra_19, false),
        Poster("ghajar_welcome_extra_20", R.drawable.ghajar_welcome_extra_20, false),
        Poster("ghajar_welcome_extra_21", R.drawable.ghajar_welcome_extra_21, true),
        Poster("ghajar_welcome_extra_22", R.drawable.ghajar_welcome_extra_22, false)
    )

    private val contents = listOf(
        Content("اتصال سریع و روشن", "وضعیت اتصال را روی خانه ببینید و با یک لمس وصل یا قطع شوید."),
        Content("ساب را تازه نگه دارید", "قبل از اتصال، یک‌بار ساب را بروزرسانی کنید تا لوکیشن‌های تازه برسند."),
        Content("بهترین لوکیشن را پیدا کنید", "پینگ لوکیشن‌ها را بررسی کنید و نزدیک‌ترین مسیر را انتخاب کنید."),
        Content("کانفیگ رایگان", "بخش کانفیگ رایگان برای شروع سریع و بدون خرید در دسترس است."),
        Content("پشتیبانی OpenVPN", "پروفایل OpenVPN را وارد کنید و نتیجهٔ پینگ را پیش از اتصال ببینید."),
        Content("فروشگاه داخل برنامه", "سرویس آماده یا سفارشی را انتخاب کنید و وضعیت پرداخت را امن پیگیری کنید."),
        Content("سرویس‌های ویژه", "حجم و مدت دلخواه را بسازید و قیمت نهایی را همان لحظه ببینید."),
        Content("ویجت مرکز فرمان", "اتصال، پینگ، بروزرسانی ساب و تغییر لوکیشن از صفحهٔ خانه انجام می‌شود."),
        Content("امنیت شاهانه", "اطلاعات حساس در فضای خصوصی برنامه نگهداری و مسیر پرداخت جدا بررسی می‌شود."),
        Content("حریم خصوصی", "قاجار VPN وضعیت لازم را شفاف نشان می‌دهد و کنترل اتصال دست خود شماست.")
    )

    fun contentFor(posterName: String): Content {
        val index = posters.indexOfFirst { it.name == posterName }.coerceAtLeast(0)
        return contents[index % contents.size]
    }

    @Synchronized
    fun reserve(context: Context): String {
        val prefs = context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE)
        val pick = requireNotNull(GhajarWelcomeRotation.next(posters.map { it.name },
            prefs.getStringSet("seen_in_cycle", emptySet()).orEmpty(), prefs.getString("last_name", null)))
        // The poster must be available for the first frame; rotation persistence
        // can finish asynchronously without delaying launch rendering.
        prefs.edit().putStringSet("seen_in_cycle", pick.seen).putString("last_name", pick.name).apply()
        return pick.name
    }
}
