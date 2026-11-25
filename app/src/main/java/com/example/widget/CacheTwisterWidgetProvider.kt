package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.CachePreferencesRepository
import com.example.data.formatBytes

class CacheTwisterWidgetProvider : AppWidgetProvider() {
    enum class PinWidgetStatus { REQUESTED, UNSUPPORTED }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = CachePreferencesRepository(context)
        val snapshot = repo.readWidgetSnapshot()
        val displayBytes = snapshot.detectedCacheBytes
        val metricLabel = if (displayBytes > 0L) formatBytes(displayBytes) else "Open"
        val statusLabel = if (snapshot.appsCount > 0) {
            "${snapshot.appsCount} apps found"
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
                badgeText = "CACHE",
                metricText = metricLabel,
                statusText = statusLabel,
                buttonText = context.getString(R.string.widget_action_verify_sweep),
                tornadoBitmap = idleBitmap
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildBaseRemoteViews(
        context: Context,
        badgeText: String,
        metricText: String,
        statusText: String,
        buttonText: String,
        tornadoBitmap: android.graphics.Bitmap
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

        views.setOnClickPendingIntent(R.id.widget_sweep_button, openPendingIntent)

        return views
    }

    companion object {
        const val EXTRA_FROM_WIDGET_VERIFY = "extra_from_widget_verify"


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
                setTextViewText(R.id.widget_badge_text, "CACHE")
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
