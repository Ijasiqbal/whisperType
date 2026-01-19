package com.whispertype.app

import android.app.Application
import com.whispertype.app.config.RemoteConfigManager
import dagger.hilt.android.HiltAndroidApp

/**
 * WhisperTypeApplication - Application class for Hilt dependency injection
 * 
 * The @HiltAndroidApp annotation triggers Hilt's code generation, including a base class
 * for the application that serves as the application-level dependency container.
 */
@HiltAndroidApp
class WhisperTypeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase Remote Config (moved from static initializers)
        // This ensures config is available before any activity starts
        RemoteConfigManager.initialize()
    }
}
