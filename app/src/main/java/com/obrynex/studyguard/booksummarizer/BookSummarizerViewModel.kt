package com.obrynex.studyguard.booksummarizer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI state ───────────────────────────────────────────────────────────────────

/**
 * Describes which phase of the Map→Reduce pipeline the UI should show.
 */
enum class SummarizerPhase {
    Idle,
    Starting,
    SummarizingChunk,
    Reducing,
    ReducingPartial,
    Done
}

/**
 * Full UI state for [BookSummarizerScreen]. All fields are immutable; updates
 * are applied via [MutableStateFlow.update].
 *
 * @param bookText         Raw source text loaded or typed by the user.
 * @param fileName         Display name of the imported file, or empty string.
 * @param wordCount        Approximate word count of [bookText].
 * @param isRunning        True while a summarization job is in progress.
 * @param currentChunk     1-based index of the chunk currently being processed.
 * @param totalChunks      Total number of chunks for this run.
 * @param phase            Current pipeline phase (drives the progress UI).
 * @param chunkStreamText  Live streaming tokens for the current chunk summary.
 * @param finalSummary     Growing or completed final summary text.
 * @param modelState       Mirrors [AIEngineManager.state] — drives the model-not-ready banner.
 * @param error            Non-null when a recoverable error occurred.
 */
data class BookSummarizerState(
    val bookText        : String       = "",
    val fileName        : String       = "",
    val wordCount       : Int          = 0,
    val isRunning       : Boolean      = false,
    val currentChunk    : Int          = 0,
    val totalChunks     : Int          = 0,
    val phase           : SummarizerPhase = SummarizerPhase.Idle,
    val chunkStreamText : String       = "",
    val finalSummary    : String       = "",
    val modelState      : AIModelState = AIModelState.Idle,
    val error           : String?      = null
) {
    /** [0f, 1f] progress fraction for the LinearProgressIndicator. */
    val progress: Float get() = when {
        totalChunks == 0              -> 0f
        phase == SummarizerPhase.Done -> 1f
        phase == SummarizerPhase.Reducing ||
        phase == SummarizerPhase.ReducingPartial ->
            (currentChunk.toFloat() / totalChunks).coerceAtLeast(0.9f)
        else -> (currentChunk.toFloat() / totalChunks).coerceIn(0f, 0.9f)
    }

    /** Arabic label shown beneath the progress bar. */
    val progressLabel: String get() = when (phase) {
        SummarizerPhase.Idle             -> ""
        SummarizerPhase.Starting         -> "جارٍ التحضير…"
        SummarizerPhase.SummarizingChunk -> "الجزء $currentChunk من $totalChunks"
        SummarizerPhase.Reducing         -> "دمج الملخصات…"
        SummarizerPhase.ReducingPartial  -> "إنشاء الملخص النهائي…"
        SummarizerPhase.Done             -> "اكتمل الملخص ✓"
    }

    val isModelReady : Boolean get() = modelState is AIModelState.Ready
    val isModelBusy  : Boolean get() = modelState is AIModelState.Validating
                                    || modelState is AIModelState.Loading
    val canSummarize : Boolean get() = isModelReady && !isRunning && bookText.isNotBlank()
}

// ── ViewModel ──────────────────────────────────────────────────────────────────

/**
 * ViewModel for [BookSummarizerScreen].
 *
 * ### Architecture
 * - Observes [AIEngineManager.state] to mirror model readiness in [BookSummarizerState].
 * - Delegates all inference + chunking to [BookSummarizerUseCase].
 * - Each [startSummarizing] call creates a new coroutine [activeJob]; calling
 *   [cancel] before launching prevents races.
 *
 * ### Testability
 * [useCaseFactory] is a lambda that creates a [BookSummarizerUseCase] for a
 * given [LocalAiEngine]. The default produces the real use case; tests inject
 * a fake that emits a predetermined sequence of [BookSummarizeProgress] events
 * without touching the model file.
 *
 * ### File import
 * [loadFile] reads a UTF-8 plain-text file from any content URI (Storage Access
 * Framework). PDF support would require an additional dependency (e.g. PdfRenderer
 * or iText) and is out of scope for the initial implementation.
 */
