/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared cipher-current renderer. A sparse, disordered packet field resolves into a woven tunnel
 * as protection activates. Android 13+ and Wasm use the same composition in a fragment shader.
 */
@Composable
internal fun ProtectionFlowCanvas(
    phase: Float,
    activeProgress: Float,
    recoveringProgress: Float,
    accent: Color,
    secondary: Color,
    field: Color,
    surface: Color,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        drawRect(surface)
        val theta = phase * FLOW_TAU
        val enabled = activeProgress.coerceIn(0f, 1f)
        val recovering = recoveringProgress.coerceIn(0f, 1f)
        val strength = opacity.coerceIn(0f, 1f)
        val aspect = size.width / size.height
        val pixelSize = max(11f * density, size.minDimension * 0.008f)
        val columns = ceil(size.width / pixelSize).toInt()
        val rows = ceil(size.height / pixelSize).toInt()
        val inactiveInk = lerp(Color(0xFFCC454F), field, 0.28f)

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val pixelX = (column + 0.5f) * pixelSize
                val pixelY = (row + 0.5f) * pixelSize
                val x = (pixelX / size.width - 0.5f) * aspect
                val y = pixelY / size.height - 0.5f
                val noise = cipherNoise(x, y, theta)
                val fineNoise = cipherNoise(x * 2.43f, y * 2.43f, -theta + 1.7f)
                val centerline =
                    0.15f * sin(x * 2.7f + noise * 0.72f) +
                        0.045f * sin(x * 7f - theta * 2f)
                val fromCurrent = abs(y - centerline)
                val currentWidth = 0.19f + 0.025f * sin(x * 5f + theta)
                val currentBody =
                    1f - smoothstep(currentWidth, currentWidth + 0.25f, fromCurrent)
                val guardRails =
                    1f - smoothstep(0.012f, 0.045f, abs(fromCurrent - currentWidth))
                val laneSeed = floor(row / 3f) * 0.071f
                val packetPhase =
                    fract(
                        (x / max(aspect, 0.001f) + 0.5f) * 4f -
                            phase * 4f +
                            laneSeed +
                            noise * 0.055f,
                    )
                val packet = 1f - smoothstep(0.055f, 0.19f, abs(packetPhase - 0.5f))
                val cipherWeave =
                    0.5f +
                        0.5f * sin(
                            (y - centerline) * 43f + x * 6f + fineNoise * 0.7f,
                        )
                val ordered =
                    currentBody * (0.18f + packet * 0.55f + cipherWeave * 0.18f) +
                        guardRails * 0.72f
                val scattered = 0.16f + (noise * 0.5f + 0.5f) * 0.44f
                val rejected =
                    (1f - currentBody) *
                        smoothstep(0.64f, 0.86f, fineNoise * 0.5f + 0.5f) *
                        packet
                val shape =
                    lerpFloat(scattered, ordered, enabled)
                        .coerceAtLeast(rejected * (0.38f + 0.42f * recovering))
                if (shape <= bayer4x4(column, row)) continue

                val protectedInk = lerp(accent, secondary, cipherWeave * 0.72f)
                var ink = lerp(inactiveInk, protectedInk, enabled)
                ink = lerp(ink, Color(0xFFF05733), rejected * recovering)
                val centerQuiet =
                    0.12f +
                        0.88f * smoothstep(
                            0.13f,
                            0.37f,
                            sqrt(x * x * 0.64f + y * y),
                        )
                val texture = 0.78f + 0.22f * (noise * 0.5f + 0.5f)
                val alpha =
                    (0.06f + 0.06f * enabled) * centerQuiet * texture * strength
                drawRect(
                    color = ink.copy(alpha = alpha),
                    topLeft = Offset(column * pixelSize, row * pixelSize),
                    size = Size(
                        width = (pixelSize - density).coerceAtLeast(1f),
                        height = (pixelSize - density).coerceAtLeast(1f),
                    ),
                )
            }
        }
    }
}

private fun cipherNoise(x: Float, y: Float, theta: Float): Float =
    sin(x * 2.81f + cos(theta) * 0.52f) *
        cos(y * 3.17f + sin(theta) * 0.52f) *
        0.62f +
        sin((x + y) * 4.73f - theta) * 0.38f

private fun bayer2x2(x: Int, y: Int): Int = (2 * x + 3 * y) and 3

private fun bayer4x4(x: Int, y: Int): Float {
    val low = bayer2x2(x and 1, y and 1)
    val high = bayer2x2((x shr 1) and 1, (y shr 1) and 1)
    return (4 * low + high) / 16f
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun fract(value: Float): Float = value - floor(value)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private const val FLOW_TAU = (2.0 * PI).toFloat()
