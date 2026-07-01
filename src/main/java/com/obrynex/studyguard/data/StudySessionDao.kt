package com.obrynex.studyguard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for study session persistence.
 */
@Dao
interface StudySessionDao {

    @Insert
    suspend fun insert(session: StudySession): Long

    @Update
    suspend fun update(session: StudySession)

    @Delete
    suspend fun delete(session: StudySession)

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): StudySession?

    @Query("SELECT * FROM study_sessions WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun getSince(since: Long): Flow<List<StudySession>>

    @Query("SELECT COUNT(*) FROM study_sessions WHERE completed = 1 AND createdAt >= :since")
    suspend fun getCompletedCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM study_sessions WHERE completed = 1 AND createdAt >= :since")
    fun getCompletedCountSinceFlow(since: Long): Flow<Int>

    @Query("SELECT SUM(durationMinutes) FROM study_sessions WHERE createdAt >= :since")
    suspend fun getTotalMinutesSince(since: Long): Int?

    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<StudySession>>

    @Query("SELECT COUNT(*) FROM study_sessions")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAll()
}
