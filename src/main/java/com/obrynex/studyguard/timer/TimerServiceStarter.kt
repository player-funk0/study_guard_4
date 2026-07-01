package com.obrynex.studyguard.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/** Starts [TimerService] using the correct API for foreground services (API 26+). */
internal fun Context.startTimerService(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}
