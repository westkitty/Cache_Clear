package com.example.data

import java.util.Locale

data class AppCacheEntry(
    val packageName: String,
    val appName: String,
    val cacheBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val isSystemApp: Boolean,
    val isOwnApp: Boolean,
    val cacheMeasured: Boolean,
    val isSelectedForClean: Boolean = true,
    val isClearedThisSession: Boolean = false,
    val externalStatus: ExternalCacheStatus? = null
)

enum class SafetyCheckStatus {
    VERIFYING,
    PASSED,
    ACTION_RECOMMENDED
}

data class SafetyCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: SafetyCheckStatus
)

data class DeviceStorageSummary(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usedBytes: Long = 0L
) {
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class VerificationReport(
    val checks: List<SafetyCheckItem>,
    val verifiedAtEpochMs: Long,
    val deviceLabel: String,
    val androidVersionLabel: String,
    val totalMeasuredCacheBytes: Long,
    val ownAppCacheBytes: Long,
    val selectedAppCount: Int,
    val totalDiscoveredApps: Int,
    val hasUsageAccess: Boolean,
    val isReadyToSweep: Boolean
)

enum class CleanPhase {
    IDLE,
    VERIFYING,
    CONFIRM_READY,
    TORNADO_SWEEPING,
    COMPLETED
}

data class CleanSessionStats(
    val lastCleanedEpochMs: Long = 0L,
    val totalBytesFreedLifetime: Long = 0L,
    val lastSessionFreedBytes: Long = 0L,
    val lastVerifiedAppsCount: Int = 0,
    val hapticEnabled: Boolean = true
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        String.format(Locale.US, "%d %s", bytes, units[unitIndex])
    } else if (value >= 100.0) {
        String.format(Locale.US, "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}
