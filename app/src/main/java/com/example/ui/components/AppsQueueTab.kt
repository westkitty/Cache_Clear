package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppCacheEntry
import com.example.data.formatBytes
import com.example.ui.CacheTwisterUiState

@Composable
fun AppsQueueTab(
    uiState: CacheTwisterUiState,
    onRefreshScan: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onOpenAppStorage: (AppCacheEntry) -> Unit,
    onOpenSystemStorage: () -> Unit,
    onVerifyAndSweep: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Available Apps",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.selectedApps.size} of ${uiState.apps.size} selected · " +
                            if (uiState.hasUsageAccess) {
                                "${formatBytes(uiState.totalMeasuredCacheBytes)} cache measured"
                            } else {
                                "Grant Usage Access for exact MB"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshScan,
                    modifier = Modifier.testTag("apps_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_scan),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (uiState.isScanning) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!uiState.hasUsageAccess) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Unlock Exact Per-App Cache Sizes",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "Android requires Usage Access so Cache Twister can read real cache sizes via StorageStatsManager and confirm 0 B after you clear each app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )
                        Button(
                            onClick = onGrantUsageAccess,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("apps_grant_usage_button")
                        ) {
                            Text("Enable Usage Access")
                        }
                    }
                }
            }
        }

        // Action row: Select All / Open System Storage / Verify & Sweep
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allSelected = uiState.apps.isNotEmpty() && uiState.selectedApps.size == uiState.apps.size
                OutlinedButton(
                    onClick = { onSelectAll(!allSelected) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_select_all_button")
                ) {
                    Text(
                        text = if (allSelected) "Keep Own Only" else "Select All",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onOpenSystemStorage,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_system_storage_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OS Storage",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (uiState.apps.isEmpty() && !uiState.isScanning) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No launchable apps detected yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap refresh to re-scan available applications on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.apps,
                key = { it.packageName }
            ) { app ->
                AppCacheRowCard(
                    entry = app,
                    onToggleSelect = { onToggleAppSelection(app.packageName) },
                    onOpenStorage = {
                        if (app.isOwnApp) onVerifyAndSweep() else onOpenAppStorage(app)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun AppCacheRowCard(
    entry: AppCacheEntry,
    onToggleSelect: () -> Unit,
    onOpenStorage: () -> Unit
) {
    val toggleCd = stringResource(R.string.cd_toggle_app_selection)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (entry.isClearedThisSession) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = entry.isSelectedForClean,
                onCheckedChange = { onToggleSelect() },
                enabled = !entry.isOwnApp,
                modifier = Modifier
                    .semantics { contentDescription = toggleCd }
                    .testTag("app_checkbox_${entry.packageName}")
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.isClearedThisSession) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified cleared",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cacheBadge = when {
                        entry.isOwnApp -> "Direct: ${formatBytes(entry.cacheBytes)}"
                        entry.cacheMeasured -> "Cache: ${formatBytes(entry.cacheBytes)}"
                        else -> "Tap Storage to inspect"
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (entry.isClearedThisSession) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = if (entry.isClearedThisSession) "Verified clean" else cacheBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.isClearedThisSession) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (entry.isSystemApp) {
                        Text(
                            text = "System",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onOpenStorage,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("app_storage_btn_${entry.packageName}")
            ) {
                Text(
                    text = if (entry.isOwnApp) "Sweep" else "Storage",
                    style = MaterialTheme.typography.labelMedium
                )
                if (!entry.isOwnApp) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.cd_open_app_storage),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
