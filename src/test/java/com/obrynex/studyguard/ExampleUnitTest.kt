package com.obrynex.studyguard

import com.obrynex.studyguard.booksummarizer.BookSummarizerUseCase
import com.obrynex.studyguard.booksummarizer.TextChunker
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.data.StudySessionDao
import com.obrynex.studyguard.di.GetStreakUseCase
import com.obrynex.studyguard.textrank.TextRankSummarizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Shared test helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Minimal no-op DAO — only calculateStreak() is under test; it never calls the DAO. */
private val fakeDao = object : StudySessionDao {
    override suspend fun insert(session: StudySession): Long = 0L
    override suspend fun update(session: StudySession) {}
    override suspend fun delete(session: StudySession) {}
    override suspend fun deleteById(sessionId: Long) {}
    override fun getAll(): Flow<List<StudySession>> = flow { emit(emptyList()) }
    override suspend fun getById(sessionId: Long): StudySession? = null
    override fun getSince(since: Long): Flow<List<StudySession>> = flow { emit(emptyList()) }
    override suspend fun getCompletedCountSince(since: Long): Int = 0
    override fun getCompletedCountSinceFlow(since: Long): Flow<Int> = flow { emit(0) }
    override suspend fun getTotalMinutesSince(since: Long): Int? = null
    override fun getRecent(limit: Int): Flow<List<StudySession>> = flow { emit(emptyList()) }
    override suspend fun getTotalCount(): Int = 0
    override suspend fun deleteAll() {}
}

