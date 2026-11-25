package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CleanPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class DebrisParticle(
    val angleOffset: Float,
    val radiusFactor: Float,
    val verticalBias: Float,
    val speedMultiplier: Float,
    val sizeDp: Float,
    val tintIndex: Int
)

@Composable
fun TornadoVortexCanvas(
    phase: CleanPhase,
    sweepProgress: Float,
    storageUsedFraction: Float,
    centerPrimaryLabel: String,
    centerSecondaryLabel: String,
    onStageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val infiniteTransition = rememberInfiniteTransition(label = "tornado_spin")
    val spinDuration = when (phase) {
        CleanPhase.TORNADO_SWEEPING -> 900
        CleanPhase.VERIFYING -> 1600
        CleanPhase.CONFIRM_READY -> 2200
        else -> 4200
    }
    val spinRadians by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = spinDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    val intensity by animateFloatAsState(
        targetValue = when (phase) {
            CleanPhase.IDLE -> 0.28f
            CleanPhase.VERIFYING -> 0.58f
            CleanPhase.CONFIRM_READY -> 0.45f
            CleanPhase.TORNADO_SWEEPING -> 1.0f
            CleanPhase.COMPLETED -> 0.08f
        },
        animationSpec = tween(durationMillis = 360),
        label = "tornado_intensity"
    )

    val particles = remember {
        List(36) { index ->
            val goldenAngle = index * 2.399963f
            DebrisParticle(
                angleOffset = goldenAngle,
                radiusFactor = 0.24f + ((index * 19) % 100) / 100f * 0.22f,
                verticalBias = ((index * 31) % 100) / 100f,
                speedMultiplier = 1.0f + (index % 4) * 0.35f,
                sizeDp = if (index % 5 == 0) 4.5f else if (index % 2 == 0) 3.2f else 2.2f,
                tintIndex = index % 3
            )
        }
    }

    val cdText = stringResource(R.string.cd_tornado_visual)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .semantics { contentDescription = cdText }
            .testTag("tornado_stage_button")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onStageClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val outerRadius = minOf(w, h) * 0.46f

            // 1. Storm-Glass Atmospheric Radial Glow
            val auraCenterColor = when (phase) {
                CleanPhase.COMPLETED -> secondaryColor.copy(alpha = 0.28f)
                CleanPhase.VERIFYING -> tertiaryColor.copy(alpha = 0.24f)
                CleanPhase.TORNADO_SWEEPING -> primaryColor.copy(alpha = 0.34f)
                else -> primaryColor.copy(alpha = 0.16f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraCenterColor, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = outerRadius * pulseScale * 1.05f
                ),
                radius = outerRadius * pulseScale * 1.05f,
                center = Offset(cx, cy)
            )

            // 2. Outer Telemetry Track & Progress Arc
            val ringStroke = 7.dp.toPx()
            val arcInset = 14.dp.toPx()
            val arcSize = Size(w - arcInset * 2f, h - arcInset * 2f)
            val arcTopLeft = Offset(arcInset, arcInset)

            drawArc(
                color = outlineVariant.copy(alpha = 0.45f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringStroke)
            )

            val activeSweepAngle = when (phase) {
                CleanPhase.TORNADO_SWEEPING -> (sweepProgress.coerceIn(0.04f, 1f) * 360f)
                CleanPhase.VERIFYING -> 115f
                CleanPhase.COMPLETED -> 360f
                else -> (storageUsedFraction.coerceIn(0.08f, 0.98f) * 360f)
            }
            val activeStartAngle = if (phase == CleanPhase.VERIFYING) {
                (spinRadians * 180f / PI.toFloat()) % 360f
            } else {
                -90f
            }
            val arcColor = when (phase) {
                CleanPhase.COMPLETED -> secondaryColor
                CleanPhase.VERIFYING -> tertiaryColor
                else -> primaryColor
            }
            drawArc(
                color = arcColor,
                startAngle = activeStartAngle,
                sweepAngle = activeSweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            // 3. Swirling Multi-Tier Mini-Tornado Funnel
            if (phase != CleanPhase.COMPLETED) {
                val tiers = 11
                val topY = h * 0.21f
                val bottomY = h * 0.77f
                val maxRx = w * 0.28f
                val minRx = w * 0.032f

                for (i in 0 until tiers) {
                    val t = i.toFloat() / (tiers - 1).toFloat() // 0 = top wide cloud, 1 = narrow vortex tip
                    val y = topY + (bottomY - topY) * t
                    val taper = 1f - t * 0.87f
                    val rx = maxRx * taper + minRx * t
                    val ry = (rx * 0.29f).coerceAtLeast(3.5.dp.toPx())

                    val swayAmplitude = (5.dp.toPx() + intensity * 14.dp.toPx()) * (0.28f + t * 0.9f)
                    val swayX = sin(spinRadians * (1.2f + intensity * 1.4f) + t * 4.2f) * swayAmplitude
                    val tierCx = cx + swayX

                    val ribbonAlpha = ((0.30f + (1f - t) * 0.48f) * (0.65f + intensity * 0.35f)).coerceIn(0.18f, 0.95f)
                    val strokeW = ((6.8f - t * 3.8f) * (0.85f + intensity * 0.3f)).dp.toPx()
                    val rotDeg = ((spinRadians * 180f / PI.toFloat()) * (1.0f + t * 1.25f) + i * 36f) % 360f

                    val ribbonBrush = Brush.horizontalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = ribbonAlpha * 0.85f),
                            onSurfaceColor.copy(alpha = ribbonAlpha),
                            secondaryColor.copy(alpha = ribbonAlpha * 0.75f)
                        ),
                        startX = tierCx - rx,
                        endX = tierCx + rx
                    )

                    drawArc(
                        brush = ribbonBrush,
                        startAngle = rotDeg,
                        sweepAngle = 245f,
                        useCenter = false,
                        topLeft = Offset(tierCx - rx, y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // 4. Physics-driven cache debris particles spiraling inward
                val pullFactor = if (phase == CleanPhase.TORNADO_SWEEPING) {
                    (1f - sweepProgress * 0.78f).coerceIn(0.12f, 1f)
                } else {
                    1f
                }

                for (p in particles) {
                    val angle = p.angleOffset + spinRadians * p.speedMultiplier * (0.8f + intensity * 1.4f)
                    val r = w * p.radiusFactor * pullFactor
                    val verticalTravel = (p.verticalBias + (if (phase == CleanPhase.TORNADO_SWEEPING) sweepProgress * 0.5f else 0f)) % 1f
                    val py = topY + (bottomY - topY) * verticalTravel
                    val px = cx + cos(angle) * r
                    val alpha = ((0.35f + intensity * 0.65f) * pullFactor).coerceIn(0.12f, 0.95f)

                    val dotColor = when (p.tintIndex) {
                        0 -> primaryColor.copy(alpha = alpha)
                        1 -> onSurfaceColor.copy(alpha = alpha * 0.9f)
                        else -> secondaryColor.copy(alpha = alpha)
                    }

                    drawCircle(
                        color = dotColor,
                        radius = p.sizeDp.dp.toPx() * (0.65f + 0.35f * pullFactor),
                        center = Offset(px, py)
                    )
                }
            } else {
                // Completed shockwave rings & sparkles
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.18f),
                    radius = outerRadius * 0.62f * pulseScale,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.55f),
                    radius = outerRadius * 0.62f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                for (s in 0 until 10) {
                    val angle = s * (2f * PI.toFloat() / 10f) + spinRadians * 0.25f
                    val dist = outerRadius * (0.76f + (s % 2) * 0.08f)
                    drawCircle(
                        color = if (s % 2 == 0) primaryColor.copy(alpha = 0.85f) else secondaryColor.copy(alpha = 0.85f),
                        radius = if (s % 2 == 0) 3.5.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(cx + cos(angle) * dist, cy + sin(angle) * dist)
                    )
                }
            }
        }

        // Center crisp overlay badge for Completed / Verifying / Live Metric
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                CleanPhase.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                CleanPhase.VERIFYING -> {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = tertiaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> Unit
            }

            Text(
                text = centerPrimaryLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = centerSecondaryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
