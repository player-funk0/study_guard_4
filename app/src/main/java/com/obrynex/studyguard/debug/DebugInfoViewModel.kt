package com.obrynex.studyguard.debug

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ai.ModelHashCache

/**
 * @param stateName         Human-readable name of the current [AIModelState].
 * @param hash              Last computed / cached SHA-256, or null if never computed.
 * @param loadTimeMs        Wall-clock ms from validate() start to Ready, or -1.
 * @param errorMessage      Developer-readable error string, or null if no error.
 * @param cachedHash        Persisted SHA-256 from DataStore (survives process death).
 * @param cachedSize        File-size snapshot stored alongside [cachedHash].
 * @param cachedModified    Last-modified ms snapshot stored alongside [cachedHash].
 * @param totalRamMb        Total device RAM in MB.
 * @param availRamMb        Available (free) RAM in MB at last refresh.
 * @param isLowMemory       True if [ActivityManager.MemoryInfo.lowMemory] is set.
 * @param isForceReiniting  True while a force re-init triggered from this screen is running.
 */
data class DebugInfoState(
    val stateName       : String  = "—",
    val hash            : String? = null,
    val loadTimeMs      : Long    = -1L,
    val errorMessage    : String? = null,
    val cachedHash      : String? = null,
    val cachedSize      : Long    = -1L,
    val cachedModified  : Long    = -1L,
    val totalRamMb      : Long    = -1L,
    val availRamMb      : Long    = -1L,
    val isLowMemory     : Boolean = false,
    val isForceReiniting: Boolean = false
)

/**
 * ViewModel for the optional Debug / Diagnostics screen.
 *
 * New capabilities vs. previous version:
 *  - [forceReInit] — triggers a full engine teardown + re-init from the debug screen.
 *  - RAM info ([DebugInfoState.totalRamMb], [availRamMb], [isLowMemory]) refreshed on every [refresh] call.
 */
class DebugInfoViewModel(
    private val manager   : AIEngineManager,
    private val hashCache : ModelHashCache,
    context               : Context
) : ViewModel() {

    private val activityManager: ActivityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _state = MutableStateFlow(DebugInfoState())
    val state: StateFlow<DebugInfoState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            manager.state.collect { engineState ->
                refreshDiagnostics(engineState)
            }
        }
    }

    /** Manually refresh diagnostics snapshot (e.g. pull-to-refresh or button tap). */
    fun refresh() {
        viewModelScope.launch { refreshDiagnostics(manager.state.value) }
    }

    /**
     * Triggers a full force re-initialisation from the debug screen.
     *
     * Sets [DebugInfoState.isForceReiniting] to true while running so the UI
     * can disable the button.  The flag is cleared when the engine transitions
     * out of Loading/Validating.
     */
    fun forceReInit() {
        if (_state.value.isForceReiniting) return
        _state.update { it.copy(isForceReiniting = true) }
        viewModelScope.launch {
            try {
                manager.requestReInit()
            } finally {
                _state.update { it.copy(isForceReiniting = false) }
            }
        }
    }

    private suspend fun refreshDiagnostics(engineState: AIModelState) {
        val memInfo = readMemoryInfo()
        val errorMessage = when (engineState) {
            is AIModelState.Failed -> engineState.reason.toDebugMessage()
            else                   -> manager.lastError?.let {
                "${it::class.simpleName}: ${it.message}"
            }
        }

        _state.update {
            DebugInfoState(
                stateName        = engineState::class.simpleName ?: "Unknown",
                hash             = manager.lastComputedHash,
                loadTimeMs       = manager.lastLoadTimeMs,
                errorMessage     = errorMessage,
                cachedHash       = hashCache.getCachedHash(),
                cachedSize       = hashCache.getLastFileSize(),
                cachedModified   = hashCache.getLastModified(),
                totalRamMb       = memInfo.totalMem / (1024 * 1024),
                availRamMb       = memInfo.availMem / (1024 * 1024),
                isLowMemory      = memInfo.lowMemory,
                isForceReiniting = it.isForceReiniting
            )
        }
    }

    private fun readMemoryInfo(): ActivityManager.MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info
    }
}
