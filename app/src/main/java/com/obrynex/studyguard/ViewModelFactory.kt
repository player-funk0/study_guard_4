package com.obrynex.studyguard

import android.app.Application
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.obrynex.studyguard.aitutor.AiTutorViewModel
import com.obrynex.studyguard.booksummarizer.BookSummarizerViewModel
import com.obrynex.studyguard.debug.DebugInfoViewModel
import com.obrynex.studyguard.di.ServiceLocator
import com.obrynex.studyguard.islamic.ui.IslamicViewModel
import com.obrynex.studyguard.learningmaterials.LearningMaterialsViewModel
import com.obrynex.studyguard.summarizer.ui.SummarizerViewModel
import com.obrynex.studyguard.timer.TimerViewModel
import com.obrynex.studyguard.tracker.SessionDetailViewModel
import com.obrynex.studyguard.tracker.TrackerViewModel
import com.obrynex.studyguard.wellbeing.WellbeingViewModel

/**
 * CompositionLocal that provides the [ViewModelProvider.Factory] throughout the
 * Compose hierarchy. Set once in [NavGraph] via [CompositionLocalProvider].
 */
val LocalViewModelFactory = compositionLocalOf<ViewModelProvider.Factory> {
    error("LocalViewModelFactory not provided")
}

/**
 * Manual [ViewModelProvider.Factory] that wires every ViewModel to its
 * dependencies from [ServiceLocator].
 */
class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TimerViewModel::class.java) ->
            TimerViewModel(application, ServiceLocator.studySessionDao) as T

        modelClass.isAssignableFrom(LearningMaterialsViewModel::class.java) ->
            LearningMaterialsViewModel(ServiceLocator.learningMaterialDao) as T

        modelClass.isAssignableFrom(SummarizerViewModel::class.java) ->
            SummarizerViewModel(
                useCase   = ServiceLocator.summarizeTextUseCase,
                aiManager = ServiceLocator.aiEngineManager
            ) as T

        modelClass.isAssignableFrom(TrackerViewModel::class.java) ->
            TrackerViewModel(ServiceLocator.studySessionDao) as T

        modelClass.isAssignableFrom(IslamicViewModel::class.java) ->
            IslamicViewModel(application) as T

        modelClass.isAssignableFrom(AiTutorViewModel::class.java) ->
            AiTutorViewModel(manager = ServiceLocator.aiEngineManager) as T

        modelClass.isAssignableFrom(BookSummarizerViewModel::class.java) ->
            BookSummarizerViewModel(manager = ServiceLocator.aiEngineManager) as T

        modelClass.isAssignableFrom(WellbeingViewModel::class.java) ->
            WellbeingViewModel(application) as T

        modelClass.isAssignableFrom(SessionDetailViewModel::class.java) ->
            SessionDetailViewModel(dao = ServiceLocator.studySessionDao) as T

        modelClass.isAssignableFrom(DebugInfoViewModel::class.java) ->
            DebugInfoViewModel(
                manager   = ServiceLocator.aiEngineManager,
                hashCache = ServiceLocator.modelHashCache,
                context   = ServiceLocator.context
            ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    companion object {
        /** Creates a factory using the Application already stored in [ServiceLocator]. */
        fun fromApplication(): ViewModelFactory =
            ViewModelFactory(ServiceLocator.context as Application)
    }
}
