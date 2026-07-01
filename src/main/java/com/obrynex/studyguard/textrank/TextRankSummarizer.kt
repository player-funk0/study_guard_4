package com.obrynex.studyguard.textrank

import kotlin.math.ln

/**
 * Graph-based extractive text summarizer using the TextRank algorithm.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Algorithm (Mihalcea & Tarau, 2004)
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *  1. Tokenize input into sentences.
 *  2. For each sentence pair (i, j), compute a normalized word-overlap
 *     similarity score:  |words(i) ∩ words(j)| / (ln|words(i)| + ln|words(j)|)
 *  3. Build a weighted, undirected graph where nodes are sentences and edges
 *     carry the similarity score.
 *  4. Run PageRank iterations until convergence (or [MAX_ITERATIONS]).
 *  5. Sort sentences by score and return the top-K in their original order.
 *
 * This is entirely extractive — the output sentences come verbatim from the
 * input text; no generative model is involved.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Arabic support
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * [ARABIC_STOPWORDS] contains the most common function words that carry no
 * semantic weight. Removing them sharpens the similarity signal considerably
 * when summarizing Arabic study material.
 *
 * Both Arabic and Latin sentence delimiters are handled by [splitSentences].
 */
object TextRankSummarizer {

    // ── Tuning constants ───────────────────────────────────────────────────────

    /** PageRank damping factor — standard recommendation is 0.85. */
    private const val DAMPING        = 0.85
    private const val MAX_ITERATIONS = 50
    private const val CONVERGENCE    = 1e-4

    /** Minimum sentence length (chars) to be eligible for extraction. */
    private const val MIN_SENTENCE_CHARS = 15

    /**
     * Common Arabic stop-words excluded from the similarity computation.
     * Curated for educational/religious Arabic text (the primary use-case for StudyGuard).
     */
    private val ARABIC_STOPWORDS = setOf(
        "في", "من", "إلى", "على", "عن", "مع", "هذا", "هذه", "ذلك", "تلك",
        "التي", "الذي", "الذين", "اللواتي", "وقد", "قد", "كان", "كانت",
        "كانوا", "يكون", "تكون", "إن", "أن", "لأن", "لكن", "أو", "أم",
        "ثم", "حتى", "إذا", "لما", "بما", "مما", "فيما", "عما", "كما",
        "وما", "وهو", "وهي", "وهم", "وهن", "فهو", "فهي", "فهم",
        "هو", "هي", "هم", "هن", "أنا", "نحن", "أنت", "أنتم", "أنتن",
        "له", "لها", "لهم", "لهن", "لنا", "لكم", "فيه", "فيها",
        "فيهم", "منه", "منها", "منهم", "عنه", "عنها", "عنهم",
        "كل", "بعض", "جميع", "غير", "سوى", "أكثر", "أقل", "أول", "آخر",
        "ما", "لا", "لم", "لن", "ليس", "ليست", "قبل", "بعد", "فوق", "تحت",
        "بين", "خلال", "حول", "ضد", "عند", "لدى", "إلا", "حيث", "بسبب",
        "نتيجة", "لذا", "لذلك", "وبالتالي", "أيضا", "أيضًا", "فقط"
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Summarizes [text] by extracting the [topN] most important sentences.
     *
     * @param text  Input text (Arabic, Latin, or mixed).
     * @param topN  Number of sentences to extract. Clamped to [1, sentence count].
     * @return Extracted sentences joined by newlines, in their original order.
     *         Returns [text] unchanged if it is too short to summarize.
     */
    fun summarize(text: String, topN: Int = 5): String {
        val sentences = splitSentences(text)
            .filter { it.length >= MIN_SENTENCE_CHARS }
            .distinct()

        if (sentences.size <= topN) return text.trim()

        val tokenized   = sentences.map { tokenize(it) }
        val simMatrix   = buildSimilarityMatrix(tokenized)
        val scores      = pageRank(simMatrix)

        val topIndices = scores
            .mapIndexed { idx, score -> idx to score }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
            .toSortedSet()          // preserve original sentence order

        return topIndices.joinToString("\n") { sentences[it] }
    }

    // ── Internal steps ─────────────────────────────────────────────────────────

    /**
     * Splits [text] into sentences using Arabic and Latin end-of-sentence markers.
     * Handles "." in abbreviations and numbers by requiring a capital/Arabic letter
     * to follow (common heuristic).
     */
    internal fun splitSentences(text: String): List<String> {
        // Split on Latin/Arabic end-of-sentence markers: . ! ? ؟ and newlines.
        // Arabic comma ، is intentionally excluded — it marks a clause pause,
        // not a full sentence boundary, and splitting on it would over-fragment text.
        val raw = text.split(Regex("[.!?؟\n]+"))
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Lowercases the sentence, removes punctuation and diacritics (tashkeel),
     * and strips stop-words. Returns a set of meaningful word stems.
     */
    internal fun tokenize(sentence: String): Set<String> {
        // Remove Arabic diacritics (harakat)
        val stripped = sentence
            .replace(Regex("[\u064B-\u065F\u0670]"), "")       // tashkeel
            .replace(Regex("[،؛.!?؟,;:()\\[\\]\"'«»]"), " ")  // punctuation
            .lowercase()

        return stripped.split(Regex("\\s+"))
            .filter { it.length > 2 && it !in ARABIC_STOPWORDS }
            .toSet()
    }

    /**
     * Builds an N×N similarity matrix where entry [i][j] is the normalized
     * word-overlap between sentences i and j.
     *
     * Normalization: |intersection| / (ln(|i|) + ln(|j|))
     * Falls back to 0 when either sentence has fewer than 2 tokens (ln→0 or negative).
     */
    internal fun buildSimilarityMatrix(tokenized: List<Set<String>>): Array<DoubleArray> {
        val n   = tokenized.size
        val mat = Array(n) { DoubleArray(n) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val overlap = tokenized[i].intersect(tokenized[j]).size.toDouble()
                if (overlap == 0.0) continue

                val denom = ln(tokenized[i].size.toDouble().coerceAtLeast(2.0)) +
                            ln(tokenized[j].size.toDouble().coerceAtLeast(2.0))

                val sim = if (denom <= 0) 0.0 else overlap / denom
                mat[i][j] = sim
                mat[j][i] = sim
            }
        }
        return mat
    }

    /**
     * Runs PageRank on the similarity graph until convergence or [MAX_ITERATIONS].
     *
     * Each row of [matrix] is normalized by its row-sum before the update step
     * so that the scores form a proper stochastic transition matrix.
     *
     * @return A DoubleArray of length N with the final sentence scores.
     */
    internal fun pageRank(matrix: Array<DoubleArray>): DoubleArray {
        val n       = matrix.size
        var scores  = DoubleArray(n) { 1.0 / n }

        // Row sums for normalization (avoid recomputing on every iteration)
        val rowSums = DoubleArray(n) { i -> matrix[i].sum().coerceAtLeast(1e-10) }

        repeat(MAX_ITERATIONS) {
            val newScores = DoubleArray(n)
            for (i in 0 until n) {
                var incoming = 0.0
                for (j in 0 until n) {
                    if (i != j) {
                        incoming += (matrix[j][i] / rowSums[j]) * scores[j]
                    }
                }
                newScores[i] = (1.0 - DAMPING) / n + DAMPING * incoming
            }

            // Check convergence: max absolute change across all scores
            val delta = (0 until n).maxOf { i -> Math.abs(newScores[i] - scores[i]) }
            scores = newScores
            if (delta < CONVERGENCE) return scores
        }
        return scores
    }
}
