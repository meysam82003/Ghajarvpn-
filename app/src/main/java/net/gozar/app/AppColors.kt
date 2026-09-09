package net.gozar.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-aware "connected/healthy" accent color used across the UI (connect
 * button, health checks, tunnel status). This symbol was referenced throughout
 * MainActivity.kt but its source file was missing from the project export;
 * restored here verbatim from the sibling GRoute codebase.
 */
internal val AppGreen: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF4BF0A4)
        else Color(0xFF0B8F53)