/** Builds a completed [StudySession] whose date is [daysAgo] days before today. */
private fun sessionDaysAgo(daysAgo: Int, completed: Boolean = true): StudySession {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return StudySession(
        durationMinutes = 25,
        date            = cal.time,
        completed       = completed,
        createdAt       = cal.timeInMillis
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TextChunker
// ─────────────────────────────────────────────────────────────────────────────

class TextChunkerTest {

    private val chunker = TextChunker(chunkSize = 50)

    @Test
    fun `empty string returns empty list`() {
        assertEquals(emptyList<String>(), chunker.split(""))
        assertEquals(emptyList<String>(), chunker.split("   "))
    }

    @Test
    fun `text shorter than chunk size returns single chunk`() {
        val text   = "Hello world"
        val result = chunker.split(text)
        assertEquals(1, result.size)
        assertEquals(text, result[0])
    }

    @Test
    fun `no chunk exceeds chunkSize characters`() {
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(20)
        chunker.split(longText).forEach { chunk ->
            assertTrue("Chunk length ${chunk.length} exceeds limit 50", chunk.length <= 50)
        }
    }

    @Test
    fun `all words are preserved across chunks`() {
        val text         = "Word1 Word2 Word3 Word4 Word5 Word6 Word7 Word8 Word9 Word10"
        val smallChunker = TextChunker(chunkSize = 20)
        val joined       = smallChunker.split(text).joinToString(" ")
        (1..10).forEach { n ->
            assertTrue("Word$n missing from chunks", "Word$n" in joined)
        }
    }

    @Test
    fun `default chunk size matches BookSummarizerUseCase constant`() {
        val defaultChunker = TextChunker()
        val text           = "A".repeat(BookSummarizerUseCase.CHUNK_CHARS + 10)
        assertTrue("Expected multiple chunks", defaultChunker.split(text).size >= 2)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TextRankSummarizer
// ─────────────────────────────────────────────────────────────────────────────

class TextRankSummarizerTest {

    @Test
    fun `splitSentences handles Latin full stops`() {
        val result = TextRankSummarizer.splitSentences("First sentence. Second sentence. Third.")
        assertEquals(3, result.size)
    }

    @Test
    fun `splitSentences handles Arabic question mark`() {
        val result = TextRankSummarizer.splitSentences("هل أنت بخير؟ الحمد لله. ما اسمك؟")
        assertEquals(3, result.size)
    }

    @Test
    fun `splitSentences filters blank entries`() {
        val result = TextRankSummarizer.splitSentences("Sentence one.   .   Sentence two.")
        assertTrue("Should filter empty splits", result.all { it.isNotEmpty() })
    }

    @Test
    fun `tokenize strips Arabic diacritics`() {
        // مُحَمَّد with harakat vs محمد without — should tokenize identically
        val with    = TextRankSummarizer.tokenize("مُحَمَّد رسول الله")
        val without = TextRankSummarizer.tokenize("محمد رسول الله")
        assertEquals(without, with)
    }

    @Test
    fun `tokenize removes Arabic stop words`() {
        val tokens = TextRankSummarizer.tokenize("العلم في الصدر لا في السطر")
        assertTrue("Stop word 'في' should be removed", "في" !in tokens)
    }

    @Test
    fun `pageRank scores sum close to 1`() {
        val n      = 4
        val matrix = Array(n) { i -> DoubleArray(n) { j -> if (i != j) 0.5 else 0.0 } }
        val scores = TextRankSummarizer.pageRank(matrix)
        assertEquals(n, scores.size)
        assertTrue("Scores should sum near 1.0 (got ${scores.sum()})",
            Math.abs(scores.sum() - 1.0) < 0.05)
    }

    @Test
    fun `summarize returns original text when shorter than topN sentences`() {
        val text   = "Short text."
        val result = TextRankSummarizer.summarize(text, topN = 5)
        assertEquals(text.trim(), result)
    }

    @Test
    fun `summarize returns at most topN sentences`() {
        val text   = (1..20).joinToString(". ") { "This is sentence number $it with enough words" }
        val result = TextRankSummarizer.summarize(text, topN = 3)
        assertTrue("Expected ≤ 3 sentences",
            TextRankSummarizer.splitSentences(result).size <= 3)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetStreakUseCase — pure streak calculation logic
// ─────────────────────────────────────────────────────────────────────────────

class GetStreakUseCaseTest {

    // calculateStreak is `internal` so accessible within the same module
    private val useCase = GetStreakUseCase(dao = fakeDao)

    @Test
    fun `empty session list returns zero`() {
        assertEquals(0, useCase.calculateStreak(emptyList()))
    }

    @Test
    fun `single session today returns streak of 1`() {
        assertEquals(1, useCase.calculateStreak(listOf(sessionDaysAgo(0))))
    }

    @Test
    fun `four consecutive days return streak of 4`() {
        val sessions = (0..3).map { sessionDaysAgo(it) }
        assertEquals(4, useCase.calculateStreak(sessions))
    }

    @Test
    fun `gap in days breaks streak back to 1`() {
        // Today and 3 days ago with a gap on days 1 and 2
        val sessions = listOf(sessionDaysAgo(0), sessionDaysAgo(3))
        assertEquals(1, useCase.calculateStreak(sessions))
    }

    @Test
    fun `yesterday counts without a session today`() {
        val sessions = listOf(sessionDaysAgo(1), sessionDaysAgo(2))
        assertEquals(2, useCase.calculateStreak(sessions))
    }

    @Test
    fun `most recent session two days ago breaks streak`() {
        val sessions = listOf(sessionDaysAgo(2), sessionDaysAgo(3))
        assertEquals(0, useCase.calculateStreak(sessions))
    }

    @Test
    fun `multiple sessions on same day count as one`() {
        // Two sessions today still produce a streak of 1
        assertEquals(1, useCase.calculateStreak(listOf(sessionDaysAgo(0), sessionDaysAgo(0))))
    }

    @Test
    fun `non-completed sessions do not count`() {
        val sessions = listOf(
            sessionDaysAgo(0, completed = false),
            sessionDaysAgo(1, completed = true)
        ).filter { it.completed }          // mirroring what GetStreakUseCase.invoke() does
        assertEquals(1, useCase.calculateStreak(sessions))
    }
}
