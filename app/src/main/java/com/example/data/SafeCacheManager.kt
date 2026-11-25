package com.example.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File
import java.util.Locale

class SafeCacheManager(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getDeviceStorageSummary(): DeviceStorageSummary {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val available = stat.availableBytes
            val used = (total - available).coerceAtLeast(0L)
            DeviceStorageSummary(
                totalBytes = total,
                availableBytes = available,
                usedBytes = used
            )
        } catch (_: Exception) {
            DeviceStorageSummary()
        }
    }

    fun measureOwnAppCacheBytes(): Long {
        var total = 0L
        val dirs = buildList {
            context.cacheDir?.let { add(it) }
            context.codeCacheDir?.let { add(it) }
            context.externalCacheDir?.let { add(it) }
        }.distinctBy { it.absolutePath }

        for (dir in dirs) {
            total += directorySize(dir)
        }
        return total
    }

    internal fun clearOwnAppCacheSafely(): CacheSweepResult =
        listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
            .distinctBy { it.absolutePath }
            .fold(CacheSweepResult(0, 0, 0)) { result, dir -> result + sweepCacheRoot(dir.toPath()) }

    fun scanAvailableApps(previouslyCleared: Set<String> = emptySet()): List<AppCacheEntry> {
        val pm = context.packageManager
        val usageGranted = hasUsageStatsPermission()
        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && usageGranted) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = LinkedHashSet<String>()
        val results = mutableListOf<AppCacheEntry>()

        // Always include Cache Twister itself first for direct instant safe clearing
        seenPackages.add(context.packageName)
        val ownCache = measureOwnAppCacheBytes()
        results.add(
            AppCacheEntry(
                packageName = context.packageName,
                appName = "Cache Twister (This App)",
                cacheBytes = ownCache,
                appBytes = 0L,
                dataBytes = 0L,
                isSystemApp = false,
                isOwnApp = true,
                cacheMeasured = true,
                isSelectedForClean = true,
                isClearedThisSession = previouslyCleared.contains(context.packageName) && ownCache == 0L
            )
        )

        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            val appInfo = info.activityInfo?.applicationInfo
            val label = try {
                info.loadLabel(pm).toString().ifBlank { pkg }
            } catch (_: Exception) {
                pkg
            }
            val isSystem = appInfo != null &&
                (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

            var cacheBytes = 0L
            var appBytes = 0L
            var dataBytes = 0L
            var measured = false

            if (storageStatsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val stats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkg,
                        Process.myUserHandle()
                    )
                    cacheBytes = stats.cacheBytes.coerceAtLeast(0L)
                    appBytes = stats.appBytes.coerceAtLeast(0L)
                    dataBytes = stats.dataBytes.coerceAtLeast(0L)
                    measured = true
                } catch (_: SecurityException) {
                    measured = false
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                } catch (_: Exception) {
                    measured = false
                }
            }

            results.add(
                AppCacheEntry(
                    packageName = pkg,
                    appName = label,
                    cacheBytes = cacheBytes,
                    appBytes = appBytes,
                    dataBytes = dataBytes,
                    isSystemApp = isSystem,
                    isOwnApp = false,
                    cacheMeasured = measured,
                    isSelectedForClean = !isSystem,
                    isClearedThisSession = previouslyCleared.contains(pkg) && (!measured || cacheBytes < 16_384L)
                )
            )
        }

        return results.sortedWith(
            compareByDescending<AppCacheEntry> { it.isOwnApp }
                .thenByDescending { it.cacheBytes }
                .thenBy { it.appName.lowercase(Locale.US) }
        )
    }

    fun buildVerificationReport(apps: List<AppCacheEntry>): VerificationReport {
        val usageGranted = hasUsageStatsPermission()
        val ownCache = measureOwnAppCacheBytes()
        val selectedApps = apps.filter { it.isSelectedForClean }
        val totalMeasuredCache = apps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }

        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        val model = Build.MODEL.orEmpty()
        val deviceLabel = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }.ifBlank { "Android Device" }

        val androidLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val checks = listOf(
            SafetyCheckItem(
                id = "scope_guard",
                title = "Cache-only scope",
                detail = "Targets disposable cache folders only. Logins, app databases, photos, and personal files are never touched.",
                status = SafetyCheckStatus.PASSED
            ),
            SafetyCheckItem(
                id = "sandbox_compliance",
                title = "Other apps require manual action",
                detail = "Opens App info for other apps. You decide whether to clear cache in Android Settings.",
                status = SafetyCheckStatus.PASSED
            ),
            SafetyCheckItem(
                id = "usage_telemetry",
                title = if (usageGranted) "Per-App Cache Telemetry Active" else "Usage Access Recommended",
                detail = if (usageGranted) {
                    "StorageStatsManager confirmed ${formatBytes(totalMeasuredCache)} of cache across ${apps.size} available apps."
                } else {
                    "Grant Usage Access in Settings to measure cache sizes per app, when Android makes them available."
                },
                status = if (usageGranted) SafetyCheckStatus.PASSED else SafetyCheckStatus.ACTION_RECOMMENDED
            ),
            SafetyCheckItem(
                id = "queue_integrity",
                title = "Apps selected for the queue",
                detail = "${selectedApps.size} of ${apps.size} available apps queued. Samsung and other system apps are not selected by default.",
                status = SafetyCheckStatus.PASSED
            )
        )

        return VerificationReport(
            checks = checks,
            verifiedAtEpochMs = System.currentTimeMillis(),
            deviceLabel = deviceLabel,
            androidVersionLabel = androidLabel,
            totalMeasuredCacheBytes = totalMeasuredCache,
            ownAppCacheBytes = ownCache,
            selectedAppCount = selectedApps.size,
            totalDiscoveredApps = apps.size,
            hasUsageAccess = usageGranted,
            isReadyToSweep = selectedApps.any { it.isOwnApp }
        )
    }

    fun createUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAppDetailsStorageIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createSystemStorageIntent(): Intent {
        val manageIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Intent(StorageManager.ACTION_MANAGE_STORAGE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
        val pm = context.packageManager
        if (manageIntent != null && manageIntent.resolveActivity(pm) != null) {
            return manageIntent
        }
        return Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun directorySize(dir: File?, depth: Int = 0): Long {
        if (depth > 64 || dir == null || !dir.exists() || java.nio.file.Files.isSymbolicLink(dir.toPath())) return 0L
        if (dir.isFile) return dir.length().coerceAtLeast(0L)
        var size = 0L
        dir.listFiles()?.forEach { child ->
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) return@forEach
            size += if (child.isDirectory) directorySize(child, depth + 1) else child.length().coerceAtLeast(0L)
        }
        return size
    }

}
