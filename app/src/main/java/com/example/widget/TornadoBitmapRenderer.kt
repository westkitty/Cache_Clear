package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object TornadoBitmapRenderer {

    private const val BITMAP_SIZE = 216

    fun renderFrame(
        progress: Float,
        spinRadians: Float,
        intensity: Float,
        isVerifying: Boolean = false,
        isCompleted: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = BITMAP_SIZE.toFloat()
        val h = BITMAP_SIZE.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f

        // 1. Soft atmospheric radial aura
        val auraColor = when {
            isCompleted -> Color.argb(95, 52, 211, 153)
            isVerifying -> Color.argb(90, 251, 191, 36)
            else -> Color.argb((70 + (intensity * 75)).toInt().coerceIn(0, 180), 34, 211, 238)
        }
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                w * 0.48f,
                intArrayOf(auraColor, Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, w * 0.48f, auraPaint)

        // 2. Outer subtle telemetry ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.argb(42, 148, 210, 255)
        }
        val ringRect = RectF(14f, 14f, w - 14f, h - 14f)
        canvas.drawOval(ringRect, trackPaint)

        // Active sweep or verification arc
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            color = when {
                isCompleted -> Color.rgb(52, 211, 153)
                isVerifying -> Color.rgb(251, 191, 36)
                else -> Color.rgb(34, 211, 238)
            }
        }
        val sweepAngle = when {
            isCompleted -> 360f
            isVerifying -> 110f
            intensity > 0.25f -> (progress.coerceIn(0.05f, 1f) * 360f)
            else -> 75f
        }
        val startAngle = if (isVerifying) (spinRadians * 57.2958f) % 360f else -90f
        canvas.drawArc(ringRect, startAngle, sweepAngle, false, arcPaint)

        if (isCompleted) {
            drawCompletionBurst(canvas, cx, cy, w)
            return bitmap
        }

        // 3. Swirling layered mini-tornado funnel
        val layers = 8
        val topY = h * 0.23f
        val bottomY = h * 0.80f
        val maxHalfWidth = w * 0.30f
        val minHalfWidth = w * 0.045f

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        for (i in 0 until layers) {
            val t = i.toFloat() / (layers - 1).toFloat() // 0 = top wide, 1 = bottom tip
            val y = topY + (bottomY - topY) * t
            val radiusX = maxHalfWidth * (1f - t * 0.84f) + minHalfWidth * t
            val radiusY = (radiusX * 0.32f).coerceAtLeast(4.5f)
            val wobbleAmp = (4f + intensity * 11f) * (0.35f + t * 0.85f)
            val swayX = sin(spinRadians * 1.4f + t * 3.8f) * wobbleAmp
            val centerX = cx + swayX

            val alpha = ((195 + 55 * (1f - t)) * (0.72f + intensity * 0.28f)).toInt().coerceIn(70, 255)
            ringPaint.strokeWidth = (9.5f - t * 4.5f) * (0.9f + intensity * 0.25f)
            ringPaint.shader = LinearGradient(
                centerX - radiusX,
                y,
                centerX + radiusX,
                y,
                intArrayOf(
                    Color.argb(alpha, 14, 165, 233),
                    Color.argb(alpha, 224, 248, 255),
                    Color.argb(alpha, 34, 211, 238)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            val oval = RectF(
                centerX - radiusX,
                y - radiusY,
                centerX + radiusX,
                y + radiusY
            )
            val rotDeg = ((spinRadians * 180f / PI.toFloat()) * (1.2f + t * 0.9f) + i * 42f) % 360f
            canvas.drawArc(oval, rotDeg, 255f, false, ringPaint)
        }

        // 4. Orbiting cache debris particles sucked into the vortex
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val particleCount = 16
        for (p in 0 until particleCount) {
            val seed = p.toFloat() / particleCount.toFloat()
            val spiralPull = (1f - progress * 0.65f).coerceIn(0.22f, 1f)
            val angle = spinRadians * (1.8f + (p % 3) * 0.45f) + seed * (2f * PI.toFloat())
            val orbitR = (w * 0.16f + (p % 4) * w * 0.068f) * spiralPull
            val py = topY + (bottomY - topY) * ((seed + progress * 0.4f) % 1f)
            val px = cx + cos(angle) * orbitR
            val pAlpha = ((140 + intensity * 115) * (1f - progress * 0.35f)).toInt().coerceIn(45, 255)

            particlePaint.color = when (p % 3) {
                0 -> Color.argb(pAlpha, 103, 232, 249)
                1 -> Color.argb(pAlpha, 240, 249, 255)
                else -> Color.argb(pAlpha, 52, 211, 153)
            }
            val dotRadius = if (p % 4 == 0) 4.2f else 2.8f
            canvas.drawCircle(px, py, dotRadius, particlePaint)
        }

        return bitmap
    }

    private fun drawCompletionBurst(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(46, 52, 211, 153)
        }
        canvas.drawCircle(cx, cy, size * 0.27f, badgePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.rgb(52, 211, 153)
        }
        canvas.drawCircle(cx, cy, size * 0.27f, borderPaint)

        val checkPath = Path().apply {
            moveTo(cx - size * 0.12f, cy + size * 0.01f)
            lineTo(cx - size * 0.03f, cy + size * 0.10f)
            lineTo(cx + size * 0.14f, cy - size * 0.09f)
        }
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.rgb(220, 252, 231)
        }
        canvas.drawPath(checkPath, checkPaint)

        // Sparkle stars around the verified burst
        val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(103, 232, 249)
        }
        val offsets = arrayOf(
            Pair(-0.34f, -0.24f),
            Pair(0.33f, -0.20f),
            Pair(-0.28f, 0.28f),
            Pair(0.30f, 0.25f),
            Pair(0.0f, -0.36f)
        )
        for ((dx, dy) in offsets) {
            canvas.drawCircle(cx + size * dx, cy + size * dy, 4.2f, sparklePaint)
        }
    }
}
