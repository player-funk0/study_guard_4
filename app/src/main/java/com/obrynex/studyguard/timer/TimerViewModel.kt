package com.obrynex.studyguard.timer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.data.StudySessionDao
import com.obrynex.studyguard.debug.DebugAgentLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Timer states
 */
sealed class TimerPhase {
    object Idle : TimerPhase()
    object Running : TimerPhase()
    object Paused : TimerPhase()
    object Break : TimerPhase()
    object Finished : TimerPhase()
}

/**
 * UI state for the Timer screen.
 *
 * @param durationMinutes     Selected study duration in minutes
 * @param breakMinutes        Selected break duration in minutes
 * @param remainingSeconds    Seconds left in current phase
 * @param totalSeconds        Total seconds for the current phase
 * @param phase               Current timer phase
 * @param subject             User-entered subject for the study session
 * @param completedSessions   Count of completed sessions today
 * @param isBreakEnabled      Whether break timer is enabled after study
 */
data class TimerUiState(
    val durationMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val phase: TimerPhase = TimerPhase.Idle,
    val subject: String = "",
    val completedSessions: Int = 0,
    val isBreakEnabled: Boolean = true,
    val isLoading: Boolean = false
) {
    val progress: Float get() = if (totalSeconds > 0) remainingSeconds / totalSeconds.toFloat() else 1f
    val isRunning get() = phase is TimerPhase.Running || phase is TimerPhase.Break
    val isPaused get() = phase is TimerPhase.Paused
    val isIdle get() = phase is TimerPhase.Idle
    val isFinished get() = phase is TimerPhase.Finished
    val displayMinutes: Int get() = remainingSeconds / 60
    val displaySeconds: Int get() = remainingSeconds % 60
}

/**
 * ViewModel for the Study Timer screen.
 *
 * Features:
 *  - Custom duration & break time selection
 *  - Start / Pause / Resume / Stop controls
 *  - Automatic session saving to Room when timer finishes
 *  - Tracks total paused time per session
 *  - Integrates with [TimerService] for background operation
 */
