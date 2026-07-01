package com.obrynex.studyguard.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** DataStore singleton — one instance per process, keyed by name. */
private val Context.modelHashStore: DataStore<Preferences>
    by preferencesDataStore(name = "model_hash_cache")

/**
 * Persists the last computed SHA-256 hash of the model file, together with
 * the file's [lastModified] and [fileSize] at the time of hashing.
 *
 * ### Versioning
 * [CURRENT_VERSION] is stored alongside the hash. If the stored version does not
 * match [CURRENT_VERSION], the cache is considered stale and is cleared automatically.
 * Bump [CURRENT_VERSION] whenever the model file or hash algorithm changes.
 *
 * ### Corruption fallback
 * [getSnapshot] validates the retrieved hash (must be a 64-char hex string).
 * If the stored value is missing or malformed, the cache is cleared and the
 * snapshot's hash field is null — forcing a full recompute on the next
 * [AIEngineManager.validate] call.
 *
 * ### Single-read design
 * All accessors go through [getSnapshot], which calls `DataStore.data.first()`
 * exactly **once** per logical operation. This eliminates redundant I/O that
 * previously occurred when [AIEngineManager.computeOrCachedSha256] called
 * [getLastFileSize], [getLastModified], and [getCachedHash] as three separate reads.
 *
 * Stored keys in DataStore:
 *  - `cache_version`    → schema version int
 *  - `last_model_hash`  → SHA-256 hex string (64 chars)
 *  - `last_modified`    → file.lastModified() in ms
 *  - `last_file_size`   → file.length() in bytes
 */
open class ModelHashCache(private val context: Context) {

    companion object {
        private const val TAG = "ModelHashCache"

        /** Increment this whenever the model binary or hash algorithm changes. */
        const val CURRENT_VERSION: Int = 1

        /** Valid SHA-256 hex strings are exactly 64 lowercase hex characters. */
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

        private val KEY_VERSION       = intPreferencesKey("cache_version")
        private val KEY_HASH          = stringPreferencesKey("last_model_hash")
        private val KEY_LAST_MODIFIED = longPreferencesKey("last_modified")
        private val KEY_FILE_SIZE     = longPreferencesKey("last_file_size")
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /**
     * All cached values read in a single [DataStore] access.
     *
     * @param hash         SHA-256 hex string, or null if absent / corrupt / version-mismatch.
     * @param lastModified file.lastModified() at last hash time, or -1 if absent.
     * @param fileSize     file.length() at last hash time, or -1 if absent.
     */
    data class Snapshot(
        val hash         : String?,
        val lastModified : Long,
        val fileSize     : Long
    )

    /**
     * Reads all cached values in **one** DataStore access and returns a [Snapshot].
     *
     * Performs version and corruption checks:
     *  - Version mismatch → cache cleared, hash = null.
     *  - Hash not a valid 64-char hex string → cache cleared, hash = null.
     *
     * Callers (primarily [AIEngineManager]) should call this once and read the
     * individual fields from the returned [Snapshot] instead of calling
     * [getCachedHash], [getLastModified], and [getLastFileSize] separately.
     */
    open suspend fun getSnapshot(): Snapshot {
        val prefs = context.modelHashStore.data.first()

        val lastModified = prefs[KEY_LAST_MODIFIED] ?: -1L
        val fileSize     = prefs[KEY_FILE_SIZE]     ?: -1L

        // Version check — stale cache from an older schema
        val storedVersion = prefs[KEY_VERSION] ?: 0
        if (storedVersion != CURRENT_VERSION) {
            AppLogger.w(TAG, "Cache version mismatch (stored=$storedVersion, current=$CURRENT_VERSION) — clearing")
            clear()
            return Snapshot(hash = null, lastModified = lastModified, fileSize = fileSize)
        }

        val hash = prefs[KEY_HASH]

        // Corruption check — must be a valid 64-char hex string
        if (hash == null || !SHA256_REGEX.matches(hash)) {
            AppLogger.w(TAG, "Corrupt cached hash ('$hash') — clearing cache")
            clear()
            return Snapshot(hash = null, lastModified = lastModified, fileSize = fileSize)
        }

        return Snapshot(hash = hash, lastModified = lastModified, fileSize = fileSize)
    }

    // ── Convenience accessors (single-read via getSnapshot) ───────────────────

    /**
     * Returns the cached SHA-256 hex string, or null if:
     *   - Never computed
     *   - Cache schema version does not match [CURRENT_VERSION]
     *   - Stored value is corrupt (not a valid 64-char hex string)
     *
     * Prefer [getSnapshot] when you also need [getLastModified] or [getLastFileSize]
     * to avoid multiple DataStore reads.
     */
    open suspend fun getCachedHash(): String? = getSnapshot().hash

    /** Returns the lastModified timestamp (ms) at last hashing, or -1 if absent. */
    open suspend fun getLastModified(): Long = getSnapshot().lastModified

    /** Returns the file size (bytes) at last hashing, or -1 if absent. */
    open suspend fun getLastFileSize(): Long = getSnapshot().fileSize

    // ── Write / clear ─────────────────────────────────────────────────────────

    /**
     * Atomically stores the hash, version, and file metadata in a single edit.
     * All four values are written together to keep them consistent.
     */
    open suspend fun saveHash(hash: String, lastModified: Long, fileSize: Long) {
        context.modelHashStore.edit { prefs ->
            prefs[KEY_VERSION]       = CURRENT_VERSION
            prefs[KEY_HASH]          = hash
            prefs[KEY_LAST_MODIFIED] = lastModified
            prefs[KEY_FILE_SIZE]     = fileSize
        }
        AppLogger.d(TAG, "Hash saved (version=$CURRENT_VERSION)")
    }

    /**
     * Clears all cached values — useful when the model file is replaced or
     * when a version mismatch / corruption is detected.
     */
    open suspend fun clear() {
        context.modelHashStore.edit { it.clear() }
        AppLogger.d(TAG, "Cache cleared")
    }
}
