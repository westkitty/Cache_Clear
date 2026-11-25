package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CleanPhase
import com.example.data.formatBytes
import com.example.ui.CacheTwisterUiState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun WidgetStudioTab(
    uiState: CacheTwisterUiState,
    onPinMiniWidgetClick: () -> Unit,
    onPinFullWidgetClick: () -> Unit,
    onToggleRequireVerify: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var previewPhase by remember { mutableStateOf(CleanPhase.IDLE) }
    var previewProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(previewPhase) {
        if (previewPhase == CleanPhase.VERIFYING) {
            delay(500L)
            previewPhase = CleanPhase.TORNADO_SWEEPING
        } else if (previewPhase == CleanPhase.TORNADO_SWEEPING) {
            for (step in 1..20) {
                previewProgress = step / 20f
                delay(85L)
            }
            previewPhase = CleanPhase.COMPLETED
            delay(1600L)
            previewPhase = CleanPhase.IDLE
            previewProgress = 0f
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Home Screen Widgets",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.totalActiveWidgets > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${uiState.totalActiveWidgets} Pinned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Pick the compact little 1×1 Mini Tornado widget or the full 2×2 Cache Twister card for your Galaxy S25 home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Compact Little 1x1 Mini Tornado Widget Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Little 1x1 Mini Tornado live preview badge
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                            modifier = Modifier.size(86.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                TornadoVortexCanvas(
                                    phase = previewPhase,
                                    sweepProgress = previewProgress,
                                    storageUsedFraction = uiState.storageSummary.usedFraction,
                                    centerPrimaryLabel = "",
                                    centerSecondaryLabel = "",
                                    onStageClick = {
                                        if (previewPhase == CleanPhase.IDLE) {
                                            previewPhase = CleanPhase.VERIFYING
                                        }
                                    },
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = when (previewPhase) {
                                        CleanPhase.VERIFYING -> "Verify…"
                                        CleanPhase.TORNADO_SWEEPING -> "${(previewProgress * 100).toInt()}%"
                                        CleanPhase.COMPLETED -> "Clean"
                                        else -> "Sweep"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mini Tornado (1×1)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A compact little tornado button that sits right alongside your app icons. Tap it once on your home screen to verify safety and spin the tornado sweep.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.activeMiniWidgetCount > 0) {
                                Text(
                                    text = "${uiState.activeMiniWidgetCount} active on home screen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onPinMiniWidgetClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pin_mini_widget_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddToHomeScreen,
                            contentDescription = stringResource(R.string.cd_add_mini_widget),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Little 1×1 Tornado Widget",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2. Resizable 2x2 Storm Card Widget Preview + Pin
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    )
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .widthIn(max = 224.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (previewPhase == CleanPhase.VERIFYING) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = when (previewPhase) {
                                            CleanPhase.VERIFYING -> "VERIFY"
                                            CleanPhase.TORNADO_SWEEPING -> "TWISTER"
                                            CleanPhase.COMPLETED -> "CLEAN"
                                            else -> "SAFE"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (previewPhase == CleanPhase.VERIFYING) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = when (previewPhase) {
                                        CleanPhase.TORNADO_SWEEPING -> "${(previewProgress * 100).toInt()}%"
                                        CleanPhase.COMPLETED -> "Verified"
                                        else -> if (uiState.totalMeasuredCacheBytes > 0L) {
                                            formatBytes(uiState.totalMeasuredCacheBytes)
                                        } else {
                                            "${uiState.apps.size} apps"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TornadoVortexCanvas(
                                phase = previewPhase,
                                sweepProgress = previewProgress,
                                storageUsedFraction = uiState.storageSummary.usedFraction,
                                centerPrimaryLabel = when (previewPhase) {
                                    CleanPhase.VERIFYING -> "Check"
                                    CleanPhase.TORNADO_SWEEPING -> "${(previewProgress * 100).toInt()}%"
                                    CleanPhase.COMPLETED -> "Clean"
                                    else -> ""
                                },
                                centerSecondaryLabel = when (previewPhase) {
                                    CleanPhase.VERIFYING -> "Safety"
                                    CleanPhase.TORNADO_SWEEPING -> "Sweeping"
                                    CleanPhase.COMPLETED -> "Verified"
                                    else -> ""
                                },
                                onStageClick = {
                                    if (previewPhase == CleanPhase.IDLE) {
                                        previewPhase = CleanPhase.VERIFYING
                                    }
                                },
                                modifier = Modifier.size(126.dp)
                            )

                            Button(
                                onClick = {
                                    if (previewPhase == CleanPhase.IDLE) {
                                        previewPhase = CleanPhase.VERIFYING
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("preview_widget_tornado_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Air,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (previewPhase == CleanPhase.IDLE) "Preview Tornado" else "Running…",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onPinFullWidgetClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pin_widget_primary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddToHomeScreen,
                            contentDescription = stringResource(R.string.cd_add_widget),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add 2×2 Cache Twister Widget",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Galaxy S25 One UI Manual Placement Tip Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How to Add Manually on Galaxy S25",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Touch and hold an empty spot on your home screen → Widgets → Cache Twister, then drag either Mini Tornado (1×1) or Cache Twister (2×2) onto your screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Preferences Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pre-Clean Safety Check",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Verify safe cache scope & sandbox rules before every sweep",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.stats.requireVerifyBeforeWidgetClean,
                            onCheckedChange = onToggleRequireVerify,
                            modifier = Modifier.testTag("toggle_require_verify_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tornado Haptic Pulse",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Subtle tactile rumble as the vortex ramps up and completes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.stats.hapticEnabled,
                            onCheckedChange = onToggleHaptic,
                            modifier = Modifier.testTag("toggle_haptic_switch")
                        )
                    }
                }
            }
        }

        // Verified Clean Telemetry Card
        item {
            val lastCleanedText = if (uiState.stats.lastCleanedEpochMs > 0L) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(uiState.stats.lastCleanedEpochMs))
            } else {
                "Not swept yet"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "VERIFIED CLEARED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBytes(uiState.stats.totalBytesFreedLifetime),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "LAST VERIFIED SWEEP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastCleanedText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}
