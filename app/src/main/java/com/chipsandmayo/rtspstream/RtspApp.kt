package com.chipsandmayo.rtspstream

import android.app.Application
import com.google.android.material.color.DynamicColors

class RtspApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Material You: match the user's wallpaper colours on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
