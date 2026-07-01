package com.obrynex.studyguard.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around MediaPipe [LlmInference] for Gemma 2B-IT on-device.
 *
 * Lifecycle is managed entirely by [AIEngineManager]:
 *   - Instantiated inside [AIEngineManager.validate] after all gates pass.
 *   - Closed via [AIEngineManager.releaseEngine] → [safeClose].
 *
 * ### Safety flags
 *
 * | Flag          | Type          | Purpose                                         |
 * |---------------|---------------|-------------------------------------------------|
 * | [isClosed]    | AtomicBoolean | Prevents use after [safeClose]; readable externally. |
 * | [isInitialized] | AtomicBoolean | Guards [getOrCreate] from double-initialisation. |
 */
class LocalAiEngine(private val context: Context) {

    companion object {
        private val TAG = AppLogger.tagFor(LocalAiEngine::class)

        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
        const val MAX_TOKENS     = 1024

        fun modelFile(context: Context): File =
            File(context.getExternalFilesDir(null), "models/$MODEL_FILENAME")
    }

    val modelFile: File get() = Companion.modelFile(context)

    /**
     * True once [safeClose] has been called.
     * External code (e.g. [AIEngineManager]) may read this to skip stale references.
     */
    val isClosed: AtomicBoolean = AtomicBoolean(false)

    /**
     * True once [getOrCreate] successfully builds the [LlmInference] session.
     * Guards against redundant initialisations if [getOrCreate] is called concurrently.
     */
    private val isInitialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * @Volatile ensures that a write on one thread is immediately visible to all
     * other threads, providing the memory barrier that [isInitialized]'s AtomicBoolean
     * alone cannot guarantee for this reference.
     */
    @Volatile private var inference: LlmInference? = null

    /**
     * Returns the live [LlmInference] session, creating it on first call.
     *
     * Guard against double init: if [isInitialized] is already true, the existing
     * [inference] is returned immediately without calling [LlmInference.createFromOptions]
     * again (which would throw on some MediaPipe versions).
     *
     * @throws IllegalStateException if called after [safeClose].
     */
    private fun getOrCreate(): LlmInference {
        check(!isClosed.get()) { "LocalAiEngine has been closed — create a new instance" }

        inference?.let { return it }  // already initialised

        // Double-check via flag before the heavy createFromOptions call
        if (isInitialized.getAndSet(true)) {
            // Another thread beat us — inference should be non-null now
            return checkNotNull(inference) { "Concurrent init race — inference unexpectedly null" }
        }

        val opts = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setTopK(40)
            .setTemperature(0.7f)
            .setRandomSeed(42)
            .build()

        return LlmInference.createFromOptions(context, opts).also { inference = it }
    }

    /**
     * Full end-to-end warm-up — proves the engine works with a minimal inference.
     *
     * Called once by [AIEngineManager] during [AIModelState.Loading].
     * Throws if the binary is structurally invalid.
     *
     * ### Hang protection
     * If MediaPipe never delivers `finished = true` (e.g. a silent bug in the native
     * layer), [done] will never complete. The outer [AIEngineManager.WARMUP_TIMEOUT_MS]
     * wrapped around this call is the sole safety net — it will cancel this coroutine
     * and transition the state machine to [AIModelState.Failed].
     */
    suspend fun warmUp(): Unit = withContext(Dispatchers.Default) {
        AppLogger.d(TAG, "warmUp starting")
        val eng = getOrCreate()
        eng.generateResponse(buildGemmaPrompt("test"))
        AppLogger.d(TAG, "warmUp complete")
    }

    /**
     * Lightweight health check — verifies the session object is still usable
     * WITHOUT running a full inference token cycle.
     *
     * Distinguishes from [warmUp]:
     *  - [warmUp] → full inference, slow (~seconds), called once at startup.
     *  - [healthCheck] → structural check only, fast (~ms), callable on demand.
     *
     * @return true if the engine appears healthy.
     */
    fun healthCheck(): Boolean {
        if (isClosed.get()) {
            AppLogger.w(TAG, "healthCheck: engine is closed")
            return false
        }
        val alive = inference != null
        AppLogger.d(TAG, "healthCheck: ${if (alive) "ok" else "inference is null"}")
        return alive
    }

    /** Generates one complete AI response and emits it as a single flow value. */
    fun generate(prompt: String): Flow<String> = flow {
        val eng        = getOrCreate()
        val fullPrompt = buildGemmaPrompt(prompt)
        emit(withContext(Dispatchers.Default) { eng.generateResponse(fullPrompt) })
    }.flowOn(Dispatchers.IO)

    /** AI summarise — Arabic output, streaming. */
    fun summarize(text: String, levelLabel: String): Flow<String> = generate(
        """أنت مساعد دراسي ذكي. لخّص النص التالي باللغة العربية بأسلوب $levelLabel.
اكتب الملخص فقط بدون أي مقدمة أو تعليق.

النص:
$text"""
    )

    /** AI study tutor — answers questions in Arabic. */
    fun ask(question: String, subject: String = ""): Flow<String> = generate(
        """أنت مدرس ذكي متخصص${if (subject.isNotBlank()) " في $subject" else ""}.
أجب على السؤال التالي بأسلوب واضح ومبسط باللغة العربية.
إذا كان السؤال يحتاج خطوات، اشرحها بالترتيب.

السؤال: $question"""
    )

    private fun buildGemmaPrompt(userPrompt: String): String =
        "<start_of_turn>user\n$userPrompt<end_of_turn>\n<start_of_turn>model\n"

    /**
     * Closes the [LlmInference] session and marks this instance as closed.
     * Idempotent — safe to call multiple times.
     */
    fun safeClose() {
        if (!isClosed.compareAndSet(false, true)) {
            AppLogger.d(TAG, "safeClose: already closed — skipping")
            return
        }
        runCatching { inference?.close() }
            .onFailure { AppLogger.e(TAG, "Exception during safeClose — ignored", it) }
        inference = null
        AppLogger.d(TAG, "safeClose complete")
    }

    /** Delegates to [safeClose]. */
    fun close() = safeClose()
}
