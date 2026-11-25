package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StormDarkColorScheme = darkColorScheme(
    primary = CycloneCyan,
    onPrimary = StormDeepBg,
    primaryContainer = CycloneCyanContainer,
    onPrimaryContainer = CycloneCyanBright,
    secondary = SafeEmerald,
    onSecondary = SafeEmeraldDeep,
    secondaryContainer = SafeEmeraldContainer,
    onSecondaryContainer = SafeEmerald,
    tertiary = CautionAmber,
    onTertiary = CautionAmberDeep,
    tertiaryContainer = CautionAmberContainer,
    onTertiaryContainer = CautionAmber,
    background = StormDeepBg,
    onBackground = IceWhite,
    surface = StormSurface,
    onSurface = IceWhite,
    surfaceVariant = StormSurfaceVariant,
    onSurfaceVariant = MistText,
    surfaceContainerLowest = StormDeepBg,
    surfaceContainerLow = StormSurface,
    surfaceContainer = StormSurfaceVariant,
    surfaceContainerHigh = StormSurfaceHigh,
    outline = StormOutline,
    outlineVariant = StormOutlineSubtle
)

private val StormLightColorScheme = lightColorScheme(
    primary = LightCyclonePrimary,
    onPrimary = FrostSurface,
    primaryContainer = LightCycloneContainer,
    onPrimaryContainer = DeepSlateText,
    secondary = LightSafeEmerald,
    onSecondary = FrostSurface,
    secondaryContainer = LightSafeContainer,
    onSecondaryContainer = DeepSlateText,
    tertiary = LightCautionAmber,
    onTertiary = FrostSurface,
    tertiaryContainer = LightCautionContainer,
    onTertiaryContainer = DeepSlateText,
    background = FrostBg,
    onBackground = DeepSlateText,
    surface = FrostSurface,
    onSurface = DeepSlateText,
    surfaceVariant = FrostSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = FrostOutline,
    outlineVariant = FrostSurfaceVariant
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun CacheTwisterTheme(
    darkTheme: Boolean = true,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolvedDark = darkTheme || isSystemInDarkTheme()
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDark -> StormDarkColorScheme
        else -> StormLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
