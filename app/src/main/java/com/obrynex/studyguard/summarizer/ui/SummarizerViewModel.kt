package com.obrynex.studyguard.summarizer.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.textrank.SummarizeWithTextRankUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the text summarizer screen.
 */
data class SummarizerState(
    val inputText: String = "",
    val summaryText: String = "",
    val isSummarizing: Boolean = false,
    val modelState: AIModelState = AIModelState.Idle,
    val error: String? = null,
    val useAI: Boolean = true,
    val summaryLevel: SummaryLevel = SummaryLevel.Balanced
)

enum class SummaryLevel(@StringRes val labelResId: Int, @StringRes val descriptionResId: Int) {
    Short(R.string.summary_level_short, R.string.summary_level_short_desc),
    Balanced(R.string.summary_level_balanced, R.string.summary_level_balanced_desc),
    Detailed(R.string.summary_level_detailed, R.string.summary_level_detailed_desc)
}

class SummarizerViewModel(
    private val useCase: SummarizeWithTextRankUseCase,
    private val aiManager: AIEngineManager
) : ViewModel() {

    private val _state = MutableStateFlow(SummarizerState())
    val state: StateFlow<SummarizerState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            aiManager.state.collect { engineState ->
                _state.update { it.copy(modelState = engineState) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text, error = null) }
    }

    fun onSummaryLevelChanged(level: SummaryLevel) {
        _state.update { it.copy(summaryLevel = level) }
    }

    fun toggleUseAI() {
        _state.update { it.copy(useAI = !it.useAI) }
    }

    fun summarize() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "Please enter some text to summarize") }
            return
        }

        activeJob?.cancel()
        _state.update { it.copy(isSummarizing = true, summaryText = "", error = null) }

        if (_state.value.useAI && _state.value.modelState is AIModelState.Ready) {
            summarizeWithAI(text)
        } else {
            summarizeWithTextRank(text)
        }
    }

    private fun summarizeWithTextRank(text: String) {
        activeJob = viewModelScope.launch {
            try {
                val topN = when (_state.value.summaryLevel) {
                    SummaryLevel.Short -> 2
                    SummaryLevel.Balanced -> 4
                    SummaryLevel.Detailed -> 6
                }
                val result = useCase(text, topN)
                _state.update { it.copy(summaryText = result, isSummarizing = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isSummarizing = false) }
            }
        }
    }

    private fun summarizeWithAI(text: String) {
        val engine = aiManager.getEngine()
        if (engine == null) {
            _state.update { it.copy(error = "AI engine not available", isSummarizing = false) }
            return
        }

        activeJob = viewModelScope.launch {
            try {
                val levelLabel = when (_state.value.summaryLevel) {
                    SummaryLevel.Short -> "short"
                    SummaryLevel.Balanced -> "balanced"
                    SummaryLevel.Detailed -> "detailed"
                }
                var result = ""
                engine.summarize(text, levelLabel).collect { token ->
                    result += token
                    _state.update { it.copy(summaryText = result) }
                }
                _state.update { it.copy(isSummarizing = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isSummarizing = false) }
            }
        }
    }

    fun clear() {
        activeJob?.cancel()
        _state.update { SummarizerState(modelState = it.modelState) }
    }

    fun retryEngine() {
        viewModelScope.launch { aiManager.requestReInit() }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
    }
}
