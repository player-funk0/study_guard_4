package com.obrynex.studyguard.learningmaterials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Learning Materials screen.
 */
data class LearningMaterialsState(
    val materials: List<LearningMaterial> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val selectedMaterial: LearningMaterial? = null,
    val newTitle: String = "",
    val newContent: String = "",
    val newCategory: String = "",
    val searchQuery: String = "",
    val error: String? = null
)

class LearningMaterialsViewModel(
    private val dao: LearningMaterialDao
) : ViewModel() {

    private val _state = MutableStateFlow(LearningMaterialsState())
    val state: StateFlow<LearningMaterialsState> = _state.asStateFlow()

    init {
        loadMaterials()
        loadCategories()
    }

    private fun loadMaterials() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                dao.getAll().collect { materials ->
                    _state.update {
                        it.copy(
                            materials = materials,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                dao.getAllCategories().collect { categories ->
                    _state.update { it.copy(categories = categories) }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ── Search & Filter ────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelected(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
    }

    /** Returns materials filtered by search query and category. */
    fun getFilteredMaterials(): List<LearningMaterial> {
        val s = _state.value
        var result = s.materials

        s.selectedCategory?.let { cat ->
            result = result.filter { it.category == cat }
        }

        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            result = result.filter { m ->
                m.title.lowercase().contains(q) ||
                m.content.lowercase().contains(q) ||
                m.category.lowercase().contains(q)
            }
        }

        return result
    }

    // ── Add Material ───────────────────────────────────────────────────────

    fun showAddDialog() {
        _state.update {
            it.copy(
                showAddDialog = true,
                newTitle = "",
                newContent = "",
                newCategory = ""
            )
        }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun onNewTitleChanged(title: String) {
        _state.update { it.copy(newTitle = title) }
    }

    fun onNewContentChanged(content: String) {
        _state.update { it.copy(newContent = content) }
    }

    fun onNewCategoryChanged(category: String) {
        _state.update { it.copy(newCategory = category) }
    }

    fun addMaterial() {
        val s = _state.value
        val title = s.newTitle.trim()
        val content = s.newContent.trim()

        if (title.isBlank()) {
            _state.update { it.copy(error = "Title is required") }
            return
        }

        viewModelScope.launch {
            val material = LearningMaterial(
                title = title,
                content = content,
                category = s.newCategory.trim()
            )
            dao.insert(material)
            _state.update {
                it.copy(
                    showAddDialog = false,
                    newTitle = "",
                    newContent = "",
                    newCategory = "",
                    error = null
                )
            }
        }
    }

    // ── Edit Material ──────────────────────────────────────────────────────

    fun showEditDialog(material: LearningMaterial) {
        _state.update {
            it.copy(
                showEditDialog = true,
                selectedMaterial = material,
                newTitle = material.title,
                newContent = material.content,
                newCategory = material.category
            )
        }
    }

    fun dismissEditDialog() {
        _state.update { it.copy(showEditDialog = false, selectedMaterial = null) }
    }

    fun updateMaterial() {
        val s = _state.value
        val material = s.selectedMaterial ?: return
        val title = s.newTitle.trim()

        if (title.isBlank()) {
            _state.update { it.copy(error = "Title is required") }
            return
        }

        viewModelScope.launch {
            dao.update(
                material.copy(
                    title = title,
                    content = s.newContent.trim(),
                    category = s.newCategory.trim()
                )
            )
            _state.update {
                it.copy(
                    showEditDialog = false,
                    selectedMaterial = null,
                    newTitle = "",
                    newContent = "",
                    newCategory = "",
                    error = null
                )
            }
        }
    }

    // ── Delete Material ────────────────────────────────────────────────────

    fun showDeleteDialog(material: LearningMaterial) {
        _state.update {
            it.copy(showDeleteDialog = true, selectedMaterial = material)
        }
    }

    fun dismissDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false, selectedMaterial = null) }
    }

    fun confirmDelete() {
        val material = _state.value.selectedMaterial ?: return
        viewModelScope.launch {
            dao.delete(material)
            _state.update {
                it.copy(showDeleteDialog = false, selectedMaterial = null)
            }
        }
    }

    // ── Access tracking ────────────────────────────────────────────────────

    fun markAccessed(material: LearningMaterial) {
        viewModelScope.launch {
            dao.updateLastAccessed(material.id)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
