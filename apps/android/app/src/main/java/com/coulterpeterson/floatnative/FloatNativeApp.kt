package com.coulterpeterson.floatnative

import android.app.Application
import com.coulterpeterson.floatnative.api.FloatplaneApi

class FloatNativeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize API Singleton
        FloatplaneApi.init(this)
    }
}
