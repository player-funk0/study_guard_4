package com.obrynex.studyguard.learningmaterials

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a learning material that the user can study while the timer is running.
 *
 * @param id              Auto-generated primary key
 * @param title           Title/name of the learning material
 * @param content         The content/text of the material (notes, formulas, etc.)
 * @param category        Category tag (e.g., "Math", "Physics", "Custom")
 * @param createdAt       Epoch ms for ordering
 * @param lastAccessedAt  Epoch ms of last access
 */
@Entity(tableName = "learning_materials")
data class LearningMaterial(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)
