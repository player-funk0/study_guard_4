package com.obrynex.studyguard.ai

import android.util.Log
import com.obrynex.studyguard.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Structured logging wrapper for the AI layer.
 *
 * ## Log levels
 *
 * | Level | Constant        | Release | Debug | File |
 * |-------|-----------------|---------|-------|------|
 * | DEBUG | [Level.DEBUG]   | ✗       | ✓     | ✓    |
 * | INFO  | [Level.INFO]    | ✓       | ✓     | ✓    |
 * | WARN  | [Level.WARN]    | ✓       | ✓     | ✓    |
 * | ERROR | [Level.ERROR]   | ✓       | ✓     | ✓    |
 *
 * ## Per-class tag helper
 * Use [tagFor] to derive the tag from the calling class's simple name:
 * ```kotlin
 * private val TAG = AppLogger.tagFor(MyClass::class)
 * ```
 *
 * ## Optional file logging (debug only)
 * Call [enableFileLogging] once at app start to write all log entries to a file.
 * File logging is a no-op in release builds regardless of this setting.
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    // ── File logging ───────────────────────────────────────────────────────────

    @Volatile private var logFile: File? = null
    @Volatile private var fileWriter: PrintWriter? = null

    /** Single-thread executor so file writes never block callers. */
    private val fileExecutor = ThreadPoolExecutor(
        1, 1, 60L, TimeUnit.SECONDS,
        ArrayBlockingQueue(256),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /**
     * Enables writing all log entries to [file].
     * **Debug builds only** — silently ignored in release.
     *
     * Call once from [Application.onCreate]:
     * ```kotlin
     * AppLogger.enableFileLogging(File(filesDir, "ai_debug.log"))
     * ```
     */
    fun enableFileLogging(file: File) {
        if (!BuildConfig.DEBUG) return
        logFile    = file
        fileWriter = PrintWriter(FileWriter(file, /* append= */ true), /* autoFlush= */ true)
        d("AppLogger", "File logging enabled → ${file.absolutePath}")
    }

    /** Flushes and closes the file writer. Safe to call multiple times. */
    fun disableFileLogging() {
        fileWriter?.flush()
        fileWriter?.close()
        fileWriter = null
        logFile    = null
    }

    // ── Tag helper ─────────────────────────────────────────────────────────────

    /**
     * Derives a logcat tag from a Kotlin class reference.
     * Trims to 23 chars to respect logcat's tag limit on older APIs.
     *
     * ```kotlin
     * private val TAG = AppLogger.tagFor(AIEngineManager::class)
     * // → "AIEngineManager"
     * ```
     */
    fun tagFor(kClass: kotlin.reflect.KClass<*>): String =
        (kClass.simpleName ?: "Unknown").take(23)

    // ── Log API ────────────────────────────────────────────────────────────────

    /** DEBUG — stripped in release builds. */
    fun d(tag: String, msg: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, msg)
        writeToFile(Level.DEBUG, tag, msg)
    }

    /** INFO — always logged. */
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile(Level.INFO, tag, msg)
    }

    /** WARN — always logged. */
    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        writeToFile(Level.WARN, tag, msg, t)
    }

    /** ERROR — always logged. */
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        writeToFile(Level.ERROR, tag, msg, t)
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun writeToFile(level: Level, tag: String, msg: String, t: Throwable? = null) {
        val writer = fileWriter ?: return       // file logging not enabled
        if (!BuildConfig.DEBUG) return          // never write to file in release

        val line = buildString {
            append(sdf.format(Date()))
            append(" [${level.name[0]}] $tag: $msg")
            if (t != null) append("\n  ${t::class.simpleName}: ${t.message}")
        }

        fileExecutor.execute {
            try {
                writer.println(line)
            } catch (_: Exception) { /* swallow — logging must never crash */ }
        }
    }
}
