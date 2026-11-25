package com.example.data

import android.content.Context

internal class CacheSelectionStore(context: Context) {
    private val ownPackage = context.packageName
    private val preferences = context.getSharedPreferences("cache_twister_selection", Context.MODE_PRIVATE)

    // A stored empty set is different from a first run. Copy to avoid aliasing SharedPreferences.
    fun read(): Set<String> = preferences.getStringSet("approved_packages", null)?.toSet() ?: setOf(ownPackage)

    fun save(packages: Set<String>) {
        preferences.edit().putStringSet("approved_packages", packages.toSet()).apply()
    }
}