class TimerViewModel(
    application: Application,
    private val sessionDao: StudySessionDao
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    /** Accumulated paused time for the current session (ms). */
    private var pausedTimeMs: Long = 0L
    private var lastPauseStart: Long = 0L

    /** Active session ID when auto-saving. */
    private var activeSessionId: Long = 0L

    /**
     * Guard against double-save.
     *
     * stopTimer() calls sendServiceAction(STOP) which makes the Service broadcast
     * BROADCAST_STOPPED. The BroadcastReceiver then calls cleanupTimer() a second
     * time, triggering a duplicate saveSession(). This flag prevents that.
     */
    private var sessionSaved = false

    /** Broadcast receiver for service updates. */
    private var broadcastReceiver: BroadcastReceiver? = null

    init {
        loadTodayStats()
    }

    // ── User input ──────────────────────────────────────────────────────────

    fun onDurationChanged(minutes: Int) {
        if (_state.value.phase is TimerPhase.Running || _state.value.phase is TimerPhase.Paused) return
        _state.update {
            it.copy(
                durationMinutes = minutes,
                remainingSeconds = minutes * 60,
                totalSeconds = minutes * 60
            )
        }
    }

    fun onBreakChanged(minutes: Int) {
        if (_state.value.phase is TimerPhase.Running || _state.value.phase is TimerPhase.Paused) return
        _state.update { it.copy(breakMinutes = minutes) }
    }

    fun onBreakEnabledChanged(enabled: Boolean) {
        _state.update { it.copy(isBreakEnabled = enabled) }
    }

    fun onSubjectChanged(text: String) {
        _state.update { it.copy(subject = text) }
    }

    // ── Timer controls ──────────────────────────────────────────────────────

    fun startTimer() {
        val s = _state.value
        if (s.phase is TimerPhase.Running) return

        val totalSeconds = s.durationMinutes * 60
        _state.update {
            it.copy(
                phase = TimerPhase.Running,
                remainingSeconds = totalSeconds,
                totalSeconds = totalSeconds
            )
        }

        pausedTimeMs = 0L
        startService(totalSeconds, s.subject)
        registerReceiver()
    }

    fun pauseTimer() {
        if (_state.value.phase !is TimerPhase.Running) return
        lastPauseStart = System.currentTimeMillis()
        _state.update { it.copy(phase = TimerPhase.Paused) }
        sendServiceAction(TimerService.ACTION_PAUSE)
    }

    fun resumeTimer() {
        if (_state.value.phase !is TimerPhase.Paused) return
        pausedTimeMs += System.currentTimeMillis() - lastPauseStart
        _state.update { it.copy(phase = TimerPhase.Running) }
        sendServiceAction(TimerService.ACTION_RESUME)
    }

    fun togglePauseResume() {
        when (_state.value.phase) {
            is TimerPhase.Running -> pauseTimer()
            is TimerPhase.Paused -> resumeTimer()
            else -> { /* no-op */ }
        }
    }

    fun stopTimer() {
        sendServiceAction(TimerService.ACTION_STOP)
        cleanupTimer(finished = false)
    }

    /** Called when the study timer naturally completes. */
    fun onStudyComplete() {
        val s = _state.value
        saveSession(completed = true)

        if (s.isBreakEnabled && s.breakMinutes > 0) {
            val breakSeconds = s.breakMinutes * 60
            _state.update {
                it.copy(
                    phase = TimerPhase.Break,
                    remainingSeconds = breakSeconds,
                    totalSeconds = breakSeconds
                )
            }
            startBreakTimer(breakSeconds)
        } else {
            _state.update {
                it.copy(phase = TimerPhase.Finished)
            }
            loadTodayStats()
        }
    }

    /** Called when the break timer completes. */
    fun onBreakComplete() {
        _state.update { it.copy(phase = TimerPhase.Finished) }
        loadTodayStats()
    }

    /** Resets the timer to idle state. */
    fun resetTimer() {
        val s = _state.value
        _state.update {
            it.copy(
                phase = TimerPhase.Idle,
                remainingSeconds = s.durationMinutes * 60,
                totalSeconds = s.durationMinutes * 60
            )
        }
        pausedTimeMs = 0L
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun startService(totalSeconds: Int, subject: String) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION_SECONDS, totalSeconds)
            putExtra(TimerService.EXTRA_SUBJECT, subject)
        }
        // #region agent log
        DebugAgentLog.log(
            location = "TimerViewModel.kt:startService",
            message = "Starting study timer foreground service",
            hypothesisId = "A",
            data = mapOf("totalSeconds" to totalSeconds, "sdkInt" to android.os.Build.VERSION.SDK_INT)
        )
        // #endregion
        context.startTimerService(intent)
    }

    private fun startBreakTimer(breakSeconds: Int) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START_BREAK
            putExtra(TimerService.EXTRA_DURATION_SECONDS, breakSeconds)
        }
        // #region agent log
        DebugAgentLog.log(
            location = "TimerViewModel.kt:startBreakTimer",
            message = "Starting break timer foreground service",
            hypothesisId = "A",
            data = mapOf("breakSeconds" to breakSeconds)
        )
        // #endregion
        context.startTimerService(intent)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
        }
        context.startTimerService(intent)
    }

    private fun registerReceiver() {
        unregisterReceiver()
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    TimerService.BROADCAST_TICK -> {
                        val remaining = intent.getIntExtra(TimerService.EXTRA_REMAINING_SECONDS, 0)
                        val isBreak = intent.getBooleanExtra(TimerService.EXTRA_IS_BREAK, false)
                        _state.update {
                            it.copy(
                                remainingSeconds = remaining,
                                phase = if (isBreak) TimerPhase.Break else TimerPhase.Running
                            )
                        }
                    }
                    TimerService.BROADCAST_COMPLETE -> {
                        val isBreak = intent.getBooleanExtra(TimerService.EXTRA_IS_BREAK, false)
                        if (isBreak) onBreakComplete() else onStudyComplete()
                    }
                    TimerService.BROADCAST_STOPPED -> {
                        cleanupTimer(finished = false)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(TimerService.BROADCAST_TICK)
            addAction(TimerService.BROADCAST_COMPLETE)
            addAction(TimerService.BROADCAST_STOPPED)
        }

        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceiver() {
        broadcastReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            broadcastReceiver = null
        }
    }

    private fun cleanupTimer(finished: Boolean) {
        unregisterReceiver()
        if (!finished && !sessionSaved) {
            saveSession(completed = false)
        }
        val s = _state.value
        _state.update {
            it.copy(
                phase = TimerPhase.Idle,
                remainingSeconds = s.durationMinutes * 60,
                totalSeconds = s.durationMinutes * 60
            )
        }
        pausedTimeMs = 0L
        sessionSaved = false   // reset for the next session
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveSession(completed: Boolean) {
        if (sessionSaved) return          // ← double-save guard
        sessionSaved = true
        val s = _state.value
        val session = StudySession(
            durationMinutes = if (completed) s.durationMinutes
                else (s.totalSeconds - s.remainingSeconds) / 60,
            date = Date(),
            completed = completed,
            pausedTimeMs = pausedTimeMs,
            subject = s.subject.ifBlank { "General Study" },
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            activeSessionId = sessionDao.insert(session)
            if (completed) loadTodayStats()
        }
    }

    private fun loadTodayStats() {
        viewModelScope.launch {
            val startOfDay = getStartOfDayMillis()
            val count = sessionDao.getCompletedCountSince(startOfDay)
            _state.update { it.copy(completedSessions = count) }
        }
    }

    private fun getStartOfDayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
    }
}
