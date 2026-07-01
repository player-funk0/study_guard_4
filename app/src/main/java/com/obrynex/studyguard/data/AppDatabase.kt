package com.obrynex.studyguard.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.obrynex.studyguard.learningmaterials.LearningMaterial
import com.obrynex.studyguard.learningmaterials.LearningMaterialDao

/**
 * Room database for StudyGuard.
 *
 * Tables:
 *   - study_sessions  : Past study sessions
 *   - learning_materials : User's learning materials
 */
@Database(
    entities = [
        StudySession::class,
        LearningMaterial::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studySessionDao(): StudySessionDao
    abstract fun learningMaterialDao(): LearningMaterialDao
}
