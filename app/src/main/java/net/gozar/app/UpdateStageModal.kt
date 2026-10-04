package net.gozar.app

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/** The production modal used for both available releases and release history downloads. */
@Composable
internal fun UpdateStageModal(stage: Int, title: String, confirmLabel: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    GlassDialog(onDismiss={if(stage !in 1..2) onDismiss()},dismissible=stage !in 1..2,
        title=title,confirmLabel=confirmLabel,confirmEnabled=stage!=2,
        dismissLabel=if(stage==0) "بعداً" else if(stage in 1..2) null else "بستن",
        onConfirm={if(stage!=2) onConfirm()},body=body)
}
