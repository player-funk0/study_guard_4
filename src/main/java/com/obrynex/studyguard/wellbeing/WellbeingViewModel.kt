package com.obrynex.studyguard.wellbeing

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * UI state for digital wellbeing screen.
 */
data class WellbeingState(
    val todayScreenTimeMs: Long = 0L,
    val topApps: List<AppUsageInfo> = emptyList(),
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false
)

/**
 * Info about a single app's usage.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val icon: android.graphics.drawable.Drawable? = null
)

class WellbeingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(WellbeingState())
    val state: StateFlow<WellbeingState> = _state.asStateFlow()

    private val context get() = getApplication<Application>().applicationContext

    init {
        loadUsageStats()
    }

    fun refresh() = loadUsageStats()

    private fun loadUsageStats() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageManager == null) {
                _state.update { it.copy(isLoading = false, hasPermission = false) }
                return@launch
            }

            // Check if we have permission
            val hasPermission = try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }

            if (!hasPermission) {
                _state.update { it.copy(isLoading = false, hasPermission = false) }
                return@launch
            }

            // Get today's usage
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val usageStats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            ) ?: emptyList()

            val pm = context.packageManager
            val ourPackage = context.packageName

            val appUsageList = usageStats
                .filter { it.totalTimeInForeground > 60_000L } // At least 1 minute
                .filter { it.packageName != ourPackage }
                .sortedByDescending { it.totalTimeInForeground }
                .take(10)
                .mapNotNull { stat ->
                    try {
                        val ai = pm.getApplicationInfo(stat.packageName, 0)
                        AppUsageInfo(
                            packageName = stat.packageName,
                            appName = pm.getApplicationLabel(ai).toString(),
                            usageTimeMs = stat.totalTimeInForeground,
                            icon = pm.getApplicationIcon(ai)
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }

            val totalScreenTime = usageStats
                .filter { it.packageName != ourPackage }
                .sumOf { it.totalTimeInForeground }

            _state.update {
                it.copy(
                    todayScreenTimeMs = totalScreenTime,
                    topApps = appUsageList,
                    isLoading = false,
                    hasPermission = true
                )
            }
        }
    }

    /** Formats milliseconds into human-readable time. */
    companion object {
        fun formatDuration(ms: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(ms)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }
    }
}
