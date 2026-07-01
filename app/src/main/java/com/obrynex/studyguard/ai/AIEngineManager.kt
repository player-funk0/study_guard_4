package com.obrynex.studyguard.ai

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for the on-device AI model lifecycle.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Thread-safety guarantees
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *  - [initMutex] ensures only one [validate] call runs at a time — concurrent
 *    callers wait at the lock and then exit early via the state check.
 *  - [isInitializing] is an atomic flag for fast, lock-free early-exit checks
 *    before acquiring the mutex.
 *  - Backoff state ([reInitCount], [lastReInitMs]) is guarded by [initMutex].
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * State machine
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   Idle ──validate()──► Validating ──gates ok──► Loading ──warmUp ok──► Ready
 *                            │                        │                     │
 *                            └────── Failed ──────────┘             releaseEngine()
 *                                                                           │
 *                                                                         Idle
 *
 * @param context       Application context.
 * @param hashCache     DataStore-backed hash cache; injectable for testing.
 * @param chunkBytes    Buffer size for SHA-256 streaming reads.
 */
open class AIEngineManager(
    private val context    : Context,
    private val hashCache  : ModelHashCache = ModelHashCache(context),
    val          chunkBytes: Int            = DEFAULT_CHUNK_BYTES
) {

    // ── Constants ──────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AIEngineManager"

        const val MODEL_MIN_BYTES: Long = 1_200_000_000L
        /**
         * Expected SHA-256 hex digest of the model binary, or null to skip integrity checking.
         *
         * ### Why null by default
         * The Gemma 2B-IT model is distributed as a single large binary (≈1.35 GB).
         * Computing its SHA-256 on first launch takes ~30–60 s on mid-range devices.
         * Integrity checking is therefore **opt-in**: set this to the known-good digest
         * when distributing a pinned model version, and bump [ModelHashCache.CURRENT_VERSION]
         * so any stale cached hash is discarded.
         *
         * ### How to enable
         * Replace null with the lowercase hex SHA-256 of the model file, e.g.:
         * ```kotlin
         * const val EXPECTED_SHA256: String? = "4e9a3b2c…"   // 64 hex chars
         * ```
         * Gate 3 in [runValidationGates] will then verify the file on first launch
         * and cache the result so subsequent launches skip the recompute.
         */
        val EXPECTED_SHA256: String? = null
        const val MODEL_RELATIVE_PATH = "models/${LocalAiEngine.MODEL_FILENAME}"
        const val DEFAULT_CHUNK_BYTES: Int = 8 * 1024 * 1024

        /** Timeout for the SHA-256 hashing step (5 minutes). */
        const val HASH_TIMEOUT_MS: Long = 5 * 60 * 1_000L

        /** Timeout for each [LocalAiEngine.warmUp] attempt. */
        const val WARMUP_TIMEOUT_MS: Long = 60_000L

        /** Hard cap on total time from validate() start to Ready. */
        const val GLOBAL_INIT_TIMEOUT_MS: Long = 10 * 60 * 1_000L

        /** Maximum warmUp attempts before transitioning to [AIModelState.Failed]. */
        const val MAX_WARMUP_ATTEMPTS: Int = 2

        /** Minimum free RAM required before loading the model (512 MB). */
        const val MIN_FREE_RAM_BYTES: Long = 512L * 1024 * 1024

        /** Minimum interval between re-init calls (throttle guard). */
        const val RE_INIT_THROTTLE_MS: Long = 3_000L

        /** Backoff delays (ms) per re-init attempt index. */
        val BACKOFF_DELAYS_MS: LongArray = longArrayOf(0L, 2_000L, 5_000L, 10_000L)
    }

    // ── Mutex & atomic flags ───────────────────────────────────────────────────

    /**
     * Ensures that only one [validate] coroutine runs at a time.
     * All other concurrent callers will suspend at the lock boundary and then
     * exit immediately when they see the state is no longer Idle.
     */
    private val initMutex = Mutex()

    /** Fast, lock-free flag readable before acquiring [initMutex]. */
    val isInitializing: AtomicBoolean = AtomicBoolean(false)

    /** Counts how many times [requestReInit] has been called (for backoff). */
    private val reInitCount: AtomicInteger = AtomicInteger(0)

    /** Epoch-ms of the last [requestReInit] call (for throttling). */
    private val lastReInitMs: AtomicLong = AtomicLong(0L)

    // ── State ──────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<AIModelState>(AIModelState.Idle)

    /** Public, read-only StateFlow — collect this in ViewModels. */
    open val state: StateFlow<AIModelState> = _state.asStateFlow()

    private var engine: LocalAiEngine? = null

    // ── Diagnostics ────────────────────────────────────────────────────────────

    /** Wall-clock time in ms from validate() start to Ready, or -1 if not yet. */
    open var lastLoadTimeMs: Long = -1L
        protected set

    /** Last exception that caused a [AIModelState.Failed] transition. */
    open var lastError: Throwable? = null
        protected set

    /** SHA-256 of the model file as last computed/cached, or null. */
    open var lastComputedHash: String? = null
        protected set

    // ── Public API ─────────────────────────────────────────────────────────────

    open val modelFilePath: String
        get() = File(context.getExternalFilesDir(null), MODEL_RELATIVE_PATH).absolutePath

    /** Returns the live engine. Non-null ONLY when state is [AIModelState.Ready]. */
    open fun getEngine(): LocalAiEngine? = engine

    /**
     * Resets diagnostic fields to their default "never-run" values.
     * Useful when the model file is replaced and a fresh run is expected.
     */
    fun clearDiagnostics() {
        lastLoadTimeMs   = -1L
        lastError        = null
        lastComputedHash = null
        AppLogger.d(TAG, "clearDiagnostics() — diagnostics reset")
    }

    /**
     * Runs all validation gates then warms up the engine, guarded by [initMutex].
     *
     * Thread-safety: at most one coroutine proceeds past the lock at a time.
     * Concurrent callers wait at the mutex, then exit because state is no longer Idle.
     *
     * Fast pre-check (no lock needed): [isInitializing] flag avoids even trying
     * to acquire the mutex when init is obviously already running.
     *
     * Gate pipeline:
     *   0. RAM gate (low RAM → fail early)
     *   1. File existence
     *   2. Minimum file size
     *   3. SHA-256 checksum (opt-in)
     *   4. warmUp (retried up to [MAX_WARMUP_ATTEMPTS] times)
     *
     * Wrapped in [GLOBAL_INIT_TIMEOUT_MS] to prevent runaway hangs.
     */
    open suspend fun validate() {
        // Fast lock-free pre-check
        if (isInitializing.get()) {
            AppLogger.d(TAG, "validate() — skipped (already initialising)")
            return
        }

        initMutex.withLock {
            // Re-check inside the lock: another caller may have just finished
            when (_state.value) {
                is AIModelState.Validating,
                is AIModelState.Loading,
                is AIModelState.Ready -> {
                    AppLogger.d(TAG, "validate() — skipped inside lock (state=${_state.value::class.simpleName})")
                    return
                }
                else -> Unit
            }

            isInitializing.set(true)
            try {
                runValidateLocked()
            } finally {
                isInitializing.set(false)
            }
        }
    }

    /** Internal implementation — must only be called while [initMutex] is held. */
    private suspend fun runValidateLocked() {
        AppLogger.d(TAG, "validate() — starting from state=${_state.value::class.simpleName}")

        try {
            withTimeout(GLOBAL_INIT_TIMEOUT_MS) {
                _state.value = AIModelState.Validating

                // ── Gate phase ─────────────────────────────────────────────────
                val failure = withContext(Dispatchers.IO) { runValidationGates() }
                if (failure != null) {
                    AppLogger.w(TAG, "Validation gate failed: ${failure.toDebugMessage()}")
                    lastError    = (failure as? ValidationFailure.LoadFailed)?.cause
                    _state.value = if (failure is ValidationFailure.FileNotFound)
                        AIModelState.NotFound
                    else
                        AIModelState.Failed(failure)
                    return@withTimeout
                }

                // ── Load + warm-up phase ───────────────────────────────────────
                _state.value = AIModelState.Loading
                val startMs  = System.currentTimeMillis()
                var lastException: Throwable? = null

                repeat(MAX_WARMUP_ATTEMPTS) { attempt ->
                    if (_state.value !is AIModelState.Loading) return@repeat

                    AppLogger.d(TAG, "warmUp attempt ${attempt + 1}/$MAX_WARMUP_ATTEMPTS")

                    withContext(Dispatchers.Default) {
                        runCatching {
                            val eng = LocalAiEngine(context)
                            withTimeout(WARMUP_TIMEOUT_MS) { eng.warmUp() }
                            eng
                        }
                    }.fold(
                        onSuccess = { eng ->
                            engine         = eng
                            lastLoadTimeMs = System.currentTimeMillis() - startMs
                            lastError      = null
                            _state.value   = AIModelState.Ready
                            AppLogger.i(TAG, "Engine ready — loadTime=${lastLoadTimeMs}ms")
                        },
                        onFailure = { ex ->
                            lastException = ex
                            AppLogger.w(TAG, "warmUp attempt ${attempt + 1} failed: ${ex.message}")
                            engine?.safeClose()
                            engine = null
                        }
                    )
                }

                if (_state.value !is AIModelState.Ready) {
                    lastError    = lastException
                    _state.value = AIModelState.Failed(
                        ValidationFailure.LoadFailed(
                            lastException ?: RuntimeException("Unknown warmUp failure")
                        )
                    )
                    AppLogger.e(TAG, "All warmUp attempts failed", lastException)
                }
            }
        } catch (ex: kotlinx.coroutines.TimeoutCancellationException) {
            AppLogger.e(TAG, "Global init timeout (${GLOBAL_INIT_TIMEOUT_MS}ms) exceeded", ex)
            lastError    = ex
            _state.value = AIModelState.Failed(
                ValidationFailure.LoadFailed(RuntimeException("Init timed out after ${GLOBAL_INIT_TIMEOUT_MS}ms", ex))
            )
            engine?.safeClose()
            engine = null
        }
    }

    /**
     * Re-initialises the engine from scratch with throttling + exponential backoff.
     *
     * Throttle: if called within [RE_INIT_THROTTLE_MS] of the previous call,
     * the request is silently dropped to prevent UI button-hammering.
     *
     * Backoff: the delay grows with each call, capped at [BACKOFF_DELAYS_MS].last().
     */
    open suspend fun requestReInit() {
        val now = SystemClock.elapsedRealtime()
        val last = lastReInitMs.get()

        // Throttle guard
        if (now - last < RE_INIT_THROTTLE_MS) {
            AppLogger.d(TAG, "requestReInit() throttled — too soon after last call")
            return
        }
        lastReInitMs.set(now)

        val attempt = reInitCount.getAndIncrement()
        val backoff = BACKOFF_DELAYS_MS.getOrElse(attempt) { BACKOFF_DELAYS_MS.last() }

        AppLogger.d(TAG, "requestReInit() attempt=$attempt backoff=${backoff}ms")
        if (backoff > 0) delay(backoff)

        engine?.safeClose()
        engine       = null
        _state.value = AIModelState.Idle
        validate()
    }

    /**
     * Closes the MediaPipe session, freeing the ~1.35 GB native allocation.
     * Also resets the re-init backoff counter so fresh retries start clean.
     */
    open fun releaseEngine() {
        AppLogger.d(TAG, "releaseEngine() — freeing native allocation")
        engine?.safeClose()
        engine = null
        reInitCount.set(0)
        lastReInitMs.set(0L)
        _state.value = AIModelState.Idle
    }

    // ── Validation gates ───────────────────────────────────────────────────────

    private suspend fun runValidationGates(): ValidationFailure? {
        val file = File(context.getExternalFilesDir(null), MODEL_RELATIVE_PATH)

        // Gate 0 — RAM check (fail early before loading a 1.35 GB model)
        val freeRam = availableFreeRam()
        if (freeRam < MIN_FREE_RAM_BYTES) {
            AppLogger.w(TAG, "Gate 0 failed: freeRam=${freeRam / (1024 * 1024)}MB < ${MIN_FREE_RAM_BYTES / (1024 * 1024)}MB")
            return ValidationFailure.InsufficientRam(
                availableBytes = freeRam,
                requiredBytes  = MIN_FREE_RAM_BYTES
            )
        }
        AppLogger.d(TAG, "Gate 0 passed: freeRam=${freeRam / (1024 * 1024)}MB")

        // Gate 1 — existence
        if (!file.exists()) {
            AppLogger.w(TAG, "Gate 1 failed: file not found at ${file.absolutePath}")
            return ValidationFailure.FileNotFound
        }

        // Gate 2 — minimum size
        if (file.length() < MODEL_MIN_BYTES) {
            AppLogger.w(TAG, "Gate 2 failed: ${file.length()}B < ${MODEL_MIN_BYTES}B")
            return ValidationFailure.SizeTooSmall(
                actualBytes  = file.length(),
                minimumBytes = MODEL_MIN_BYTES
            )
        }

        // Gate 3 — SHA-256 integrity (opt-in)
        EXPECTED_SHA256?.let { expected ->
            val actual = computeOrCachedSha256(file)
            if (!actual.equals(expected, ignoreCase = true)) {
                AppLogger.w(TAG, "Gate 3 failed: hash mismatch")
                return ValidationFailure.ChecksumMismatch(expected, actual)
            }
            AppLogger.d(TAG, "Gate 3 passed: checksum ok")
        } ?: AppLogger.d(TAG, "Gate 3 skipped: EXPECTED_SHA256 not set")

        return null
    }

    private suspend fun computeOrCachedSha256(file: File): String {
        val currentSize     = file.length()
        val currentModified = file.lastModified()

        // Single DataStore read — avoids 3 separate data.first() calls that were
        // previously triggered by getLastFileSize(), getLastModified(), getCachedHash().
        val snapshot = hashCache.getSnapshot()
        if (currentSize     == snapshot.fileSize &&
            currentModified == snapshot.lastModified) {
            snapshot.hash?.let { cached ->
                AppLogger.d(TAG, "Pre-hash check passed — using cached SHA-256")
                lastComputedHash = cached
                return cached
            }
        }

        AppLogger.d(TAG, "Computing SHA-256 (${currentSize / 1_000_000} MB, chunk=${chunkBytes / 1024} KB)…")
        val hash = withTimeout(HASH_TIMEOUT_MS) { sha256hex(file) }

        hashCache.saveHash(hash, currentModified, currentSize)
        lastComputedHash = hash
        AppLogger.d(TAG, "SHA-256 computed and cached: $hash")
        return hash
    }

    private suspend fun sha256hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(chunkBytes)
        file.inputStream().buffered(buffer.size).use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                currentCoroutineContext().ensureActive()
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns approximate available free RAM in bytes.
     * Uses [ActivityManager.MemoryInfo.availMem] which reflects the full system view
     * (not just this process's heap).
     */
    private fun availableFreeRam(): Long {
        val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }
}
