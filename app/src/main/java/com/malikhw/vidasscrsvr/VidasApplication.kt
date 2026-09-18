package com.malikhw.vidasscrsvr

import android.app.Application
import com.google.android.material.color.DynamicColors

class VidasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
