package com.obrynex.studyguard.booksummarizer

/**
 * Splits a large plain-text document into fixed-size chunks at word boundaries.
 *
 * Extracted from [BookSummarizerUseCase] so it can be:
 *  - Unit-tested in isolation (no coroutines or ML engine needed).
 *  - Reused by any future feature that needs to page through long text.
 *
 * ### Splitting strategy
 * The algorithm scans forward [chunkSize] characters, then walks backward to
 * find the nearest whitespace character so that words are never truncated mid-way.
 * If no whitespace is found in the current window (e.g. a very long URL or CJK
 * run), the cut falls at the hard [chunkSize] boundary.
 *
 * @param chunkSize Maximum characters per chunk (default = [BookSummarizerUseCase.CHUNK_CHARS]).
 */
class TextChunker(private val chunkSize: Int = BookSummarizerUseCase.CHUNK_CHARS) {

    /**
     * Splits [text] into a list of non-empty chunks of at most [chunkSize] characters.
     *
     * Guarantees:
     *  - Every returned chunk is non-empty.
     *  - No chunk exceeds [chunkSize] characters.
     *  - Concatenating all chunks (with single spaces between) reproduces the
     *    normalized whitespace-collapsed version of [text].
     *
     * @return Single-element list when [text] fits within one chunk;
     *         empty list when [text] is blank.
     */
    fun split(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= chunkSize) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        var start  = 0

        while (start < trimmed.length) {
            val end = minOf(start + chunkSize, trimmed.length)

            val boundary = when {
                // We've reached the end of the string — take everything
                end == trimmed.length -> end

                // Walk backward from `end` to find the last space or newline
                else -> {
                    val spaceIdx  = trimmed.lastIndexOf(' ',  end)
                    val newlineIdx = trimmed.lastIndexOf('\n', end)
                    val best = maxOf(spaceIdx, newlineIdx)
                    if (best > start) best else end   // fallback: hard cut
                }
            }

            chunks.add(trimmed.substring(start, boundary).trim())

            // Advance past the boundary and skip any leading whitespace
            start = boundary
            while (start < trimmed.length && trimmed[start].isWhitespace()) start++
        }

        return chunks.filter { it.isNotEmpty() }
    }
}
