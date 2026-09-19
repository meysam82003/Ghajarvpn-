package net.gozar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one card in the app.
 *
 * Before this, "a card" meant whatever each screen decided: a 24dp corner
 * here, an 18dp one there, a tinted container on one page and a bare
 * Material default on the next, some elevated and some not. That is how a
 * redesign ends up looking like six designs.
 *
 * This is the single definition: the theme's card surface, a hairline border,
 * [GhajarRadius.md] corners and no elevation. [accent] swaps the hairline (and
 * nothing else) for a state colour, which is how a card says "pending" or
 * "failed" without becoming a differently shaped object.
 */
@Composable
fun GhajarCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    /** Raised sheets (dialogs, sheets) sit on surface, content cards on card. */
    raised: Boolean = false,
    radius: Dp = GhajarRadius.md,
    padding: Dp = GhajarSpacing.lg,
    spacing: Dp = GhajarSpacing.sm,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = ghajarColors
    val shape = RoundedCornerShape(radius)
    val colors = CardDefaults.cardColors(containerColor = if (raised) c.surface else c.card)
    val border = BorderStroke(1.dp, accent ?: c.border)
    val elevation = CardDefaults.cardElevation(0.dp)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors,
            border = border, elevation = elevation) { body() }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors,
            border = border, elevation = elevation) { body() }
    }
}
