package com.example.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppCacheEntry
import com.example.data.CleanPhase
import com.example.data.formatBytes
import com.example.ui.components.AppsQueueTab
import com.example.ui.components.PreCleanVerificationSheet
import com.example.ui.components.StorageFootprintDashboard
import com.example.ui.components.TornadoVortexCanvas
import com.example.ui.components.WidgetStudioTab
import com.example.widget.CacheTwisterWidgetProvider
import com.example.widget.MiniTornadoWidgetProvider

enum class TwisterDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TWISTER("twister", "Twister", Icons.Filled.Air, Icons.Filled.Air),
    APPS("apps", "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    WIDGET("widget", "Widget", Icons.Filled.Widgets, Icons.Outlined.Widgets)
}

@Composable
fun CacheTwisterScreen(
    viewModel: CacheTwisterViewModel,
    launchVerifyTrigger: Int
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(TwisterDestination.TWISTER) }

    // Return to Twister home tab on system Back when viewing secondary tabs
    BackHandler(enabled = currentTab != TwisterDestination.TWISTER) {
        currentTab = TwisterDestination.TWISTER
    }

    // Trigger pre-clean verification when launched from the home screen widget
    LaunchedEffect(launchVerifyTrigger) {
        if (launchVerifyTrigger > 0) {
            currentTab = TwisterDestination.TWISTER
            viewModel.startPreCleanVerification()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val safeStartActivity: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            viewModel.postStatusMessage("Unable to open Android Settings screen on this device.")
        }
    }

    val pinMiniWidgetAction: () -> Unit = {
        val status = MiniTornadoWidgetProvider.requestPinToHomeScreen(context)
        if (status == CacheTwisterWidgetProvider.PinWidgetStatus.REQUESTED) {
            viewModel.postStatusMessage("Confirm the prompt to add the little 1×1 Mini Tornado widget to your home screen.")
        } else {
            viewModel.postStatusMessage("Touch and hold your home screen → Widgets → Cache Twister → Mini Tornado (1×1).")
        }
    }

    val pinFullWidgetAction: () -> Unit = {
        val status = CacheTwisterWidgetProvider.requestPinToHomeScreen(context)
        if (status == CacheTwisterWidgetProvider.PinWidgetStatus.REQUESTED) {
            viewModel.postStatusMessage("Confirm the prompt to add the 2×2 Cache Twister widget to your home screen.")
        } else {
            viewModel.postStatusMessage("Touch and hold your home screen → Widgets → Cache Twister (2×2).")
        }
    }

    val openAppStorageAction: (AppCacheEntry) -> Unit = { entry ->
        viewModel.prepareExternalAppStorageOpen(entry)
        safeStartActivity(viewModel.createAppStorageIntent(entry.packageName))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        TwisterDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TwisterDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)

                    when (currentTab) {
                        TwisterDestination.TWISTER -> TwisterHomeTab(
                            uiState = uiState,
                            onStartVerify = { viewModel.startPreCleanVerification() },
                            onResetToIdle = { viewModel.resetToIdle() },
                            onPinMiniWidget = pinMiniWidgetAction,
                            onPinFullWidget = pinFullWidgetAction,
                            onOpenWidgetStudio = { currentTab = TwisterDestination.WIDGET },
                            onOpenAppsTab = { currentTab = TwisterDestination.APPS },
                            onOpenNextGuidedApp = { entry -> openAppStorageAction(entry) },
                            onOpenSystemStorage = { safeStartActivity(viewModel.createSystemStorageIntent()) },
                            onGrantUsageAccess = { safeStartActivity(viewModel.createUsageAccessIntent()) },
                            modifier = contentModifier
                        )
                        TwisterDestination.APPS -> AppsQueueTab(
                            uiState = uiState,
                            onRefreshScan = { viewModel.refreshScan() },
                            onGrantUsageAccess = { safeStartActivity(viewModel.createUsageAccessIntent()) },
                            onToggleAppSelection = { viewModel.toggleAppSelection(it) },
                            onSelectAll = { viewModel.setAllAppsSelected(it) },
                            onOpenAppStorage = openAppStorageAction,
                            onOpenSystemStorage = { safeStartActivity(viewModel.createSystemStorageIntent()) },
                            onVerifyAndSweep = {
                                currentTab = TwisterDestination.TWISTER
                                viewModel.startPreCleanVerification()
                            },
                            modifier = contentModifier
                        )
                        TwisterDestination.WIDGET -> WidgetStudioTab(
                            uiState = uiState,
                            onPinMiniWidgetClick = pinMiniWidgetAction,
                            onPinFullWidgetClick = pinFullWidgetAction,
                            onToggleRequireVerify = { viewModel.setRequireVerifyBeforeWidgetClean(it) },
                            onToggleHaptic = { viewModel.setHapticEnabled(it) },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }

    // Explicit Pre-Clean Safety Verification Confirmation Modal
    val report = uiState.verificationReport
    if (uiState.phase == CleanPhase.CONFIRM_READY && report != null) {
        PreCleanVerificationSheet(
            report = report,
            onConfirmTornado = { viewModel.confirmAndStartTornadoClean() },
            onGrantUsageAccess = { safeStartActivity(viewModel.createUsageAccessIntent()) },
            onDismiss = { viewModel.dismissVerification() }
        )
    }
}

