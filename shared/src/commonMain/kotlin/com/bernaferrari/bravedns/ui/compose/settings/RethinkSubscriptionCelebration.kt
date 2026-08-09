/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion

/** Target-neutral purchase celebration; hosts decide whether it appears in a dialog or a route. */
@Composable
fun RethinkSubscriptionCelebration(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    displayDurationMillis: Long = 2_000L,
) {
    val motion = LocalRethinkMotion.current
    Box(modifier = modifier.fillMaxSize()) {
        if (!motion.reducedMotion) RethinkConfettiOverlay()
        LaunchedEffect(Unit) {
            delay(if (motion.reducedMotion) 600L else displayDurationMillis)
            onComplete()
        }
    }
}

private const val CONFETTI_COUNT = 90
private const val CONFETTI_DURATION_MS = 1_600
private const val CONFETTI_SPAWN_Y = 1.05f
private const val CONFETTI_GRAVITY = 0.55f

@Composable
private fun RethinkConfettiOverlay() {
    val palette = remember {
        listOf(Color(0xfff0efe4), Color(0xffe6e5de), Color(0xfff4306d), Color(0xfffbfbf7), Color(0xffd8d6c2))
    }
    val particles = remember {
        val random = Random(42)
        List(CONFETTI_COUNT) {
            RethinkConfettiParticle(
                angle = random.nextFloat() * 80f + 50f,
                speed = random.nextFloat() * 220f + 420f,
                size = random.nextFloat() * 8f + 6f,
                color = palette[random.nextInt(palette.size)],
                spin = random.nextFloat() * 360f,
                circle = random.nextBoolean(),
                drift = random.nextFloat() * 0.4f + 0.1f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = CONFETTI_DURATION_MS, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        particles.forEachIndexed { index, particle ->
            val theta = particle.angle * (PI / 180.0)
            val velocityX = cos(theta).toFloat() * particle.speed
            val velocityY = -sin(theta).toFloat() * particle.speed
            val time = progress.value + (index % 10) * 0.01f
            val x = size.width * 0.5f + velocityX * time + (time * time) * (particle.drift * size.width * 0.02f)
            val y = size.height * CONFETTI_SPAWN_Y + velocityY * time + (time * time) * (CONFETTI_GRAVITY * size.height * 0.2f)
            rotate(particle.spin * time * 1.2f, Offset(x, y)) {
                if (particle.circle) {
                    drawCircle(color = particle.color, radius = particle.size, center = Offset(x, y))
                } else {
                    drawRect(color = particle.color, topLeft = Offset(x - particle.size, y - particle.size), size = Size(particle.size * 2f, particle.size * 2f))
                }
            }
        }
    }
}

private data class RethinkConfettiParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val circle: Boolean,
    val drift: Float,
)
