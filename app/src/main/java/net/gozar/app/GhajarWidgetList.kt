package net.gozar.app

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.concurrent.ConcurrentHashMap

/**
 * The widget's config list: the configs of the subscription the selected
 * config belongs to (or every config when it has none), with the last ping the
 * widget measured for each. Rows fill in the config id; the provider's
 * ACTION_SELECT selects it and connects.
 */
class GhajarWidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val app: Context) : RemoteViewsFactory {
        private var rows: List<ProxyConfig> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true

        override fun onDataSetChanged() {
            rows = runCatching { WidgetConfigs.visible(app) }.getOrDefault(emptyList())
        }

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(app.packageName, R.layout.widget_config_row)
            GhajarRemoteTheme.row(app, v)
            val c = rows.getOrNull(position) ?: return v
            val lang = runCatching { ConfigStore.get(app).lang.value }.getOrNull() ?: Lang.FA
            val active = VpnState.activeId.value
            val selected = active ?: runCatching { ConfigStore.get(app).selectedId.value }.getOrNull()
            v.setTextViewText(R.id.widget_row_name, BrandConfig.sanitizePublicText(c.name))
            v.setTextViewText(R.id.widget_row_proto, c.protocol.uppercase())
            v.setTextViewText(R.id.widget_row_ping, when (val ms = WidgetConfigs.pings[c.id]) {
                null -> ""
                -1 -> "×"
                else -> localizeDigits("$ms ms", lang)
            })
            v.setImageViewResource(R.id.widget_row_dot, when {
                c.id == active && VpnState.state.value == Connection.CONNECTED -> R.drawable.widget_dot_on
                c.id == selected -> R.drawable.widget_dot_busy
                else -> R.drawable.widget_dot_off
            })
            v.setOnClickFillInIntent(R.id.widget_row, Intent().putExtra(GhajarWidget.EXTRA_CONFIG_ID, c.id))
            return v
        }
    }
}

/** What the widget's list shows, and the pings it has measured. */
object WidgetConfigs {
    /** Config id -> last widget-measured delay in ms, -1 for a failed probe. */
    val pings = ConcurrentHashMap<String, Int>()

    private const val MAX_ROWS = 80

    fun visible(context: Context): List<ProxyConfig> {
        val store = ConfigStore.get(context.applicationContext)
        val all = store.configs.value
        val selected = all.firstOrNull { it.id == (VpnState.activeId.value ?: store.selectedId.value) }
        val sub = selected?.subId.orEmpty()
        val list = if (sub.isNotEmpty()) all.filter { it.subId == sub } else all
        return list.take(MAX_ROWS)
    }
}
