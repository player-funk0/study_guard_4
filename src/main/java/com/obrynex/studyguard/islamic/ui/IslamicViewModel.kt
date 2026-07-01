package com.obrynex.studyguard.islamic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.prefs.PrefsManager
import com.obrynex.studyguard.islamic.data.ALL_HADITHS
import com.obrynex.studyguard.islamic.data.Hadith
import com.obrynex.studyguard.islamic.data.HadithCategory
import com.obrynex.studyguard.islamic.data.dailyHadith
import com.obrynex.studyguard.islamic.data.nawawiHadiths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Islamic tab.
 *
 * @param hadiths          Filtered list shown in the LazyColumn.
 * @param selectedCategory Active category chip, null means "الكل".
 * @param searchQuery      Live search text.
 * @param dailyHadith      Today's featured hadith — changes once per day.
 * @param bookmarkedIds    Set of hadith IDs the user has bookmarked (persisted to DataStore).
 * @param showNawawiOnly   When true, list is restricted to the Forty Nawawi hadiths.
 */
data class IslamicUiState(
    val hadiths          : List<Hadith>    = ALL_HADITHS,
    val selectedCategory : HadithCategory? = null,
    val searchQuery      : String          = "",
    val dailyHadith      : Hadith          = dailyHadith(),
    val bookmarkedIds    : Set<Int>        = emptySet(),
    val showNawawiOnly   : Boolean         = false
)

class IslamicViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: android.content.Context
        get() = getApplication<Application>().applicationContext

    private val _state = MutableStateFlow(IslamicUiState())
    val state: StateFlow<IslamicUiState> = _state.asStateFlow()

    init {
        // Restore persisted bookmarks on creation — this replaces the in-memory default.
        viewModelScope.launch {
            PrefsManager.bookmarkedHadithIds(appContext).collect { ids ->
                _state.update { it.copy(bookmarkedIds = ids) }
            }
        }
    }

    // ── Filtering ──────────────────────────────────────────────────────────

    fun onCategorySelected(category: HadithCategory?) {
        _state.update { current ->
            current.copy(
                selectedCategory = category,
                showNawawiOnly   = false,
                hadiths          = applyFilters(
                    query        = current.searchQuery,
                    category     = category,
                    nawawiOnly   = false
                )
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { current ->
            current.copy(
                searchQuery = query,
                hadiths     = applyFilters(
                    query      = query,
                    category   = current.selectedCategory,
                    nawawiOnly = current.showNawawiOnly
                )
            )
        }
    }

    fun toggleNawawiFilter() {
        _state.update { current ->
            val newNawawi = !current.showNawawiOnly
            current.copy(
                showNawawiOnly   = newNawawi,
                selectedCategory = null,
                hadiths          = applyFilters(
                    query      = current.searchQuery,
                    category   = null,
                    nawawiOnly = newNawawi
                )
            )
        }
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Toggles the bookmark for [hadithId] and immediately persists the updated
     * set to DataStore so it survives process death and app restarts.
     */
    fun toggleBookmark(hadithId: Int) {
        val updated = _state.value.bookmarkedIds.let { current ->
            if (hadithId in current) current - hadithId else current + hadithId
        }
        _state.update { it.copy(bookmarkedIds = updated) }
        viewModelScope.launch {
            PrefsManager.saveBookmarkedHadithIds(appContext, updated)
        }
    }

    fun isBookmarked(hadithId: Int): Boolean =
        _state.value.bookmarkedIds.contains(hadithId)

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun applyFilters(
        query      : String,
        category   : HadithCategory?,
        nawawiOnly : Boolean
    ): List<Hadith> {
        var base = if (nawawiOnly) nawawiHadiths() else ALL_HADITHS

        if (category != null) {
            base = base.filter { it.category == category }
        }

        if (query.isNotBlank()) {
            base = base.filter { hadith ->
                hadith.text.contains(query, ignoreCase = true) ||
                hadith.benefit.contains(query, ignoreCase = true) ||
                hadith.narrator.contains(query, ignoreCase = true) ||
                hadith.source.contains(query, ignoreCase = true)
            }
        }

        return base
    }
}