class BookSummarizerViewModel(
    private val manager       : AIEngineManager,
    private val useCaseFactory: (com.obrynex.studyguard.ai.LocalAiEngine?) -> BookSummarizerRunner =
        { engine ->
            // Engine can theoretically be null if releaseEngine() races with this
            // call between the isModelReady check and getEngine() — guard explicitly.
            checkNotNull(engine) { "Engine became null between readiness check and use-case creation" }
            BookSummarizerUseCase(engine)
        }
) : ViewModel() {

    private val _state = MutableStateFlow(BookSummarizerState())
    val state: StateFlow<BookSummarizerState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        // Mirror the engine state so the UI always knows if the model is ready
        viewModelScope.launch {
            manager.state.collect { engineState ->
                _state.update { it.copy(modelState = engineState) }
            }
        }
        // Kick off validation so the engine is ready by the time the user taps Summarize
        viewModelScope.launch { manager.validate() }
    }

    // ── Text / file input ──────────────────────────────────────────────────────

    fun onBookTextChanged(text: String) {
        _state.update {
            it.copy(
                bookText  = text,
                fileName  = if (text.isEmpty()) "" else it.fileName,
                wordCount = countWords(text),
                error     = null
            )
        }
    }

    /**
     * Reads a plain-text (.txt) file from [uri] via the ContentResolver.
     *
     * This is a suspend-safe IO call wrapped in a coroutine so it never blocks
     * the main thread.
     */
    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val name = resolveFileName(context, uri) ?: "كتاب.txt"
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                } ?: error("تعذّر فتح الملف")

                _state.update {
                    it.copy(
                        bookText  = text,
                        fileName  = name,
                        wordCount = countWords(text),
                        error     = null
                    )
                }
            }.onFailure { ex ->
                _state.update { it.copy(error = "خطأ في قراءة الملف: ${ex.message}") }
            }
        }
    }

    // ── Summarization ──────────────────────────────────────────────────────────

    /**
     * Starts the Map→Reduce summarization pipeline.
     *
     * Guards:
     *  - Engine must be [AIModelState.Ready]; otherwise shows an error.
     *  - [bookText] must not be blank.
     *  - Cancels any previous job before launching.
     */
    fun startSummarizing() {
        if (!_state.value.isModelReady) {
            _state.update { it.copy(error = "النموذج غير جاهز بعد. انتظر اكتمال التحميل.") }
            return
        }
        val text = _state.value.bookText.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "الرجاء إدخال نص الكتاب أو اختيار ملف نصي.") }
            return
        }

        activeJob?.cancel()

        _state.update {
            it.copy(
                isRunning       = true,
                error           = null,
                finalSummary    = "",
                chunkStreamText = "",
                currentChunk    = 0,
                totalChunks     = 0,
                phase           = SummarizerPhase.Starting
            )
        }

        // Re-capture the engine immediately before launching — guards against a
        // releaseEngine() call racing between the isModelReady check above and here.
        val liveEngine = manager.getEngine()
        if (liveEngine == null) {
            _state.update {
                it.copy(isRunning = false, phase = SummarizerPhase.Idle,
                        error = "النموذج أُفرغ فجأة — حاول مجدداً.")
            }
            return
        }

        val useCase = useCaseFactory(liveEngine)

        activeJob = viewModelScope.launch {
            runCatching {
                useCase.summarize(text).collect { progress ->
                    when (progress) {

                        is BookSummarizeProgress.Starting -> _state.update {
                            it.copy(
                                totalChunks  = progress.totalChunks,
                                currentChunk = 0,
                                phase        = SummarizerPhase.Starting
                            )
                        }

                        is BookSummarizeProgress.SummarizingChunk -> _state.update {
                            it.copy(
                                currentChunk    = progress.chunkIndex + 1,
                                chunkStreamText = progress.partialToken,
                                phase           = SummarizerPhase.SummarizingChunk
                            )
                        }

                        is BookSummarizeProgress.Reducing -> _state.update {
                            it.copy(
                                phase           = SummarizerPhase.Reducing,
                                chunkStreamText = ""
                            )
                        }

                        is BookSummarizeProgress.ReducingPartial -> _state.update {
                            it.copy(
                                phase        = SummarizerPhase.ReducingPartial,
                                finalSummary = progress.partialSummary
                            )
                        }

                        is BookSummarizeProgress.Done -> {
                            activeJob = null
                            _state.update {
                                it.copy(
                                    finalSummary    = progress.summary,
                                    phase           = SummarizerPhase.Done,
                                    isRunning       = false,
                                    chunkStreamText = ""
                                )
                            }
                        }
                    }
                }
            }.onFailure { ex ->
                // CancellationException must be rethrown so the coroutine machinery
                // knows this job was cancelled cooperatively. Swallowing it breaks
                // structured concurrency and can prevent parent scopes from cancelling.
                if (ex is kotlinx.coroutines.CancellationException) {
                    activeJob = null
                    throw ex
                }
                activeJob = null
                _state.update {
                    it.copy(
                        isRunning = false,
                        phase     = SummarizerPhase.Idle,
                        error     = "فشل التلخيص: ${ex.message}"
                    )
                }
            }
        }
    }

    /** Cancels an in-progress summarization job. State reverts to Idle. */
    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.update { it.copy(isRunning = false, phase = SummarizerPhase.Idle) }
    }

    /** Clears all input and output, ready for a new book. */
    fun reset() {
        activeJob?.cancel()
        activeJob = null
        _state.update { current ->
            BookSummarizerState(modelState = current.modelState)
        }
    }

    fun retryEngine() {
        viewModelScope.launch { manager.requestReInit() }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        activeJob = null
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun resolveFileName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
        }
}
