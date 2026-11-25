package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppCacheEntry
import com.example.data.DeviceStorageSummary
import com.example.data.formatBytes
import com.example.ui.CacheTwisterUiState

@Composable
fun StorageFootprintDashboard(
    uiState: CacheTwisterUiState,
    onOpenAppStorage: (AppCacheEntry) -> Unit,
    onSweepOwnApp: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storage = uiState.storageSummary
    val totalCacheBytes = uiState.totalMeasuredCacheBytes
    val cacheShareOfDevice = if (storage.totalBytes > 0L) {
        (totalCacheBytes.toFloat() / storage.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_footprint_dashboard")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Dashboard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Storage & Cache Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live device footprint & largest app caches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Donut Visualization + Telemetry Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageDonutGauge(
                    storage = storage,
                    cacheFractionOfTotal = cacheShareOfDevice,
                    modifier = Modifier.size(108.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StorageMetricRow(
                        dotColor = MaterialTheme.colorScheme.primary,
                        label = "Used Storage",
                        value = formatBytes(storage.usedBytes)
                    )
                    StorageMetricRow(
                        dotColor = MaterialTheme.colorScheme.tertiary,
                        label = if (uiState.hasUsageAccess) "Measured Cache" else "Local Cache",
                        value = formatBytes(totalCacheBytes)
                    )
                    StorageMetricRow(
                        dotColor = MaterialTheme.colorScheme.secondary,
                        label = "Available Free",
                        value = formatBytes(storage.availableBytes)
                    )
                    Text(
                        text = "Total capacity: ${formatBytes(storage.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Segmented Allocation Bar
            SegmentedStorageBar(
                usedFraction = storage.usedFraction,
                cacheFraction = cacheShareOfDevice
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Largest Cache Footprints Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Largest Cache Footprints",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (uiState.apps.isNotEmpty()) {
                    TextButton(
                        onClick = onOpenAllApps,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("dashboard_view_all_apps_button")
                    ) {
                        Text(
                            text = "All ${uiState.apps.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (!uiState.hasUsageAccess) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Grant Usage Access to Rank Every App",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "Allow Usage Access in Settings so Android's StorageStatsManager can pinpoint which specific apps hold the biggest caches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )
                        Button(
                            onClick = onGrantUsageAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dashboard_grant_usage_button")
                        ) {
                            Text(
                                text = "Unlock Per-App Cache Ranking",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            val rankedApps = uiState.topCacheFootprintApps
            val maxAppCache = uiState.largestSingleAppCacheBytes.coerceAtLeast(1L)

            if (rankedApps.isEmpty()) {
                Text(
                    text = if (uiState.isScanning) {
                        "Measuring app cache footprints…"
                    } else {
                        "No measurable app cache buildup detected right now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rankedApps.forEachIndexed { index, entry ->
                        val barFraction = if (entry.cacheMeasured && entry.cacheBytes > 0L) {
                            (entry.cacheBytes.toFloat() / maxAppCache.toFloat()).coerceIn(0.06f, 1f)
                        } else {
                            0.08f
                        }
                        val cachePercent = if (entry.cacheMeasured && totalCacheBytes > 0L) {
                            ((entry.cacheBytes.toDouble() / totalCacheBytes.toDouble()) * 100.0).toInt()
                        } else {
                            0
                        }
                        TopCacheFootprintRow(
                            rank = index + 1,
                            entry = entry,
                            barFraction = barFraction,
                            sharePercent = cachePercent,
                            onClick = {
                                if (entry.isOwnApp) onSweepOwnApp() else onOpenAppStorage(entry)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageDonutGauge(
    storage: DeviceStorageSummary,
    cacheFractionOfTotal: Float,
    modifier: Modifier = Modifier
) {
    val usedAnimated by animateFloatAsState(
        targetValue = storage.usedFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "storage_used_arc"
    )
    val cacheAnimated by animateFloatAsState(
        targetValue = cacheFractionOfTotal.coerceAtMost(usedAnimated),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "storage_cache_arc"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val usedColor = MaterialTheme.colorScheme.primary
    val cacheColor = MaterialTheme.colorScheme.tertiary
    val usedPct = (storage.usedFraction * 100f).toInt().coerceIn(0, 100)
    val cdText = stringResource(R.string.cd_storage_donut_chart, usedPct)

    Box(
        modifier = modifier.semantics { contentDescription = cdText },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 11.dp.toPx()
            val inset = strokePx * 0.65f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Free space track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            // Total used arc
            val usedSweep = (usedAnimated * 360f).coerceAtLeast(if (usedAnimated > 0f) 6f else 0f)
            drawArc(
                color = usedColor,
                startAngle = -90f,
                sweepAngle = usedSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Reclaimable cache highlight slice overlaid at the end of the used arc
            if (cacheAnimated > 0.001f) {
                val cacheSweep = (cacheAnimated * 360f).coerceIn(8f, usedSweep)
                drawArc(
                    color = cacheColor,
                    startAngle = -90f + (usedSweep - cacheSweep),
                    sweepAngle = cacheSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$usedPct%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "USED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageMetricRow(
    dotColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SegmentedStorageBar(
    usedFraction: Float,
    cacheFraction: Float
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val usedColor = MaterialTheme.colorScheme.primary
    val cacheColor = MaterialTheme.colorScheme.tertiary

    val animUsed by animateFloatAsState(
        targetValue = usedFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550),
        label = "seg_used"
    )
    val animCache by animateFloatAsState(
        targetValue = cacheFraction.coerceIn(0f, animUsed),
        animationSpec = tween(durationMillis = 550),
        label = "seg_cache"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = corner
        )
        val nonCacheWidth = ((animUsed - animCache).coerceAtLeast(0f) * size.width)
        val cacheWidth = if (animCache > 0.001f) {
            (animCache * size.width).coerceAtLeast(8.dp.toPx()).coerceAtMost(size.width)
        } else {
            0f
        }
        if (nonCacheWidth > 0f) {
            drawRoundRect(
                color = usedColor,
                size = Size(nonCacheWidth + cacheWidth, size.height),
                cornerRadius = corner
            )
        }
        if (cacheWidth > 0f) {
            drawRoundRect(
                color = cacheColor,
                topLeft = Offset(nonCacheWidth.coerceAtMost(size.width - cacheWidth), 0f),
                size = Size(cacheWidth, size.height),
                cornerRadius = corner
            )
        }
    }
}

@Composable
private fun TopCacheFootprintRow(
    rank: Int,
    entry: AppCacheEntry,
    barFraction: Float,
    sharePercent: Int,
    onClick: () -> Unit
) {
    val animatedBar by animateFloatAsState(
        targetValue = barFraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "app_cache_bar_$rank"
    )

    val barColor = when {
        entry.isClearedThisSession -> MaterialTheme.colorScheme.secondary
        rank == 1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val rowCd = stringResource(R.string.cd_top_cache_app_row, entry.appName)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (entry.isClearedThisSession) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = rowCd }
            .testTag("top_cache_app_row_${entry.packageName}")
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = barColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (entry.cacheMeasured && sharePercent > 0) {
                        Text(
                            text = "$sharePercent% of measured app cache",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!entry.cacheMeasured) {
                        Text(
                            text = "Tap to open Android Storage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (entry.cacheMeasured) formatBytes(entry.cacheBytes) else "Inspect",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                    if (!entry.isOwnApp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Proportional visual footprint bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedBar)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}
