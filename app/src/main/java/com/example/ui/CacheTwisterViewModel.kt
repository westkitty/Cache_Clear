package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppCacheEntry
import com.example.data.CachePreferencesRepository
import com.example.data.CleanPhase
import com.example.data.CleanSessionStats
import com.example.data.DeviceStorageSummary
import com.example.data.SafeCacheManager
import com.example.data.VerificationReport
import com.example.data.formatBytes
import com.example.widget.CacheTwisterWidgetProvider
import com.example.widget.MiniTornadoWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CacheTwisterUiState(
    val isScanning: Boolean = true,
    val apps: List<AppCacheEntry> = emptyList(),
    val storageSummary: DeviceStorageSummary = DeviceStorageSummary(),
    val hasUsageAccess: Boolean = false,
    val phase: CleanPhase = CleanPhase.IDLE,
    val sweepProgress: Float = 0f,
    val currentlySweepingLabel: String = "",
    val verificationReport: VerificationReport? = null,
    val sessionFreedBytes: Long = 0L,
    val clearedPackagesThisSession: Set<String> = emptySet(),
    val stats: CleanSessionStats = CleanSessionStats(),
    val activeMiniWidgetCount: Int = 0,
    val activeFullWidgetCount: Int = 0,
    val statusMessage: String? = null
) {
    val totalActiveWidgets: Int
        get() = activeMiniWidgetCount + activeFullWidgetCount
    val selectedApps: List<AppCacheEntry>
        get() = apps.filter { it.isSelectedForClean }

    val totalMeasuredCacheBytes: Long
        get() = apps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }

    val topCacheFootprintApps: List<AppCacheEntry>
        get() {
            val measuredWithCache = apps
                .filter { it.cacheMeasured && it.cacheBytes > 0L }
                .sortedByDescending { it.cacheBytes }
            return if (measuredWithCache.isNotEmpty()) {
                measuredWithCache.take(5)
            } else {
                apps.sortedByDescending { it.cacheBytes }.take(5)
            }
        }

    val largestSingleAppCacheBytes: Long
        get() = topCacheFootprintApps.maxOfOrNull { it.cacheBytes } ?: 0L

    val nextGuidedApp: AppCacheEntry?
        get() = apps.firstOrNull { entry ->
            entry.isSelectedForClean &&
                !entry.isOwnApp &&
                !entry.isClearedThisSession &&
                (!entry.cacheMeasured || entry.cacheBytes > 16_384L)
        }
}

class CacheTwisterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val cacheManager = SafeCacheManager(appContext)
    private val prefsRepo = CachePreferencesRepository(appContext)

    private val _uiState = MutableStateFlow(CacheTwisterUiState())
    val uiState: StateFlow<CacheTwisterUiState> = _uiState.asStateFlow()

    private var activeSweepJob: Job? = null
    private var pendingExternalPackage: String? = null
    private var pendingExternalBeforeBytes: Long = 0L

    init {
        viewModelScope.launch {
            prefsRepo.statsFlow.collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
        refreshScan()
    }

    fun refreshScan(preserveSelection: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val previousSelection = if (preserveSelection) {
                _uiState.value.apps.associate { it.packageName to it.isSelectedForClean }
            } else {
                emptyMap()
            }
            val clearedSet = _uiState.value.clearedPackagesThisSession

            val (storage, usageGranted, scannedApps) = withContext(Dispatchers.IO) {
                val storageSummary = cacheManager.getDeviceStorageSummary()
                val usage = cacheManager.hasUsageStatsPermission()
                val rawApps = cacheManager.scanAvailableApps(clearedSet)
                val merged = rawApps.map { entry ->
                    val prev = previousSelection[entry.packageName]
                    if (prev != null) entry.copy(isSelectedForClean = prev) else entry
                }
                Triple(storageSummary, usage, merged)
            }

            val totalMeasured = scannedApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
            prefsRepo.updateDetectedCacheSnapshot(
                totalDetectedCacheBytes = totalMeasured,
                appsCount = scannedApps.size
            )
            CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
            MiniTornadoWidgetProvider.refreshAllMiniWidgets(appContext)
            val miniCount = MiniTornadoWidgetProvider.getActiveWidgetCount(appContext)
            val fullCount = CacheTwisterWidgetProvider.getActiveWidgetCount(appContext)

            _uiState.update { current ->
                val updatedReport = if (current.phase == CleanPhase.CONFIRM_READY) {
                    cacheManager.buildVerificationReport(scannedApps)
                } else {
                    current.verificationReport
                }
                current.copy(
                    isScanning = false,
                    apps = scannedApps,
                    storageSummary = storage,
                    hasUsageAccess = usageGranted,
                    activeMiniWidgetCount = miniCount,
                    activeFullWidgetCount = fullCount,
                    verificationReport = updatedReport
                )
            }
        }
    }

    fun onAppResumed() {
        val checkedPkg = pendingExternalPackage
        val beforeBytes = pendingExternalBeforeBytes
        pendingExternalPackage = null
        pendingExternalBeforeBytes = 0L

        viewModelScope.launch {
            val (storage, usageGranted, scannedApps) = withContext(Dispatchers.IO) {
                Triple(
                    cacheManager.getDeviceStorageSummary(),
                    cacheManager.hasUsageStatsPermission(),
                    cacheManager.scanAvailableApps(_uiState.value.clearedPackagesThisSession)
                )
            }

            val previousSelection = _uiState.value.apps.associate { it.packageName to it.isSelectedForClean }
            var deltaFreed = 0L
            var updatedCleared = _uiState.value.clearedPackagesThisSession
            var banner: String? = null

            if (checkedPkg != null) {
                val refreshedEntry = scannedApps.firstOrNull { it.packageName == checkedPkg }
                if (refreshedEntry != null) {
                    if (refreshedEntry.cacheMeasured && beforeBytes > refreshedEntry.cacheBytes) {
                        deltaFreed = beforeBytes - refreshedEntry.cacheBytes
                        updatedCleared = updatedCleared + checkedPkg
                        banner = "Verified ${refreshedEntry.appName}: ${formatBytes(deltaFreed)} cleared"
                    } else if (!refreshedEntry.cacheMeasured) {
                        updatedCleared = updatedCleared + checkedPkg
                        banner = "Checked ${refreshedEntry.appName} in Android Storage"
                    } else if (refreshedEntry.cacheBytes < 16_384L) {
                        updatedCleared = updatedCleared + checkedPkg
                        banner = "Verified ${refreshedEntry.appName}: cache is clean"
                    }
                }
            }

            val mergedApps = scannedApps.map { entry ->
                val prevSel = previousSelection[entry.packageName]
                entry.copy(
                    isSelectedForClean = prevSel ?: entry.isSelectedForClean,
                    isClearedThisSession = updatedCleared.contains(entry.packageName) &&
                        (!entry.cacheMeasured || entry.cacheBytes < 16_384L)
                )
            }

            val totalMeasured = mergedApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
            if (deltaFreed > 0L) {
                prefsRepo.recordVerifiedClean(
                    bytesFreed = deltaFreed,
                    verifiedAppsCount = mergedApps.size,
                    totalDetectedCacheBytes = totalMeasured
                )
            } else {
                prefsRepo.updateDetectedCacheSnapshot(totalMeasured, mergedApps.size)
            }
            CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
            MiniTornadoWidgetProvider.refreshAllMiniWidgets(appContext)
            val miniCount = MiniTornadoWidgetProvider.getActiveWidgetCount(appContext)
            val fullCount = CacheTwisterWidgetProvider.getActiveWidgetCount(appContext)

            _uiState.update { current ->
                current.copy(
                    isScanning = false,
                    apps = mergedApps,
                    storageSummary = storage,
                    hasUsageAccess = usageGranted,
                    activeMiniWidgetCount = miniCount,
                    activeFullWidgetCount = fullCount,
                    clearedPackagesThisSession = updatedCleared,
                    sessionFreedBytes = current.sessionFreedBytes + deltaFreed,
                    statusMessage = banner ?: current.statusMessage,
                    verificationReport = if (current.phase == CleanPhase.CONFIRM_READY) {
                        cacheManager.buildVerificationReport(mergedApps)
                    } else {
                        current.verificationReport
                    }
                )
            }
        }
    }

    fun startPreCleanVerification() {
        if (_uiState.value.phase == CleanPhase.TORNADO_SWEEPING ||
            _uiState.value.phase == CleanPhase.VERIFYING
        ) return

        activeSweepJob?.cancel()
        activeSweepJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = CleanPhase.VERIFYING,
                    currentlySweepingLabel = "Running 4-point safety verification…",
                    statusMessage = null
                )
            }

            val (storage, usageGranted, scannedApps) = withContext(Dispatchers.IO) {
                val previousSelection = _uiState.value.apps.associate { it.packageName to it.isSelectedForClean }
                val fresh = cacheManager.scanAvailableApps(_uiState.value.clearedPackagesThisSession).map { entry ->
                    entry.copy(isSelectedForClean = previousSelection[entry.packageName] ?: entry.isSelectedForClean)
                }
                Triple(cacheManager.getDeviceStorageSummary(), cacheManager.hasUsageStatsPermission(), fresh)
            }

            // Brief deliberate verification pause so user observes the safety check sequence
            delay(750L)

            val report = withContext(Dispatchers.IO) {
                cacheManager.buildVerificationReport(scannedApps)
            }

            _uiState.update {
                it.copy(
                    isScanning = false,
                    apps = scannedApps,
                    storageSummary = storage,
                    hasUsageAccess = usageGranted,
                    verificationReport = report,
                    phase = CleanPhase.CONFIRM_READY,
                    currentlySweepingLabel = "Safety verified · Ready to sweep"
                )
            }
        }
    }

    fun confirmAndStartTornadoClean() {
        val current = _uiState.value
        if (current.phase == CleanPhase.TORNADO_SWEEPING) return

        activeSweepJob?.cancel()
        activeSweepJob = viewModelScope.launch {
            val targets = current.selectedApps.ifEmpty { current.apps }
            _uiState.update {
                it.copy(
                    phase = CleanPhase.TORNADO_SWEEPING,
                    sweepProgress = 0.04f,
                    currentlySweepingLabel = "Spinning up safe vortex…",
                    statusMessage = null
                )
            }

            if (current.stats.hapticEnabled) {
                triggerTornadoHaptic()
            }

            var ownFreedBytes = 0L
            val totalSteps = 26
            for (step in 1..totalSteps) {
                val progress = step.toFloat() / totalSteps.toFloat()
                val label = if (targets.isNotEmpty()) {
                    val idx = ((step - 1) * targets.size / totalSteps).coerceIn(0, targets.lastIndex)
                    val target = targets[idx]
                    if (target.isOwnApp) "Sweeping Cache Twister cache…" else "Verifying ${target.appName}…"
                } else {
                    "Sweeping local cache…"
                }

                if (step == 12) {
                    ownFreedBytes = withContext(Dispatchers.IO) {
                        cacheManager.clearOwnAppCacheSafely()
                    }
                }

                _uiState.update {
                    it.copy(
                        sweepProgress = progress,
                        currentlySweepingLabel = label
                    )
                }
                delay(92L)
            }

            val updatedCleared = _uiState.value.clearedPackagesThisSession + appContext.packageName
            val (postStorage, postApps) = withContext(Dispatchers.IO) {
                val previousSelection = _uiState.value.apps.associate { it.packageName to it.isSelectedForClean }
                val fresh = cacheManager.scanAvailableApps(updatedCleared).map { entry ->
                    entry.copy(isSelectedForClean = previousSelection[entry.packageName] ?: entry.isSelectedForClean)
                }
                Pair(cacheManager.getDeviceStorageSummary(), fresh)
            }

            val remainingMeasured = postApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
            prefsRepo.recordVerifiedClean(
                bytesFreed = ownFreedBytes,
                verifiedAppsCount = targets.size,
                totalDetectedCacheBytes = remainingMeasured
            )
            CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
            MiniTornadoWidgetProvider.refreshAllMiniWidgets(appContext)

            if (current.stats.hapticEnabled) {
                triggerCompletionHaptic()
            }

            val otherSelectedCount = postApps.count { it.isSelectedForClean && !it.isOwnApp && !it.isClearedThisSession }
            val resultBanner = buildString {
                if (ownFreedBytes > 0L) {
                    append("Swept ${formatBytes(ownFreedBytes)} directly. ")
                } else {
                    append("Local cache verified clean. ")
                }
                if (otherSelectedCount > 0) {
                    append("$otherSelectedCount apps ready for 1-tap Android Storage clean.")
                }
            }

            _uiState.update { state ->
                state.copy(
                    phase = CleanPhase.COMPLETED,
                    sweepProgress = 1f,
                    apps = postApps,
                    storageSummary = postStorage,
                    clearedPackagesThisSession = updatedCleared,
                    sessionFreedBytes = state.sessionFreedBytes + ownFreedBytes,
                    currentlySweepingLabel = "Tornado sweep verified",
                    statusMessage = resultBanner
                )
            }
        }
    }

    fun dismissVerification() {
        if (_uiState.value.phase == CleanPhase.CONFIRM_READY || _uiState.value.phase == CleanPhase.VERIFYING) {
            activeSweepJob?.cancel()
            _uiState.update { it.copy(phase = CleanPhase.IDLE, currentlySweepingLabel = "") }
        }
    }

    fun resetToIdle() {
        _uiState.update { it.copy(phase = CleanPhase.IDLE, sweepProgress = 0f, currentlySweepingLabel = "") }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val updated = state.apps.map { entry ->
                if (entry.packageName == packageName) {
                    entry.copy(isSelectedForClean = !entry.isSelectedForClean)
                } else {
                    entry
                }
            }
            state.copy(apps = updated)
        }
    }

    fun setAllAppsSelected(selected: Boolean) {
        _uiState.update { state ->
            state.copy(apps = state.apps.map { it.copy(isSelectedForClean = selected || it.isOwnApp) })
        }
    }

    fun prepareExternalAppStorageOpen(entry: AppCacheEntry) {
        pendingExternalPackage = entry.packageName
        pendingExternalBeforeBytes = entry.cacheBytes
    }

    fun setRequireVerifyBeforeWidgetClean(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setRequireVerifyBeforeWidgetClean(enabled)
            CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setHapticEnabled(enabled)
        }
    }

    fun postStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun createUsageAccessIntent() = cacheManager.createUsageAccessSettingsIntent()

    fun createAppStorageIntent(packageName: String) = cacheManager.createAppDetailsStorageIntent(packageName)

    fun createSystemStorageIntent() = cacheManager.createSystemStorageIntent()

    private fun triggerTornadoHaptic() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0L, 28L, 45L, 38L, 45L, 52L)
                val amplitudes = intArrayOf(0, 70, 0, 120, 0, 175)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50L)
            }
        } catch (_: Exception) {
            // Optional tactile feedback
        }
    }

    private fun triggerCompletionHaptic() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(45L)
            }
        } catch (_: Exception) {
            // Optional tactile feedback
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
