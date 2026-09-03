package net.gozar.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class SmartConnectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = SmartConnectPreferences(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.rgb(7, 27, 46))
            addView(TextView(context).apply {
                text = "وزیر ارتباطات · اتصال هوشمند"
                textSize = 22f; setTextColor(Color.rgb(248, 237, 210)); gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(TextView(context).apply {
                text = "برای استفادهٔ معمولی Auto مناسب است. حالت‌ها فقط وزن معیارهای اندازه‌گیری‌شده را تغییر می‌دهند و تضمین کاهش پینگ یا بازشدن سرویس خاصی نمی‌دهند."
                setTextColor(Color.LTGRAY); setPadding(0, dp(10), 0, dp(14)); textSize = 14f
            })
            addView(RadioGroup(context).apply {
                SmartMode.entries.forEach { mode ->
                    addView(RadioButton(context).apply {
                        id = android.view.View.generateViewId(); tag = mode; text = "${label(mode)}\n${description(mode)}"
                        setTextColor(Color.WHITE); textSize = 15f; setPadding(dp(8), dp(8), dp(8), dp(8)); isChecked = mode == preferences.mode
                        setOnClickListener { preferences.mode = mode }
                    })
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun label(mode: SmartMode) = when (mode) {
        SmartMode.AUTO -> "Auto"
        SmartMode.FASTEST -> "سریع‌ترین"
        SmartMode.STABLE -> "پایدار"
        SmartMode.GAMING -> "بازی"
        SmartMode.DOWNLOAD -> "دانلود"
        SmartMode.BROWSING -> "وب‌گردی"
        SmartMode.STREAMING -> "استریم"
        SmartMode.EMERGENCY -> "اضطراری"
    }

    private fun description(mode: SmartMode) = when (mode) {
        SmartMode.AUTO -> "تعادل latency، پایداری و سابقهٔ موفق"
        SmartMode.FASTEST -> "اولویت بیشتر برای latency اندازه‌گیری‌شده"
        SmartMode.STABLE -> "اولویت jitter، packet loss و success rate"
        SmartMode.GAMING -> "latency و jitter پایین؛ تعویض فقط با برتری روشن"
        SmartMode.DOWNLOAD -> "throughput واقعی در صورت وجود، همراه با پایداری"
        SmartMode.BROWSING -> "تعادل پاسخ‌گویی و موفقیت اتصال"
        SmartMode.STREAMING -> "throughput و packet loss؛ بدون ادعای سرویس خاص"
        SmartMode.EMERGENCY -> "استفاده از گزینه‌های سالم و fallbackهای اخیر با محدودیت"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
