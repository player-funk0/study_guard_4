package com.obrynex.studyguard.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.data.StudySessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val dao: StudySessionDao
) : ViewModel() {

    fun sessionById(id: Long): Flow<StudySession?> {
        return kotlinx.coroutines.flow.flow {
            emit(dao.getById(id))
        }
    }

    fun delete(session: StudySession) {
        viewModelScope.launch {
            dao.delete(session)
        }
    }
}
