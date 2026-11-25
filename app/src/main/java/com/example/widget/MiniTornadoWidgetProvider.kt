package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.CachePreferencesRepository
import com.example.data.formatBytes

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
                tornadoBitmap = idleBitmap
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {


        fun buildMiniRemoteViews(
            context: Context,
            labelText: String,
            tornadoBitmap: android.graphics.Bitmap
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_mini_tornado)
            views.setTextViewText(R.id.mini_widget_label, labelText)
            views.setImageViewBitmap(R.id.mini_widget_tornado_image, tornadoBitmap)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY, true)
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context, 201, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
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
                tornadoBitmap = previewBmp
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
