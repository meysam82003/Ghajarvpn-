package net.gozar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.widthIn as composeWidthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Small Compose symbol bridge for the historical 3.0.4 snapshot files.
 *
 * The reviewed snapshots intentionally keep their original compact import
 * lists. New UI added by incremental patches uses these package-local aliases
 * so the reconstructed source remains buildable without rewriting the large
 * snapshot files just to add imports.
 */
internal typealias Brush = androidx.compose.ui.graphics.Brush

@Composable
internal fun Surface(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(shape = shape, color = color, content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

internal fun Modifier.widthIn(
    min: Dp = Dp.Unspecified,
    max: Dp = Dp.Unspecified
): Modifier = this.composeWidthIn(min = min, max = max)
