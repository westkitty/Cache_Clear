package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cacheTwisterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cache_twister_prefs"
)

class CachePreferencesRepository(private val context: Context) {

    private val widgetMirrorPrefs =
        context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)

    val statsFlow: Flow<CleanSessionStats> = context.cacheTwisterDataStore.data.map { prefs ->
        CleanSessionStats(
            lastCleanedEpochMs = prefs[KEY_LAST_CLEANED_MS] ?: 0L,
            totalBytesFreedLifetime = prefs[KEY_LIFETIME_FREED] ?: 0L,
            lastSessionFreedBytes = prefs[KEY_LAST_SESSION_FREED] ?: 0L,
            lastVerifiedAppsCount = prefs[KEY_LAST_APPS_COUNT] ?: 0,
            requireVerifyBeforeWidgetClean = prefs[KEY_REQUIRE_VERIFY] ?: true,
            hapticEnabled = prefs[KEY_HAPTIC_ENABLED] ?: true
        )
    }

    suspend fun recordVerifiedClean(
        bytesFreed: Long,
        verifiedAppsCount: Int,
        totalDetectedCacheBytes: Long
    ) {
        val now = System.currentTimeMillis()
        context.cacheTwisterDataStore.edit { prefs ->
            val previousLifetime = prefs[KEY_LIFETIME_FREED] ?: 0L
            prefs[KEY_LAST_CLEANED_MS] = now
            prefs[KEY_LAST_SESSION_FREED] = bytesFreed.coerceAtLeast(0L)
            prefs[KEY_LIFETIME_FREED] = previousLifetime + bytesFreed.coerceAtLeast(0L)
            prefs[KEY_LAST_APPS_COUNT] = verifiedAppsCount
        }
        widgetMirrorPrefs.edit()
            .putLong(MIRROR_LAST_CLEANED_MS, now)
            .putLong(MIRROR_LAST_FREED_BYTES, bytesFreed.coerceAtLeast(0L))
            .putLong(MIRROR_DETECTED_CACHE_BYTES, totalDetectedCacheBytes.coerceAtLeast(0L))
            .putInt(MIRROR_APPS_COUNT, verifiedAppsCount)
            .apply()
    }

    suspend fun updateDetectedCacheSnapshot(
        totalDetectedCacheBytes: Long,
        appsCount: Int
    ) {
        widgetMirrorPrefs.edit()
            .putLong(MIRROR_DETECTED_CACHE_BYTES, totalDetectedCacheBytes.coerceAtLeast(0L))
            .putInt(MIRROR_APPS_COUNT, appsCount)
            .apply()
    }

    suspend fun setRequireVerifyBeforeWidgetClean(enabled: Boolean) {
        context.cacheTwisterDataStore.edit { prefs ->
            prefs[KEY_REQUIRE_VERIFY] = enabled
        }
        widgetMirrorPrefs.edit().putBoolean(MIRROR_REQUIRE_VERIFY, enabled).apply()
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.cacheTwisterDataStore.edit { prefs ->
            prefs[KEY_HAPTIC_ENABLED] = enabled
        }
        widgetMirrorPrefs.edit().putBoolean(MIRROR_HAPTIC_ENABLED, enabled).apply()
    }

    fun readWidgetSnapshot(): WidgetSyncSnapshot {
        return WidgetSyncSnapshot(
            lastCleanedEpochMs = widgetMirrorPrefs.getLong(MIRROR_LAST_CLEANED_MS, 0L),
            lastFreedBytes = widgetMirrorPrefs.getLong(MIRROR_LAST_FREED_BYTES, 0L),
            detectedCacheBytes = widgetMirrorPrefs.getLong(MIRROR_DETECTED_CACHE_BYTES, 0L),
            appsCount = widgetMirrorPrefs.getInt(MIRROR_APPS_COUNT, 0),
            requireVerify = widgetMirrorPrefs.getBoolean(MIRROR_REQUIRE_VERIFY, true),
            hapticEnabled = widgetMirrorPrefs.getBoolean(MIRROR_HAPTIC_ENABLED, true)
        )
    }

    data class WidgetSyncSnapshot(
        val lastCleanedEpochMs: Long,
        val lastFreedBytes: Long,
        val detectedCacheBytes: Long,
        val appsCount: Int,
        val requireVerify: Boolean,
        val hapticEnabled: Boolean
    )

    companion object {
        private const val WIDGET_PREFS_NAME = "cache_twister_widget_mirror"
        private const val MIRROR_LAST_CLEANED_MS = "last_cleaned_ms"
        private const val MIRROR_LAST_FREED_BYTES = "last_freed_bytes"
        private const val MIRROR_DETECTED_CACHE_BYTES = "detected_cache_bytes"
        private const val MIRROR_APPS_COUNT = "apps_count"
        private const val MIRROR_REQUIRE_VERIFY = "require_verify"
        private const val MIRROR_HAPTIC_ENABLED = "haptic_enabled"

        private val KEY_LAST_CLEANED_MS = longPreferencesKey("last_cleaned_epoch_ms")
        private val KEY_LIFETIME_FREED = longPreferencesKey("lifetime_freed_bytes")
        private val KEY_LAST_SESSION_FREED = longPreferencesKey("last_session_freed_bytes")
        private val KEY_LAST_APPS_COUNT = intPreferencesKey("last_verified_apps_count")
        private val KEY_REQUIRE_VERIFY = booleanPreferencesKey("require_verify_before_widget_clean")
        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    }
}
