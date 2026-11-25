package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

class MiniTornadoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = CachePreferencesRepository(context)
        val snapshot = repo.readWidgetSnapshot()
        val label = if (snapshot.detectedCacheBytes > 0L) {
            formatBytes(snapshot.detectedCacheBytes)
        } else {
            context.getString(R.string.mini_widget_idle_label)
        }

        val idleBitmap = TornadoBitmapRenderer.renderFrame(
            progress = 0.22f,
            spinRadians = 0.7f,
            intensity = 0.24f,
            isVerifying = false,
            isCompleted = false
        )

        for (widgetId in appWidgetIds) {
            val views = buildMiniRemoteViews(
                context = context,
                labelText = label,
                tornadoBitmap = idleBitmap,
                openAppOnTap = false
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MINI_WIDGET_SWEEP -> handleMiniTornadoSweep(context)
            ACTION_MINI_WIDGET_REFRESH -> refreshAllMiniWidgets(context)
        }
    }

    private fun handleMiniTornadoSweep(context: Context) {
        if (!isSweeping.compareAndSet(false, true)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, MiniTornadoWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(component)

        if (widgetIds.isEmpty()) {
            isSweeping.set(false)
            pendingResult.finish()
            return
        }

        miniWidgetScope.launch {
            try {
                val cacheManager = SafeCacheManager(appContext)
                val repo = CachePreferencesRepository(appContext)
                val prefs = repo.readWidgetSnapshot()

                // Phase 1: Safety verification check
                for (step in 0 until 4) {
                    val bmp = TornadoBitmapRenderer.renderFrame(
                        progress = (step + 1) / 4f,
                        spinRadians = step * 1.15f,
                        intensity = 0.5f,
                        isVerifying = true,
                        isCompleted = false
                    )
                    updateMiniWidgets(appContext, appWidgetManager, widgetIds, "Verify…", bmp, false)
                    delay(115L)
                }

                val discoveredApps = cacheManager.scanAvailableApps()
                val report = cacheManager.buildVerificationReport(discoveredApps)
                if (prefs.hapticEnabled) {
                    pulseHaptic(appContext)
                }

                // Phase 2: Swirling little tornado animation right on home screen
                var freedOwnBytes = 0L
                val totalFrames = 14
                for (frame in 0 until totalFrames) {
                    val progress = (frame + 1).toFloat() / totalFrames.toFloat()
                    if (frame == 6) {
                        freedOwnBytes = cacheManager.clearOwnAppCacheSafely()
                    }
                    val bmp = TornadoBitmapRenderer.renderFrame(
                        progress = progress,
                        spinRadians = frame * 1.05f,
                        intensity = 0.7f + 0.3f * kotlin.math.sin(progress * kotlin.math.PI.toFloat()),
                        isVerifying = false,
                        isCompleted = false
                    )
                    updateMiniWidgets(
                        appContext,
                        appWidgetManager,
                        widgetIds,
                        "${(progress * 100).toInt()}%",
                        bmp,
                        false
                    )
                    delay(100L)
                }

                val postApps = cacheManager.scanAvailableApps(setOf(appContext.packageName))
                val remainingMeasured = postApps.filter { it.cacheMeasured }.sumOf { it.cacheBytes }
                repo.recordVerifiedClean(
                    bytesFreed = freedOwnBytes,
                    verifiedAppsCount = report.selectedAppCount,
                    totalDetectedCacheBytes = remainingMeasured
                )

                // Phase 3: Verified clean burst (tapping during burst opens full app queue)
                val doneBmp = TornadoBitmapRenderer.renderFrame(
                    progress = 1f,
                    spinRadians = 0f,
                    intensity = 0f,
                    isVerifying = false,
                    isCompleted = true
                )
                val doneLabel = if (freedOwnBytes > 0L) "-${formatBytes(freedOwnBytes)}" else "Clean"
                updateMiniWidgets(appContext, appWidgetManager, widgetIds, doneLabel, doneBmp, true)

                delay(3000L)

                refreshAllMiniWidgets(appContext)
                CacheTwisterWidgetProvider.refreshAllWidgets(appContext)
            } finally {
                isSweeping.set(false)
                pendingResult.finish()
            }
        }
    }

    private fun updateMiniWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        label: String,
        bitmap: android.graphics.Bitmap,
        openAppOnTap: Boolean
    ) {
        for (id in ids) {
            val views = buildMiniRemoteViews(context, label, bitmap, openAppOnTap)
            manager.updateAppWidget(id, views)
        }
    }

    private fun pulseHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35L)
                }
            }
        } catch (_: Exception) {
            // Haptics are optional
        }
    }

    companion object {
        const val ACTION_MINI_WIDGET_SWEEP = "com.example.action.MINI_WIDGET_SWEEP"
        const val ACTION_MINI_WIDGET_REFRESH = "com.example.action.MINI_WIDGET_REFRESH"

        private val isSweeping = AtomicBoolean(false)
        private val miniWidgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun buildMiniRemoteViews(
            context: Context,
            labelText: String,
            tornadoBitmap: android.graphics.Bitmap,
            openAppOnTap: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_mini_tornado)
            views.setTextViewText(R.id.mini_widget_label, labelText)
            views.setImageViewBitmap(R.id.mini_widget_tornado_image, tornadoBitmap)

            val clickPendingIntent = if (openAppOnTap) {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY, true)
                }
                PendingIntent.getActivity(
                    context,
                    201,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                val sweepIntent = Intent(context, MiniTornadoWidgetProvider::class.java).apply {
                    action = ACTION_MINI_WIDGET_SWEEP
                }
                PendingIntent.getBroadcast(
                    context,
                    202,
                    sweepIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            views.setOnClickPendingIntent(R.id.mini_widget_root, clickPendingIntent)
            views.setOnClickPendingIntent(R.id.mini_widget_tornado_image, clickPendingIntent)
            return views
        }

        fun getActiveWidgetCount(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MiniTornadoWidgetProvider::class.java)
            return manager.getAppWidgetIds(component).size
        }

        fun refreshAllMiniWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MiniTornadoWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val updateIntent = Intent(context, MiniTornadoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
        }

        fun requestPinToHomeScreen(context: Context): CacheTwisterWidgetProvider.PinWidgetStatus {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return CacheTwisterWidgetProvider.PinWidgetStatus.UNSUPPORTED
            }
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) {
                return CacheTwisterWidgetProvider.PinWidgetStatus.UNSUPPORTED
            }
            val provider = ComponentName(context, MiniTornadoWidgetProvider::class.java)
            val previewBmp = TornadoBitmapRenderer.renderFrame(
                progress = 0.35f,
                spinRadians = 1.0f,
                intensity = 0.55f
            )
            val previewViews = buildMiniRemoteViews(
                context = context,
                labelText = context.getString(R.string.mini_widget_idle_label),
                tornadoBitmap = previewBmp,
                openAppOnTap = false
            )
            val extras = Bundle().apply {
                putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
            }
            val pinned = manager.requestPinAppWidget(provider, extras, null)
            return if (pinned) {
                CacheTwisterWidgetProvider.PinWidgetStatus.REQUESTED
            } else {
                CacheTwisterWidgetProvider.PinWidgetStatus.UNSUPPORTED
            }
        }
    }
}