@Composable
private fun TwisterHomeTab(
    uiState: CacheTwisterUiState,
    onStartVerify: () -> Unit,
    onResetToIdle: () -> Unit,
    onPinMiniWidget: () -> Unit,
    onPinFullWidget: () -> Unit,
    onOpenWidgetStudio: () -> Unit,
    onOpenAppsTab: () -> Unit,
    onOpenNextGuidedApp: (AppCacheEntry) -> Unit,
    onOpenSystemStorage: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceTitle = remember {
        Build.MODEL.orEmpty().ifBlank { "Android" }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$deviceTitle · Safe Cache Guard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(
                onClick = onPinMiniWidget,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("top_pin_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddToHomeScreen,
                    contentDescription = stringResource(R.string.cd_add_mini_widget),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mini Widget",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Centerpiece Animated Mini-Tornado Vortex Stage
        val centerPrimary = when (uiState.phase) {
            CleanPhase.VERIFYING -> "Verifying"
            CleanPhase.TORNADO_SWEEPING -> "${(uiState.sweepProgress * 100).toInt()}%"
            CleanPhase.COMPLETED -> if (uiState.sessionFreedBytes > 0L) {
                formatBytes(uiState.sessionFreedBytes)
            } else {
                "Verified"
            }
            else -> if (uiState.hasUsageAccess && uiState.totalMeasuredCacheBytes > 0L) {
                formatBytes(uiState.totalMeasuredCacheBytes)
            } else {
                "${uiState.selectedApps.size} Apps"
            }
        }
        val centerSecondary = when (uiState.phase) {
            CleanPhase.VERIFYING -> "4-point safety check"
            CleanPhase.TORNADO_SWEEPING -> "Tornado sweeping"
            CleanPhase.COMPLETED -> if (uiState.sessionFreedBytes > 0L) "Verified freed" else "Safe & checked"
            else -> if (uiState.hasUsageAccess) "Measured app cache" else "Ready to verify"
        }

        TornadoVortexCanvas(
            phase = uiState.phase,
            sweepProgress = uiState.sweepProgress,
            storageUsedFraction = uiState.storageSummary.usedFraction,
            centerPrimaryLabel = centerPrimary,
            centerSecondaryLabel = centerSecondary,
            onStageClick = {
                if (uiState.phase == CleanPhase.IDLE || uiState.phase == CleanPhase.COMPLETED) {
                    onStartVerify()
                }
            }
        )

        // Live Tornado Telemetry Caption
        AnimatedVisibility(visible = uiState.currentlySweepingLabel.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = uiState.currentlySweepingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Primary Action Button
        Button(
            onClick = {
                when (uiState.phase) {
                    CleanPhase.COMPLETED -> onStartVerify()
                    CleanPhase.IDLE -> onStartVerify()
                    else -> Unit
                }
            },
            enabled = uiState.phase == CleanPhase.IDLE || uiState.phase == CleanPhase.COMPLETED,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("verify_and_clean_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (uiState.phase == CleanPhase.COMPLETED) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.VerifiedUser
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (uiState.phase) {
                    CleanPhase.VERIFYING -> "Verifying Safety Checks…"
                    CleanPhase.TORNADO_SWEEPING -> "Tornado Cleaning…"
                    CleanPhase.COMPLETED -> "Re-Verify & Sweep Again"
                    else -> "Verify & Clean Cache"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Post-Tornado Guided Per-App Safe Storage Runner
        val nextApp = uiState.nextGuidedApp
        AnimatedVisibility(visible = uiState.phase == CleanPhase.COMPLETED && nextApp != null) {
            if (nextApp != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Safe Guided App Sweep",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "Android 15 protects each app's private cache. Tap below to open ${nextApp.appName}'s official Storage screen, tap Clear cache, and return here to auto-verify.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenNextGuidedApp(nextApp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("guided_next_app_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text(
                                    text = "Clean ${nextApp.appName}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onResetToIdle,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("guided_done_button")
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }

        // Prominent Home Screen Widgets Quick-Pin Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            imageVector = Icons.Filled.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Home Screen Tornado Widgets",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState.totalActiveWidgets > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${uiState.totalActiveWidgets} Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Place the little 1×1 Mini Tornado button or the 2×2 control card directly on your home screen for one-tap sweeping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPinMiniWidget,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_pin_mini_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddToHomeScreen,
                            contentDescription = stringResource(R.string.cd_add_mini_widget),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pin 1×1 Mini",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    FilledTonalButton(
                        onClick = onPinFullWidget,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_pin_full_widget_button")
                    ) {
                        Text(
                            text = "Pin 2×2 Card",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Visual Storage & Largest Cache Footprints Dashboard
        StorageFootprintDashboard(
            uiState = uiState,
            onOpenAppStorage = onOpenNextGuidedApp,
            onSweepOwnApp = onStartVerify,
            onGrantUsageAccess = onGrantUsageAccess,
            onOpenAllApps = onOpenAppsTab
        )

        // Quick Secondary Utilities Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = if (uiState.hasUsageAccess) onOpenAppsTab else onGrantUsageAccess,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("home_apps_or_usage_button")
            ) {
                Text(
                    text = if (uiState.hasUsageAccess) "Inspect ${uiState.apps.size} Apps" else "Enable MB Scan",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onOpenSystemStorage,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("home_os_storage_button")
            ) {
                Text(
                    text = "System Storage",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
