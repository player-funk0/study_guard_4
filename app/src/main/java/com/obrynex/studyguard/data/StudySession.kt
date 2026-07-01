package com.obrynex.studyguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents a single study session saved to the local Room database.
 *
 * @param id                Auto-generated primary key
 * @param durationMinutes   Total duration in minutes (or elapsed minutes if incomplete)
 * @param date              When the session was recorded
 * @param completed         True if the timer finished naturally
 * @param pausedTimeMs      Total time spent paused (milliseconds)
 * @param subject           User-entered subject/topic
 * @param createdAt         Epoch ms for precise ordering
 */
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val durationMinutes: Int = 0,
    val date: Date = Date(),
    val completed: Boolean = false,
    val pausedTimeMs: Long = 0L,
    val subject: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
