package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.CachePreferencesRepository
import com.example.data.SafeCacheManager
import com.example.data.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CacheTwisterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = CachePreferencesRepository(context)
        val cacheManager = SafeCacheManager(context)
        val snapshot = repo.readWidgetSnapshot()
        val ownCache = cacheManager.measureOwnAppCacheBytes()
        val displayBytes = if (snapshot.detectedCacheBytes > 0L) snapshot.detectedCacheBytes else ownCache
        val metricLabel = if (displayBytes > 0L) formatBytes(displayBytes) else "Safe"
        val statusLabel = if (snapshot.appsCount > 0) {
            "${snapshot.appsCount} apps verified"
        } else {
            context.getString(R.string.widget_status_ready)
        }

        val idleBitmap = TornadoBitmapRenderer.renderFrame(
            progress = 0.18f,
            spinRadians = 0.6f,
            intensity = 0.2f,
            isVerifying = false,
            isCompleted = false
        )

        for (widgetId in appWidgetIds) {
            val views = buildBaseRemoteViews(
                context = context,
                badgeText = "SAFE",
                metricText = metricLabel,
                statusText = statusLabel,
                buttonText = context.getString(R.string.widget_action_verify_sweep),
                tornadoBitmap = idleBitmap
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TORNADO_SWEEP -> handleWidgetTornadoSweep(context)
            ACTION_WIDGET_REFRESH -> refreshAllWidgets(context)
        }
    }

    private fun handleWidgetTornadoSweep(context: Context) {
        if (!isSweeping.compareAndSet(false, true)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, CacheTwisterWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(component)

        if (widgetIds.isEmpty()) {
            isSweeping.set(false)
            pendingResult.finish()
            return
        }

        widgetScope.launch {
            try {
                val cacheManager = SafeCacheManager(appContext)
                val repo = CachePreferencesRepository(appContext)
                val prefs = repo.readWidgetSnapshot()

                // Phase 1: Live pre-clean safety verification directly in widget
                for (step in 0 until 4) {
                    val bmp = TornadoBitmapRenderer.renderFrame(
                        progress = (step + 1) / 4f,
                        spinRadians = step * 1.1f,
                        intensity = 0.45f,
                        isVerifying = true,
                        isCompleted = false
                    )
                    updateWidgetsWithFrame(
                        context = appContext,
                        manager = appWidgetManager,
                        ids = widgetIds,
                        badge = "VERIFY",
                        metric = "${(step + 1) * 25}%",
                        status = appContext.getString(R.string.widget_status_verifying),
                        buttonText = "Checking…",
                        bitmap = bmp
                    )
                    delay(120L)
                }

                // Scan real apps & build safety verification report
                val discoveredApps = cacheManager.scanAvailableApps()
                val report = cacheManager.buildVerificationReport(discoveredApps)
                if (prefs.hapticEnabled) {
                    pulseHaptic(appContext)
                }

                // Phase 2: Swirling mini-tornado cleaning sequence right on home screen
                var freedOwnBytes = 0L
                val totalFrames = 14
                for (frame in 0 until totalFrames) {
                    val progress = (frame + 1).toFloat() / totalFrames.toFloat()
                    if (frame == 5) {
                        freedOwnBytes = cacheManager.clearOwnAppCacheSafely()
                    }
                    val bmp = TornadoBitmapRenderer.renderFrame(
                        progress = progress,
                        spinRadians = frame * 0.95f,
                        intensity = 0.65f + 0.35f * kotlin.math.sin(progress * kotlin.math.PI.toFloat()),
                        isVerifying = false,
                        isCompleted = false
                    )
                    val sweepPercent = (progress * 100).toInt()
                    updateWidgetsWithFrame(
                        context = appContext,
                        manager = appWidgetManager,
                        ids = widgetIds,
                        badge = "TWISTER",
                        metric = "$sweepPercent%",
                        status = "Sweeping ${report.selectedAppCount} apps…",
                        buttonText = "Sweeping…",
                        bitmap = bmp
                    )
                    delay(105L)
                }

                // Post-clean re-measurement
                val postApps = cacheManager.scanAvailableApps(setOf(appContext.packageName))
                val remainingMeasured = postApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
                repo.recordVerifiedClean(
                    bytesFreed = freedOwnBytes,
                    verifiedAppsCount = report.selectedAppCount,
                    totalDetectedCacheBytes = remainingMeasured
                )

                // Phase 3: Verified completion burst
                val doneBmp = TornadoBitmapRenderer.renderFrame(
                    progress = 1f,
                    spinRadians = 0f,
                    intensity = 0f,
                    isVerifying = false,
                    isCompleted = true
                )
                val summaryMetric = if (freedOwnBytes > 0L) {
                    "-${formatBytes(freedOwnBytes)}"
                } else if (remainingMeasured > 0L) {
                    formatBytes(remainingMeasured)
                } else {
                    "0 B"
                }
                val summaryStatus = if (postApps.size > 1) {
                    "Verified · Tap for ${postApps.size - 1} apps"
                } else {
                    appContext.getString(R.string.widget_status_done)
                }

                updateWidgetsWithFrame(
                    context = appContext,
                    manager = appWidgetManager,
                    ids = widgetIds,
                    badge = "CLEAN",
                    metric = summaryMetric,
                    status = summaryStatus,
                    buttonText = "Open Queue",
                    bitmap = doneBmp,
                    sweepOpensApp = true
                )

                delay(3200L)

                // Settle back into crisp ready state
                refreshAllWidgets(appContext)
            } finally {
                isSweeping.set(false)
                pendingResult.finish()
            }
        }
    }

    private fun updateWidgetsWithFrame(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        badge: String,
        metric: String,
        status: String,
        buttonText: String,
        bitmap: android.graphics.Bitmap,
        sweepOpensApp: Boolean = false
    ) {
        for (id in ids) {
            val views = buildBaseRemoteViews(
                context = context,
                badgeText = badge,
                metricText = metric,
                statusText = status,
                buttonText = buttonText,
                tornadoBitmap = bitmap,
                sweepOpensApp = sweepOpensApp
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun buildBaseRemoteViews(
        context: Context,
        badgeText: String,
        metricText: String,
        statusText: String,
        buttonText: String,
        tornadoBitmap: android.graphics.Bitmap,
        sweepOpensApp: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cache_twister)
        views.setTextViewText(R.id.widget_badge_text, badgeText)
        views.setTextViewText(R.id.widget_metric_text, metricText)
        views.setTextViewText(R.id.widget_status_text, statusText)
        views.setTextViewText(R.id.widget_sweep_button, buttonText)
        views.setImageViewBitmap(R.id.widget_tornado_image, tornadoBitmap)

        // Tapping top header or tornado opens app to full Pre-Clean Verifier & App Queue
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_WIDGET_VERIFY, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_tornado_image, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_badge_text, openPendingIntent)

        // Tapping primary pill launches live tornado verification & sweep on the widget itself
        val buttonPendingIntent = if (sweepOpensApp) {
            openPendingIntent
        } else {
            val sweepIntent = Intent(context, CacheTwisterWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TORNADO_SWEEP
            }
            PendingIntent.getBroadcast(
                context,
                102,
                sweepIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        views.setOnClickPendingIntent(R.id.widget_sweep_button, buttonPendingIntent)

        return views
    }

    private fun pulseHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40L)
                }
            }
        } catch (_: Exception) {
            // Haptics are strictly optional
        }
    }

    enum class PinWidgetStatus {
        REQUESTED,
        UNSUPPORTED
    }

    companion object {
        const val ACTION_WIDGET_TORNADO_SWEEP = "com.example.action.WIDGET_TORNADO_SWEEP"
        const val ACTION_WIDGET_REFRESH = "com.example.action.WIDGET_REFRESH"
        const val EXTRA_FROM_WIDGET_VERIFY = "extra_from_widget_verify"

        private val isSweeping = AtomicBoolean(false)
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun getActiveWidgetCount(context: Context): Int {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CacheTwisterWidgetProvider::class.java)
            return appWidgetManager.getAppWidgetIds(component).size
        }

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CacheTwisterWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val updateIntent = Intent(context, CacheTwisterWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
        }

        fun requestPinToHomeScreen(context: Context): PinWidgetStatus {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return PinWidgetStatus.UNSUPPORTED
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (!appWidgetManager.isRequestPinAppWidgetSupported) {
                return PinWidgetStatus.UNSUPPORTED
            }
            val provider = ComponentName(context, CacheTwisterWidgetProvider::class.java)
            val previewBmp = TornadoBitmapRenderer.renderFrame(
                progress = 0.32f,
                spinRadians = 0.9f,
                intensity = 0.5f
            )
            val previewViews = RemoteViews(context.packageName, R.layout.widget_cache_twister).apply {
                setTextViewText(R.id.widget_badge_text, "SAFE")
                setTextViewText(R.id.widget_metric_text, "Ready")
                setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_status_ready))
                setTextViewText(R.id.widget_sweep_button, context.getString(R.string.widget_action_verify_sweep))
                setImageViewBitmap(R.id.widget_tornado_image, previewBmp)
            }
            val extras = android.os.Bundle().apply {
                putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
            }
            val pinned = appWidgetManager.requestPinAppWidget(provider, extras, null)
            return if (pinned) PinWidgetStatus.REQUESTED else PinWidgetStatus.UNSUPPORTED
        }
    }
}
