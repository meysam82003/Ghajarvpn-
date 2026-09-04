package com.ghajarvpn.browser

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Real WindowInsets plumbing for the browser chrome. No fixed spacers: toolbar and
 * bottom bar paddings track system bars + display cutout + IME on every device,
 * in both portrait and landscape, with buttons or gesture navigation.
 */
object BrowserInsets {

    /**
     * Installs an insets listener on [root] that pads:
     *  - [topBar] with systemBars+cutout top (and horizontal insets for cutouts in landscape)
     *  - [bottomBar] with the maximum of navigation bottom and IME bottom (plus horizontal),
     *    so the bar rises above the keyboard and never hides under gesture navigation.
     * Per-edge maximum of systemBars and displayCutout guarantees no control ends up
     * under a status bar, notch or gesture area.
     */
    fun apply(root: View, topBar: View, bottomBar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val left = maxOf(bars.left, cutout.left)
            val right = maxOf(bars.right, cutout.right)
            val top = maxOf(bars.top, cutout.top)
            val bottom = maxOf(maxOf(bars.bottom, cutout.bottom), ime.bottom)

            topBar.updatePadding(left = left, top = top, right = right)
            bottomBar.updatePadding(left = left, bottom = bottom, right = right)
            insets
        }
        root.requestApplyInsets()
    }

    fun topInset(insets: WindowInsetsCompat): Int {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        return maxOf(bars.top, cutout.top)
    }
}
