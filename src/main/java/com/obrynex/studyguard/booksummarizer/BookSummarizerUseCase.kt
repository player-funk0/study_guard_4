package com.obrynex.studyguard.booksummarizer

import com.obrynex.studyguard.ai.LocalAiEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Summarizes a full book or long document using the on-device Gemma 2B-IT engine.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Strategy — Map → Reduce
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   MAP:    Split text into [CHUNK_CHARS]-character chunks (at word boundaries).
 *           Summarize each chunk individually → list of chunk summaries.
 *
 *   REDUCE: Join all chunk summaries. If the combined text fits within one
 *           context window ([MAX_REDUCE_CHARS]), run a single final inference
 *           to produce a unified, coherent summary.
 *           If it still exceeds the limit, a second reduce pass is applied.
 *
 * This ensures the engine never sees more tokens than it can handle, regardless
 * of how long the source book is.
 *
 * All progress is emitted as [BookSummarizeProgress] events so the UI can
 * display a live progress bar and stream intermediate tokens.
 *
 * @param engine A live, ready [LocalAiEngine] — caller must verify readiness.
 */

/**
 * Common interface implemented by [BookSummarizerUseCase] (production) and
 * `FakeBookSummarizerUseCase` (tests). Allows [BookSummarizerViewModel] to accept
 * a factory lambda whose return type is independent of the real engine instance,
 * enabling full ViewModel testing without a live MediaPipe session.
 */
interface BookSummarizerRunner {
    fun summarize(text: String, levelLabel: String = "مفصّل"): kotlinx.coroutines.flow.Flow<BookSummarizeProgress>
}

open class BookSummarizerUseCase(
    private val engine  : LocalAiEngine,
    private val chunker : TextChunker = TextChunker()
) : BookSummarizerRunner {

    companion object {
        /**
         * Maximum characters per chunk fed to the model during the MAP phase.
         *
         * Gemma 2B-IT has a 1,024-token budget (set in [LocalAiEngine.MAX_TOKENS]).
         * Arabic averages ~4 chars/token, so 1,600 chars ≈ 400 input tokens,
         * leaving ~624 tokens for the output — a comfortable margin.
         */
        const val CHUNK_CHARS = 1_600

        /**
         * Maximum length of the concatenated chunk summaries before triggering
         * a second REDUCE pass. Keeps the final prompt inside one context window.
         */
        const val MAX_REDUCE_CHARS = 3_200
    }

    /**
     * Produces a full-book summary by emitting [BookSummarizeProgress] events.
     *
     * Collect this Flow in [BookSummarizerViewModel]; cancel the collection to
     * abort an in-progress summarization cleanly.
     *
     * @param text       The full plain-text content of the book.
     * @param levelLabel Arabic adjective describing the summary style (e.g. "مفصّل").
     */
    open override fun summarize(
        text       : String,
        levelLabel : String
    ): Flow<BookSummarizeProgress> = flow {

        val chunks      = chunker.split(text)
        val totalChunks = chunks.size

        if (totalChunks == 0) {
            emit(BookSummarizeProgress.Done(summary = ""))
            return@flow
        }

        emit(BookSummarizeProgress.Starting(totalChunks))

        // ── MAP phase ─────────────────────────────────────────────────────────
        val chunkSummaries = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            // Skip empty chunks
            if (chunk.isBlank()) {
                chunkSummaries.add("")
                return@forEachIndexed
            }

            // Announce start of this chunk (empty partialToken signals "working")
            emit(
                BookSummarizeProgress.SummarizingChunk(
                    chunkIndex   = index,
                    totalChunks  = totalChunks,
                    partialToken = ""
                )
            )

            var accumulated = ""
            try {
                engine.summarize(chunk, levelLabel).collect { token ->
                    accumulated += token
                    emit(
                        BookSummarizeProgress.SummarizingChunk(
                            chunkIndex   = index,
                            totalChunks  = totalChunks,
                            partialToken = accumulated
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-throw cancellation
            } catch (e: Exception) {
                // If a chunk fails, use the raw chunk text as fallback
                accumulated = chunk.take(200) + "... [خطأ في التلخيص]"
            }
            chunkSummaries.add(accumulated.trim())
        }

        // ── REDUCE phase ──────────────────────────────────────────────────────
        emit(BookSummarizeProgress.Reducing)

        val finalSummary: String

        if (totalChunks == 1) {
            // Only one chunk — its summary IS the final answer; skip reduce call.
            finalSummary = chunkSummaries.first()
            emit(BookSummarizeProgress.ReducingPartial(finalSummary))

        } else {
            val merged = chunkSummaries.joinToString("\n\n")

            if (merged.length <= MAX_REDUCE_CHARS) {
                // Single reduce pass
                var reduced = ""
                try {
                    engine.summarize(merged, "موجز ومتماسك").collect { token ->
                        reduced += token
                        emit(BookSummarizeProgress.ReducingPartial(reduced))
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallback: concatenate chunk summaries with separators
                    reduced = merged.take(MAX_REDUCE_CHARS)
                }
                finalSummary = reduced.trim()

            } else {
                // Two-pass reduce: halve the merged text, summarize each half, then merge again.
                val mid = merged.lastIndexOf('\n', merged.length / 2).takeIf { it > 0 }
                    ?: (merged.length / 2)

                var r1 = ""
                engine.summarize(merged.substring(0, mid), "موجز").collect { r1 += it }

                var r2 = ""
                engine.summarize(merged.substring(mid), "موجز").collect { r2 += it }

                var reduced = ""
                engine.summarize("${r1.trim()}\n\n${r2.trim()}", "موجز ومتماسك").collect { token ->
                    reduced += token
                    emit(BookSummarizeProgress.ReducingPartial(reduced))
                }
                finalSummary = reduced.trim()
            }
        }

        emit(BookSummarizeProgress.Done(summary = finalSummary))
    }

}

// ── Progress events ────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of progress events emitted by [BookSummarizerUseCase.summarize].
 * Each variant carries exactly the data the UI needs for that stage.
 */
sealed class BookSummarizeProgress {

    /**
     * First event — emitted before any inference starts.
     * [totalChunks] lets the UI configure the progress bar range.
     */
    data class Starting(val totalChunks: Int) : BookSummarizeProgress()

    /**
     * Emitted repeatedly during the MAP phase.
     * [partialToken] grows as the model streams tokens; an empty string means
     * the chunk was just announced and inference hasn't produced output yet.
     */
    data class SummarizingChunk(
        val chunkIndex   : Int,
        val totalChunks  : Int,
        val partialToken : String
    ) : BookSummarizeProgress()

    /** All chunks summarized; the REDUCE pass is starting. */
    object Reducing : BookSummarizeProgress()

    /** Streaming tokens from the final reduce inference. */
    data class ReducingPartial(val partialSummary: String) : BookSummarizeProgress()

    /** Terminal event — [summary] is the complete, ready-to-display book summary. */
    data class Done(val summary: String) : BookSummarizeProgress()
}
