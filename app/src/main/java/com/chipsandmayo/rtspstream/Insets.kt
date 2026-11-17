package com.chipsandmayo.rtspstream

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15+ draws apps edge-to-edge, under the status/navigation bars.
 * Adds the system bar / notch / keyboard insets on top of the view's own padding.
 */
fun View.padForSystemBars(includeIme: Boolean = true) {
    val start = paddingLeft
    val top = paddingTop
    val end = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        v.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
        insets
    }
}
