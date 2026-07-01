package com.obrynex.studyguard.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studyguard_prefs")

/**
 * Lightweight preferences manager for app-level settings.
 */
object PrefsManager {

    private val KEY_ONBOARDING_DONE    = booleanPreferencesKey("onboarding_done")
    private val KEY_BOOKMARKED_HADITHS = stringSetPreferencesKey("bookmarked_hadith_ids")

    // ── Onboarding ─────────────────────────────────────────────────────────

    /** Whether the user has completed the onboarding flow. */
    fun onboardingDone(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }

    /** Mark onboarding as completed. */
    suspend fun setOnboardingDone(context: Context) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    // ── Hadith bookmarks ───────────────────────────────────────────────────

    /**
     * Emits the persisted set of bookmarked hadith IDs.
     * The set survives process death and app reinstalls that preserve user data.
     */
    fun bookmarkedHadithIds(context: Context): Flow<Set<Int>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_BOOKMARKED_HADITHS]
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    /**
     * Persists the complete set of bookmarked hadith IDs.
     * Overwrites the previously stored set.
     */
    suspend fun saveBookmarkedHadithIds(context: Context, ids: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BOOKMARKED_HADITHS] = ids.map { it.toString() }.toSet()
        }
    }
}
