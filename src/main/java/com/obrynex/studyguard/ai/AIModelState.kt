package com.obrynex.studyguard.ai

/**
 * Exhaustive state machine for the on-device AI engine.
 * [AIEngineManager] is the only class allowed to emit these states.
 */
sealed class AIModelState {

    /** Initial state — dormant, no validation attempted. Also state after [AIEngineManager.releaseEngine]. */
    object Idle : AIModelState()

    /** File explicitly checked and not found. Distinct from [Idle]. */
    object NotFound : AIModelState()

    /** File found; running existence / size / checksum gates. */
    object Validating : AIModelState()

    /** All gates passed; MediaPipe LlmInference is initialising. */
    object Loading : AIModelState()

    /** Engine is live and ready to accept prompts. */
    object Ready : AIModelState()

    /** At least one gate failed — inspect [reason]. */
    data class Failed(val reason: ValidationFailure) : AIModelState()
}

/** Describes exactly which validation gate failed and why. */
sealed class ValidationFailure {

    object FileNotFound : ValidationFailure()

    data class SizeTooSmall(
        val actualBytes  : Long,
        val minimumBytes : Long
    ) : ValidationFailure()

    data class ChecksumMismatch(
        val expected : String,
        val actual   : String
    ) : ValidationFailure()

    data class LoadFailed(val cause: Throwable) : ValidationFailure()

    /**
     * System RAM is below [AIEngineManager.MIN_FREE_RAM_BYTES].
     * Loading a 1.35 GB model would likely cause an OOM crash or severe jank.
     */
    data class InsufficientRam(
        val availableBytes : Long,
        val requiredBytes  : Long
    ) : ValidationFailure()

    /** Arabic user-facing message for display in the UI. */
    fun toArabicMessage(): String = when (this) {
        is FileNotFound ->
            "ملف النموذج غير موجود.\nتأكد من نقل الملف إلى:\n${AIEngineManager.MODEL_RELATIVE_PATH}"
        is SizeTooSmall ->
            "حجم الملف ${actualBytes / 1_000_000} MB — أصغر من الحد الأدنى ${minimumBytes / 1_000_000} MB.\n" +
            "يبدو أن التحميل لم يكتمل. أعد تحميل النموذج من Kaggle."
        is ChecksumMismatch ->
            "الملف تالف أو إصدار مختلف عن المتوقع.\nأعد تحميل النموذج من Kaggle."
        is LoadFailed ->
            "فشل تحميل النموذج في MediaPipe:\n${cause.message}"
        is InsufficientRam ->
            "الذاكرة الخالية غير كافية (${availableBytes / (1024 * 1024)} MB).\n" +
            "أغلق التطبيقات الأخرى وحاول مجدداً."
    }

    /** English developer message for debug screen / logs. */
    fun toDebugMessage(): String = when (this) {
        is FileNotFound     -> "FileNotFound"
        is SizeTooSmall     -> "SizeTooSmall: ${actualBytes}B < ${minimumBytes}B"
        is ChecksumMismatch -> "ChecksumMismatch\n  expected=$expected\n  actual  =$actual"
        is LoadFailed       -> "LoadFailed: ${cause::class.simpleName}: ${cause.message}"
        is InsufficientRam  -> "InsufficientRam: ${availableBytes / (1024 * 1024)}MB available, ${requiredBytes / (1024 * 1024)}MB required"
    }
}
