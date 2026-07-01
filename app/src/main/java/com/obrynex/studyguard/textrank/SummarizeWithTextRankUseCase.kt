package com.obrynex.studyguard.textrank

/**
 * Use-case that wraps [TextRankSummarizer] for injection into ViewModels.
 *
 * Having a dedicated class (rather than calling the singleton directly) means:
 *  - ViewModels depend on an interface boundary, not the concrete object.
 *  - Tests can supply a [FakeSummarizeWithTextRankUseCase] without touching
 *    the algorithm at all.
 *  - Sentence count and style can be configured per call-site.
 *
 * ### Usage in [AiTutorViewModel]
 * ```kotlin
 * class AiTutorViewModel(
 *     private val manager   : AIEngineManager,
 *     private val textRank  : SummarizeWithTextRankUseCase = SummarizeWithTextRankUseCase()
 * ) : ViewModel()
 * ```
 *
 * When the AI engine enters fallback mode, [invoke] is called synchronously
 * (it is pure computation with no IO or suspension) and the result is displayed
 * as a ChatMessage with [isFallback] = true.
 */
open class SummarizeWithTextRankUseCase {

    /**
     * Produces an extractive summary of [text] by selecting the [topN] most
     * central sentences via TextRank.
     *
     * @param text  The user's question or passage to be summarized.
     * @param topN  Number of sentences to extract (default 4 — enough for a
     *              concise study-chat reply without being overwhelming).
     * @return Extracted sentences in original order, joined by newlines.
     *         Returns the trimmed input unchanged if it is already short.
     */
    open operator fun invoke(text: String, topN: Int = 4): String =
        TextRankSummarizer.summarize(text, topN)
}
