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
import com.example.data.CacheSelectionStore
import com.example.data.CacheSweepResult
import com.example.data.ConfirmationGate
import com.example.data.ExternalCacheStatus
import com.example.data.externalCacheStatus
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
        get() = apps.filter { entry ->
            entry.isSelectedForClean && !entry.isOwnApp &&
                (!entry.cacheMeasured || entry.cacheBytes > 0L)
        }.minByOrNull { if (it.externalStatus == null) 0 else 1 }
}

class CacheTwisterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val cacheManager = SafeCacheManager(appContext)
    private val prefsRepo = CachePreferencesRepository(appContext)
    private val selectionStore = CacheSelectionStore(appContext)
    private var approved = selectionStore.read()

    private val _uiState = MutableStateFlow(CacheTwisterUiState())
    val uiState: StateFlow<CacheTwisterUiState> = _uiState.asStateFlow()

    private var activeSweepJob: Job? = null
    private var pendingExternalPackage: String? = null
    private var pendingExternalBeforeBytes: Long? = null
    private val externalStatuses = mutableMapOf<String, ExternalCacheStatus>()
    private val confirmation = ConfirmationGate<VerificationReport>()

    private fun CacheTwisterUiState.selection() = selectedApps.map { it.packageName }.toSet()

    private fun withApproval(entries: List<AppCacheEntry>) = entries.map {
        it.copy(isSelectedForClean = it.packageName in approved, externalStatus = externalStatuses[it.packageName])
    }

    private fun invalidateReview() {
        confirmation.invalidate()
        if (_uiState.value.phase == CleanPhase.CONFIRM_READY || _uiState.value.phase == CleanPhase.VERIFYING) {
            activeSweepJob?.cancel()
            _uiState.update { it.copy(phase = CleanPhase.IDLE, verificationReport = null) }
        }
    }

    init {
        viewModelScope.launch {
            prefsRepo.statsFlow.collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
        refreshScan()
    }

    fun refreshScan() {
        invalidateReview()
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val clearedSet = _uiState.value.clearedPackagesThisSession

            val (storage, usageGranted, scannedApps) = withContext(Dispatchers.IO) {
                val storageSummary = cacheManager.getDeviceStorageSummary()
                val usage = cacheManager.hasUsageStatsPermission()
                Triple(storageSummary, usage, cacheManager.scanAvailableApps(clearedSet))
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
                current.copy(
                    isScanning = false,
                    apps = withApproval(scannedApps),
                    storageSummary = storage,
                    hasUsageAccess = usageGranted,
                    activeMiniWidgetCount = miniCount,
                    activeFullWidgetCount = fullCount,
                )
            }
        }
    }

    fun onAppResumed() {
        invalidateReview()
        val checkedPkg = pendingExternalPackage
        val beforeBytes = pendingExternalBeforeBytes
        pendingExternalPackage = null
        pendingExternalBeforeBytes = null

        viewModelScope.launch {
            val (storage, usageGranted, scannedApps) = withContext(Dispatchers.IO) {
                Triple(
                    cacheManager.getDeviceStorageSummary(),
                    cacheManager.hasUsageStatsPermission(),
                    cacheManager.scanAvailableApps(_uiState.value.clearedPackagesThisSession)
                )
            }

            var banner: String? = null
            if (checkedPkg != null) {
                val refreshedEntry = scannedApps.firstOrNull { it.packageName == checkedPkg }
                if (refreshedEntry != null) {
                    val after = refreshedEntry.cacheBytes.takeIf { refreshedEntry.cacheMeasured }
                    val status = externalCacheStatus(beforeBytes, after)
                    externalStatuses[checkedPkg] = status
                    banner = if (status == ExternalCacheStatus.DECREASED && beforeBytes != null && after != null) {
                        "${refreshedEntry.appName}: cache decreased by ${formatBytes(beforeBytes - after)}; ${formatBytes(after)} remains"
                    } else "${refreshedEntry.appName}: ${status.label}"
                }
            }
            val mergedApps = withApproval(scannedApps)
            val totalMeasured = mergedApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
            prefsRepo.updateDetectedCacheSnapshot(totalMeasured, mergedApps.size)
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
                    statusMessage = banner ?: current.statusMessage
                )
            }
        }
    }

    fun startPreCleanVerification() {
        if (_uiState.value.phase == CleanPhase.TORNADO_SWEEPING ||
            _uiState.value.phase == CleanPhase.VERIFYING
        ) return

        invalidateReview()
        val reviewedSelection = _uiState.value.selection()
        if (appContext.packageName !in reviewedSelection) {
            postStatusMessage("Select Cache Twister to review its own cache cleanup.")
            return
        }
        activeSweepJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = CleanPhase.VERIFYING,
                    currentlySweepingLabel = "Reading current cache scope…",
                    statusMessage = null
                )
            }

            val (storage, usageGranted, rawApps) = withContext(Dispatchers.IO) {
                val fresh = cacheManager.scanAvailableApps(_uiState.value.clearedPackagesThisSession)
                Triple(cacheManager.getDeviceStorageSummary(), cacheManager.hasUsageStatsPermission(), fresh)
            }
            val scannedApps = withApproval(rawApps)

            // Brief deliberate verification pause so user observes the safety check sequence
            delay(750L)

            val report = withContext(Dispatchers.IO) {
                cacheManager.buildVerificationReport(scannedApps)
            }

            if (_uiState.value.phase != CleanPhase.VERIFYING ||
                _uiState.value.selection() != reviewedSelection ||
                scannedApps.filter { it.isSelectedForClean }.map { it.packageName }.toSet() != reviewedSelection
            ) {
                invalidateReview()
                postStatusMessage("The app list changed. Review the current selection again.")
                return@launch
            }
            confirmation.arm(report, reviewedSelection)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    apps = scannedApps,
                    storageSummary = storage,
                    hasUsageAccess = usageGranted,
                    verificationReport = report,
                    phase = CleanPhase.CONFIRM_READY,
                    currentlySweepingLabel = "Review scope before clearing"
                )
            }
        }
    }

    fun confirmAndStartTornadoClean(report: VerificationReport) {
        val current = _uiState.value
        if (current.phase != CleanPhase.CONFIRM_READY || current.verificationReport !== report) return
        if (!confirmation.consume(report, current.selection(), appContext.packageName)) {
            invalidateReview()
            return
        }
        _uiState.update {
            it.copy(
                phase = CleanPhase.TORNADO_SWEEPING, verificationReport = null, sweepProgress = 0.04f,
                currentlySweepingLabel = "Clearing Cache Twister’s own cache…", statusMessage = null
            )
        }
        activeSweepJob = viewModelScope.launch {
            if (current.stats.hapticEnabled) {
                triggerTornadoHaptic()
            }

            var result = CacheSweepResult(0, 0, 0)
            val totalSteps = 20
            for (step in 1..totalSteps) {
                if (step == 8) {
                    result = withContext(Dispatchers.IO) { cacheManager.clearOwnAppCacheSafely() }
                }
                _uiState.update { it.copy(sweepProgress = step.toFloat() / totalSteps) }
                delay(55L)
            }
            val ownFreedBytes = result.deletedFileBytes
            val updatedCleared = if (result.failed == 0 && result.skipped == 0) setOf(appContext.packageName) else emptySet()
            val (postStorage, postApps) = withContext(Dispatchers.IO) {
                    val fresh = cacheManager.scanAvailableApps(updatedCleared)
                Pair(cacheManager.getDeviceStorageSummary(), fresh)
            }

            val remainingMeasured = postApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
            if (ownFreedBytes > 0L) {
                prefsRepo.recordVerifiedClean(ownFreedBytes, 1, remainingMeasured)
            }
            prefsRepo.updateDetectedCacheSnapshot(remainingMeasured, postApps.size)
            CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
            MiniTornadoWidgetProvider.refreshAllMiniWidgets(appContext)

            if (current.stats.hapticEnabled) {
                triggerCompletionHaptic()
            }

            val resultBanner = buildString {
                append("Twister’s own cache: ${formatBytes(ownFreedBytes)} deleted.")
                if (result.skipped > 0 || result.failed > 0) {
                    append(" ${result.skipped} skipped, ${result.failed} failed.")
                }
                val remainingOwn = postApps.firstOrNull { it.isOwnApp }?.cacheBytes
                if (remainingOwn != null && remainingOwn > 0L) append(" ${formatBytes(remainingOwn)} remains in Twister.")
                append(" Other apps were not cleared.")
            }

            _uiState.update { state ->
                state.copy(
                    phase = CleanPhase.COMPLETED,
                    sweepProgress = 1f,
                    apps = withApproval(postApps),
                    storageSummary = postStorage,
                    clearedPackagesThisSession = updatedCleared,
                    sessionFreedBytes = state.sessionFreedBytes + ownFreedBytes,
                    currentlySweepingLabel = "Own-cache sweep finished",
                    statusMessage = resultBanner
                )
            }
        }
    }

    fun dismissVerification() {
        confirmation.invalidate()
        if (_uiState.value.phase == CleanPhase.CONFIRM_READY || _uiState.value.phase == CleanPhase.VERIFYING) {
            activeSweepJob?.cancel()
            _uiState.update { it.copy(phase = CleanPhase.IDLE, verificationReport = null, currentlySweepingLabel = "") }
        }
    }

    fun resetToIdle() {
        if (_uiState.value.phase == CleanPhase.TORNADO_SWEEPING) return
        invalidateReview()
        _uiState.update { it.copy(phase = CleanPhase.IDLE, sweepProgress = 0f, currentlySweepingLabel = "") }
    }

    fun toggleAppSelection(packageName: String) {
        if (_uiState.value.apps.none { it.packageName == packageName }) return
        saveSelection(if (packageName in approved) approved - packageName else approved + packageName)
    }

    fun setAllAppsSelected(selected: Boolean) {
        saveSelection(if (selected) approved + _uiState.value.apps
            .filterNot { it.isSystemApp }.map { it.packageName } else emptySet())
    }

    private fun saveSelection(next: Set<String>) {
        invalidateReview()
        approved = next
        selectionStore.save(next)
        _uiState.update { it.copy(apps = withApproval(it.apps)) }
    }

    fun prepareExternalAppStorageOpen(entry: AppCacheEntry) {
        pendingExternalPackage = entry.packageName
        pendingExternalBeforeBytes = entry.cacheBytes.takeIf { entry.cacheMeasured }
    }

    fun cancelExternalAppStorageOpen() {
        pendingExternalPackage = null
        pendingExternalBeforeBytes = null
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
