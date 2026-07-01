package com.obrynex.studyguard.di

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.ModelHashCache
import com.obrynex.studyguard.data.AppDatabase
import com.obrynex.studyguard.data.StudySessionDao
import com.obrynex.studyguard.learningmaterials.LearningMaterialDao
import com.obrynex.studyguard.textrank.SummarizeWithTextRankUseCase
import kotlinx.coroutines.flow.map

/**
 * Manual dependency injection container.
 *
 * Initialized from [com.obrynex.studyguard.StudyGuardApplication.onCreate].
 * Provides singleton access to all app-wide services.
 */
@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    private lateinit var appContext: Context
    private var _database: AppDatabase? = null
    private var _aiEngineManager: AIEngineManager? = null
    private var _hashCache: ModelHashCache? = null
    private var _studySessionDao: StudySessionDao? = null
    private var _learningMaterialDao: LearningMaterialDao? = null

    /** Initialize all singletons. Call once from Application.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Application context — safe to call after [init]. */
    val context: Context get() = appContext

    /** Room database singleton. */
    val database: AppDatabase
        get() = _database ?: synchronized(this) {
            _database ?: Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "studyguard.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { _database = it }
        }

    /** Study session DAO. */
    val studySessionDao: StudySessionDao
        get() = _studySessionDao ?: synchronized(this) {
            _studySessionDao ?: database.studySessionDao().also { _studySessionDao = it }
        }

    /** Learning material DAO. */
    val learningMaterialDao: LearningMaterialDao
        get() = _learningMaterialDao ?: synchronized(this) {
            _learningMaterialDao ?: database.learningMaterialDao().also { _learningMaterialDao = it }
        }

    /** AI engine manager singleton. */
    val aiEngineManager: AIEngineManager
        get() = _aiEngineManager ?: synchronized(this) {
            _aiEngineManager ?: AIEngineManager(appContext).also { _aiEngineManager = it }
        }

    /** Model hash cache singleton. */
    val modelHashCache: ModelHashCache
        get() = _hashCache ?: synchronized(this) {
            _hashCache ?: ModelHashCache(appContext).also { _hashCache = it }
        }

    /** TextRank summarizer use case. */
    val summarizeTextUseCase: SummarizeWithTextRankUseCase by lazy {
        SummarizeWithTextRankUseCase()
    }

    /** Get streak use case — returns consecutive days with completed sessions. */
    val getStreakUseCase: GetStreakUseCase by lazy {
        GetStreakUseCase(studySessionDao)
    }
}

/**
 * Calculates the user's current study streak (consecutive days with at least one
 * completed session).
 */
class GetStreakUseCase(private val dao: StudySessionDao) {
    /** Execute the streak calculation. */
    operator fun invoke(): kotlinx.coroutines.flow.Flow<Int> {
        // Calculate start of the last 365 days for streak lookup
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -365)
        val since = calendar.timeInMillis
        return dao.getSince(since).map { sessions ->
            calculateStreak(sessions.filter { it.completed })
        }
    }

    internal fun calculateStreak(completedSessions: List<com.obrynex.studyguard.data.StudySession>): Int {
        if (completedSessions.isEmpty()) return 0

        val sessionDays = completedSessions
            .map { session ->
                val cal = java.util.Calendar.getInstance().apply { time = session.date }
                // Normalize to day start
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .toSortedSet()
            .toList()

        if (sessionDays.isEmpty()) return 0

        fun previousDayStart(dayStartMillis: Long): Long =
            java.util.Calendar.getInstance().apply {
                timeInMillis = dayStartMillis
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis

        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = previousDayStart(today)

        // If no session today or yesterday, streak is broken
        val mostRecent = sessionDays.last()
        if (mostRecent < yesterday) return 0

        // Count consecutive days backwards from most recent
        var streak = 1
        var expectedPrev = previousDayStart(mostRecent)
        for (i in sessionDays.size - 2 downTo 0) {
            if (sessionDays[i] == expectedPrev) {
                streak++
                expectedPrev = previousDayStart(sessionDays[i])
            } else if (sessionDays[i] < expectedPrev) {
                break
            }
        }
        return streak
    }
}
