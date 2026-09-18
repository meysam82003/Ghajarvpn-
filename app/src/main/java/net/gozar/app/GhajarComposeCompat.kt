package net.gozar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.widthIn as composeWidthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    /**
     * The design system puts a hairline on every raised surface, so the alias
     * has to be able to carry one. Null keeps the old borderless behaviour for
     * the existing call sites.
     */
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = shape,
        color = color,
        border = border,
        content = content
    )
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
\n

/**
 * The app's text field, standing in for Material's outlined one.
 *
 * Twenty-two call sites across settings, tools, SSH and share were still
 * drawing Material's notched outline inside slabs that have no borders of their
 * own, which is most of what made those pages read as the old app wearing the
 * new one's colours. Rather than edit every site, this package-local overload
 * takes their exact arguments and renders the skin's filled field: same value,
 * same label, same keyboard, no stroke.
 *
 * `shape` is accepted and ignored on purpose - callers pass a rounded corner to
 * soften Material's outline, and there is no outline any more.
 */
@Composable
internal fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: (@Composable () -> Unit)? = null
) {
    val c = ghajarColors
    var focused by remember { mutableStateOf(false) }
    val underline = when {
        isError -> c.error
        focused -> c.primary
        else -> Color.Transparent
    }
    val captionColor = if (isError) c.error else c.textSecondary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)) {
        if (label != null) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(color = captionColor),
                LocalContentColor provides captionColor
            ) { label() }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.md))
                .background(if (enabled) c.secondaryCard else c.disabled.copy(alpha = 0.25f))
                .drawBehind {
                    if (underline != Color.Transparent) {
                        val h = 2.dp.toPx()
                        drawRect(
                            color = underline,
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h)
                        )
                    }
                }
                .padding(horizontal = GhajarSpacing.md, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides c.textMuted) { leadingIcon() }
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = c.textMuted),
                        LocalContentColor provides c.textMuted
                    ) { placeholder() }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    textStyle = (textStyle ?: MaterialTheme.typography.bodyMedium).copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            if (trailingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides c.textMuted) { trailingIcon() }
            }
        }
        if (supportingText != null) {
            val helpColor = if (isError) c.error else c.textMuted
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelSmall.copy(color = helpColor),
                LocalContentColor provides helpColor
            ) { supportingText() }
        }
    }
}
