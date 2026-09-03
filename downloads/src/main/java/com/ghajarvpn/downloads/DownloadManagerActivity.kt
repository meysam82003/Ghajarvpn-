package com.ghajarvpn.downloads

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*

class DownloadManagerActivity : android.app.Activity() {
    private lateinit var repository: DownloadRepository
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DownloadRepository.get(this)
        setContentView(buildUi())
    }

    override fun onResume() { super.onResume(); handler.post(refresher) }
    override fun onPause() { handler.removeCallbacks(refresher); super.onPause() }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(7, 27, 46)); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@DownloadManagerActivity).apply { text = "وزیر خزانه · مدیر دانلود"; textSize = 21f; setTextColor(Color.rgb(248, 237, 210)) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@DownloadManagerActivity).apply { text = "+ لینک"; setOnClickListener { showAddDialog() } })
        })
        root.addView(TextView(this).apply {
            text = "Range واقعی، ادامه پس از بسته‌شدن برنامه و بررسی SHA-256"; setTextColor(Color.LTGRAY); setPadding(0, dp(8), 0, dp(12))
        })
        empty = TextView(this).apply { text = "صف دانلود خالی است"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(Color.LTGRAY); setPadding(0, dp(60), 0, dp(20)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(empty); addView(list) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun render() {
        val tasks = repository.all()
        empty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        list.removeAllViews()
        tasks.forEach { list.addView(taskCard(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
    }

    private fun taskCard(task: DownloadTask): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(Color.rgb(19, 51, 75))
        addView(TextView(context).apply { text = task.fileName; textSize = 16f; setTextColor(Color.WHITE); maxLines = 2 })
        val progress = if (task.totalBytes > 0) (task.downloadedBytes * 100 / task.totalBytes).coerceIn(0, 100).toInt() else 0
        addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = task.totalBytes <= 0 && task.state in ACTIVE; this.progress = progress }, LinearLayout.LayoutParams(-1, dp(14)))
        addView(TextView(context).apply {
            val total = if (task.totalBytes > 0) GhajarDownloadService.formatBytes(task.totalBytes) else "نامشخص"
            text = "${stateLabel(task)} · ${GhajarDownloadService.formatBytes(task.downloadedBytes)} / $total" +
                    if (task.speedBytesPerSecond > 0) " · ${GhajarDownloadService.formatBytes(task.speedBytesPerSecond)}/s" else ""
            setTextColor(Color.LTGRAY); setPadding(0, dp(5), 0, 0)
        })
        if (task.error.isNotBlank()) addView(TextView(context).apply { text = task.error; setTextColor(Color.rgb(255, 190, 130)) })
        addView(LinearLayout(context).apply {
            gravity = Gravity.END
            when (task.state) {
                DownloadState.QUEUED, DownloadState.PROBING, DownloadState.DOWNLOADING -> addView(actionButton("توقف") { command(task.id, DownloadContract.ACTION_PAUSE) })
                DownloadState.PAUSED, DownloadState.FAILED -> addView(actionButton("ادامه") { command(task.id, DownloadContract.ACTION_RESUME) })
                else -> Unit
            }
            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELED)) addView(actionButton("لغو") { command(task.id, DownloadContract.ACTION_CANCEL) })
            if (task.state in setOf(DownloadState.COMPLETED, DownloadState.CANCELED, DownloadState.FAILED)) addView(actionButton("حذف رکورد") {
                repository.remove(task.id); (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(task.id.hashCode()); render()
            })
        })
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 12f; setOnClickListener { action() } }

    private fun showAddDialog() {
        val url = EditText(this).apply { hint = "https://example.com/file"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        val checksum = EditText(this).apply { hint = "SHA-256 اختیاری"; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        val connections = Spinner(this).apply { adapter = ArrayAdapter(this@DownloadManagerActivity, android.R.layout.simple_spinner_dropdown_item, listOf("هوشمند", "1 اتصال", "2 اتصال", "4 اتصال", "6 اتصال", "8 اتصال")) }
        val wifi = CheckBox(this).apply { text = "فقط Wi-Fi بدون محدودیت مصرف" }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0); addView(url); addView(checksum); addView(connections); addView(wifi) }
        val dialog = AlertDialog.Builder(this).setTitle("افزودن دانلود").setView(content).setNegativeButton("لغو", null)
            .setPositiveButton("افزودن", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = url.text.toString().trim()
                val selected = listOf(0, 1, 2, 4, 6, 8)[connections.selectedItemPosition]
                val task = repository.enqueue(EnqueueRequest(raw, DownloadNames.sanitize(raw), "application/octet-stream", emptyMap(), selected, wifiOnly = wifi.isChecked, expectedSha256 = checksum.text.toString()))
                if (task == null) url.error = "فقط نشانی معتبر HTTP/HTTPS پذیرفته می‌شود"
                else { GhajarDownloadService.start(this); dialog.dismiss(); render() }
            }
        }
        dialog.show()
    }

    private fun command(id: String, action: String) { GhajarDownloadService.start(this, action, id); handler.postDelayed({ render() }, 150) }

    private fun stateLabel(task: DownloadTask) = when (task.state) {
        DownloadState.QUEUED -> "در صف"
        DownloadState.PROBING -> "بررسی سرور"
        DownloadState.DOWNLOADING -> "در حال دانلود · ${task.activeConnections} اتصال"
        DownloadState.PAUSED -> "متوقف"
        DownloadState.COMPLETED -> "کامل"
        DownloadState.FAILED -> "ناموفق"
        DownloadState.CANCELED -> "لغوشده"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private companion object { val ACTIVE = setOf(DownloadState.QUEUED, DownloadState.PROBING, DownloadState.DOWNLOADING) }
}
