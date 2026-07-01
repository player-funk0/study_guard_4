package com.obrynex.studyguard.learningmaterials

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for learning material CRUD operations.
 */
@Dao
interface LearningMaterialDao {

    @Insert
    suspend fun insert(material: LearningMaterial): Long

    @Update
    suspend fun update(material: LearningMaterial)

    @Delete
    suspend fun delete(material: LearningMaterial)

    @Query("DELETE FROM learning_materials WHERE id = :materialId")
    suspend fun deleteById(materialId: Long)

    @Query("SELECT * FROM learning_materials ORDER BY lastAccessedAt DESC")
    fun getAll(): Flow<List<LearningMaterial>>

    @Query("SELECT * FROM learning_materials WHERE id = :materialId")
    suspend fun getById(materialId: Long): LearningMaterial?

    @Query("SELECT * FROM learning_materials WHERE category = :category ORDER BY lastAccessedAt DESC")
    fun getByCategory(category: String): Flow<List<LearningMaterial>>

    @Query("SELECT DISTINCT category FROM learning_materials WHERE category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("UPDATE learning_materials SET lastAccessedAt = :timestamp WHERE id = :materialId")
    suspend fun updateLastAccessed(materialId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM learning_materials")
    suspend fun getCount(): Int

    @Query("DELETE FROM learning_materials")
    suspend fun deleteAll()
}
