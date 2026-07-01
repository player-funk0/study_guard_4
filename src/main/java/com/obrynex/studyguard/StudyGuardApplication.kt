package com.obrynex.studyguard

import android.app.Application
import com.obrynex.studyguard.ai.AppLogger
import com.obrynex.studyguard.di.ServiceLocator
import java.io.File

/**
 * Application class for StudyGuard.
 *
 * Initializes:
 *  - ServiceLocator (manual DI container)
 *  - AppLogger with optional file logging in debug builds
 */
class StudyGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize ServiceLocator
        ServiceLocator.init(this)

        // Enable file logging in debug builds
        if (BuildConfig.DEBUG) {
            AppLogger.enableFileLogging(File(filesDir, "ai_debug.log"))
        }
    }
}
