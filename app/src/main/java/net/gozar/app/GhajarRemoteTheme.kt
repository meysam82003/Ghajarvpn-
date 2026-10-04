package net.gozar.app

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb

/** Same persisted palette as Compose, applied when the launcher inflates RemoteViews. */
object GhajarRemoteTheme {
    fun widget(context: Context, views: RemoteViews, small: Boolean = false) {
        val p=ghajarPaletteFor(context);val accent=p.primary.toArgb()
        views.setInt(if(small) R.id.widget_small_root else R.id.widget_root,"setBackgroundColor",p.surface.toArgb())
        views.setInt(if(small) R.id.widget_small_button else R.id.widget_brand,"setColorFilter",accent)
        if(small)return
        for(id in intArrayOf(R.id.widget_state,R.id.widget_toggle))views.setTextColor(id,p.textPrimary.toArgb())
        for(id in intArrayOf(R.id.widget_server,R.id.widget_up,R.id.widget_down,R.id.widget_list_empty))views.setTextColor(id,p.textSecondary.toArgb())
        for(id in intArrayOf(R.id.widget_ping,R.id.widget_ping_btn,R.id.widget_subs_btn,R.id.widget_next_btn))views.setTextColor(id,accent)
        views.setInt(R.id.widget_toggle,"setBackgroundColor",p.secondaryCard.toArgb())
    }
    fun row(context: Context, views: RemoteViews) {
        val p=ghajarPaletteFor(context)
        views.setTextColor(R.id.widget_row_name,p.textPrimary.toArgb())
        views.setTextColor(R.id.widget_row_proto,p.textSecondary.toArgb())
        views.setTextColor(R.id.widget_row_ping,p.primary.toArgb())
    }
    fun refresh(context: Context) { GhajarWidget.refresh(context); GhajarQuickControls.refresh(context) }
}
