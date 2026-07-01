package com.obrynex.studyguard.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.data.StudySessionDao
import com.obrynex.studyguard.di.GetStreakUseCase
import com.obrynex.studyguard.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * UI state for the session tracker screen.
 */
data class TrackerState(
    val sessions: List<StudySession> = emptyList(),
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val streak: Int = 0,
    val totalSessions: Int = 0,
    val isLoading: Boolean = true
)

class TrackerViewModel(
    private val dao: StudySessionDao
) : AndroidViewModel(
    ServiceLocator.context as Application
) {

    private val getStreak = GetStreakUseCase(dao)

    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state.asStateFlow()

    init {
        loadData()
        collectStreak()
    }

    fun refresh() = loadData()

    fun delete(session: StudySession) {
        viewModelScope.launch { dao.delete(session) }
    }

    private fun collectStreak() {
        viewModelScope.launch {
            getStreak().collect { streak ->
                _state.update { it.copy(streak = streak) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val now = System.currentTimeMillis()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val weekStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }.timeInMillis

            dao.getAll().collect { sessions ->
                val completed = sessions.filter { it.completed }
                val todayTotal = completed
                    .filter { it.createdAt >= todayStart }
                    .sumOf { it.durationMinutes }
                val weekTotal = completed
                    .filter { it.createdAt >= weekStart }
                    .sumOf { it.durationMinutes }

                _state.update {
                    it.copy(
                        sessions = sessions,
                        todayMinutes = todayTotal,
                        weekMinutes = weekTotal,
                        totalSessions = completed.size,
                        isLoading = false
                    )
                }
            }
        }
    }
}
