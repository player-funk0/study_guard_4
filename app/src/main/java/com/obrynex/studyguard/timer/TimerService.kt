package com.obrynex.studyguard.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.obrynex.studyguard.debug.DebugAgentLog
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit

/**
 * Foreground service that runs the study timer in the background.
 *
 * Features:
 *  - Countdown timer with 1-second tick updates
 *  - Foreground notification showing remaining time
 *  - Notification actions: Pause / Resume / Stop
 *  - Broadcasts timer state back to [TimerViewModel]
 *  - Handles both study and break timers
 *  - Automatically stops when countdown reaches zero
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.obrynex.studyguard.timer.START"
        const val ACTION_PAUSE = "com.obrynex.studyguard.timer.PAUSE"
        const val ACTION_RESUME = "com.obrynex.studyguard.timer.RESUME"
        const val ACTION_STOP = "com.obrynex.studyguard.timer.STOP"
        const val ACTION_START_BREAK = "com.obrynex.studyguard.timer.START_BREAK"

        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
        const val EXTRA_IS_BREAK = "is_break"

        const val BROADCAST_TICK = "com.obrynex.studyguard.timer.TICK"
        const val BROADCAST_COMPLETE = "com.obrynex.studyguard.timer.COMPLETE"
        const val BROADCAST_STOPPED = "com.obrynex.studyguard.timer.STOPPED"

        const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Int = 0
    private var totalSeconds: Int = 0
    private var isPaused: Boolean = false
    private var isBreak: Boolean = false
    private var subject: String = ""

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        TimerNotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // #region agent log
        DebugAgentLog.log(
            location = "TimerService.kt:onStartCommand",
            message = "Service command received",
            hypothesisId = "B",
            data = mapOf(
                "action" to (intent?.action ?: "null"),
                "sdkInt" to android.os.Build.VERSION.SDK_INT
            )
        )
        // #endregion
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60)
                subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
                isBreak = false
                startTimer(duration)
            }
            ACTION_START_BREAK -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 5 * 60)
                isBreak = true
                startTimer(duration)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        // START_NOT_STICKY: if the OS kills this service (Doze/low-memory),
        // it must NOT restart automatically with a null intent.
        // The timer would silently vanish anyway — the user re-starts it manually.
        return START_NOT_STICKY
    }

    // ── Timer logic ─────────────────────────────────────────────────────────

    private fun startTimer(seconds: Int) {
        countDownTimer?.cancel()
        remainingSeconds = seconds
        totalSeconds = seconds
        isPaused = false

        sendInternalBroadcast(BROADCAST_TICK) {
            putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            putExtra(EXTRA_IS_BREAK, isBreak)
        }

        // #region agent log
        DebugAgentLog.log(
            location = "TimerService.kt:startTimer",
            message = "Calling startForeground",
            hypothesisId = "B",
            data = mapOf("remainingSeconds" to remainingSeconds, "isBreak" to isBreak)
        )
        // #endregion
        startForeground(NOTIFICATION_ID, buildNotification())
        runCountdown()
    }

    private fun runCountdown() {
        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished).toInt() + 1
                updateNotification()
                sendInternalBroadcast(BROADCAST_TICK) {
                    putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                    putExtra(EXTRA_IS_BREAK, isBreak)
                }
            }

            override fun onFinish() {
                remainingSeconds = 0
                onTimerComplete()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (isPaused) return
        isPaused = true
        countDownTimer?.cancel()
        updateNotification()
    }

    private fun resumeTimer() {
        if (!isPaused) return
        isPaused = false
        runCountdown()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        sendInternalBroadcast(BROADCAST_STOPPED)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onTimerComplete() {
        sendInternalBroadcast(BROADCAST_COMPLETE) {
            putExtra(EXTRA_IS_BREAK, isBreak)
        }

        if (!isBreak) {
            TimerNotificationHelper.showCompletionNotification(this)
        }

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Broadcasts only inside this app package to avoid unsafe implicit launches.
     */
    private inline fun sendInternalBroadcast(
        action: String,
        fillExtras: Intent.() -> Unit = {}
    ) {
        val intent = Intent(action).setPackage(packageName).apply(fillExtras)
        sendBroadcast(intent)
    }

    // ── Notification helpers ────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val title = if (isBreak) "Break Time" else subject.ifBlank { "Study Session" }
        val text = formatTime(remainingSeconds)
        return TimerNotificationHelper.buildTimerNotification(
            context = this,
            title = title,
            text = "Remaining: $text",
            showPause = !isPaused && !isBreak,
            showResume = isPaused && !isBreak,
            showStop = true
        )
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        serviceScope.cancel()
    }
}
