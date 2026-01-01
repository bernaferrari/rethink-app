package com.celzero.bravedns.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit

@Composable
fun SubscriptionAnimDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var started by remember { mutableStateOf(false) }
            AndroidView(
                factory = { KonfettiView(it) },
                update = { view ->
                    if (!started) {
                        view.start(festive())
                        started = true
                    }
                    view.setBackgroundColor(Color.Transparent.toArgb())
                }
            )
            LaunchedEffect(Unit) {
                delay(DIALOG_DISPLAY_DURATION_MS)
                onDismiss()
            }
        }
    }
}

private const val DIALOG_DISPLAY_DURATION_MS = 2000L

private const val PARTY_SPEED_DEFAULT = 30f
private const val PARTY_MAX_SPEED_DEFAULT = 50f
private const val PARTY_DAMPING = 0.9f
private const val PARTY_SPREAD_DEFAULT = 45
private const val PARTY_TIME_TO_LIVE_MS = 3000L
private const val PARTY_EMITTER_DURATION_MS = 100L
private const val PARTY_EMITTER_MAX_DEFAULT = 30

private const val PARTY_SPEED_VARIANT_1 = 55f
private const val PARTY_MAX_SPEED_VARIANT_1 = 65f
private const val PARTY_SPREAD_VARIANT = 10
private const val PARTY_EMITTER_MAX_VARIANT = 10

private const val PARTY_SPEED_VARIANT_2 = 65f
private const val PARTY_MAX_SPEED_VARIANT_2 = 80f

private const val POSITION_X_CENTER = 0.5
private const val POSITION_Y_BOTTOM = 1.0

private fun festive(): List<Party> {
    val party = Party(
        speed = PARTY_SPEED_DEFAULT,
        maxSpeed = PARTY_MAX_SPEED_DEFAULT,
        damping = PARTY_DAMPING,
        angle = Angle.TOP,
        spread = PARTY_SPREAD_DEFAULT,
        size = listOf(
            Size.SMALL,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE,
            Size.LARGE
        ),
        shapes = listOf(
            Shape.Square,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle,
            Shape.Circle
        ),
        timeToLive = PARTY_TIME_TO_LIVE_MS,
        rotation = Rotation(),
        colors = listOf(
            0xf0efe4,
            0xe6e5de,
            0xf4306d,
            0xfbfbf7,
            0xd8d6c2,
            0xf0efe4,
            0xe6e5de,
            0xf4306d,
            0xfbfbf7,
            0xd8d6c2
        ),
        emitter = Emitter(duration = PARTY_EMITTER_DURATION_MS, TimeUnit.MILLISECONDS)
            .max(PARTY_EMITTER_MAX_DEFAULT),
        position = Position.Relative(POSITION_X_CENTER, POSITION_Y_BOTTOM)
    )

    return listOf(
        party,
        party.copy(
            speed = PARTY_SPEED_VARIANT_1,
            maxSpeed = PARTY_MAX_SPEED_VARIANT_1,
            spread = PARTY_SPREAD_VARIANT,
            emitter = Emitter(duration = PARTY_EMITTER_DURATION_MS, TimeUnit.MILLISECONDS)
                .max(PARTY_EMITTER_MAX_VARIANT)
        ),
        party.copy(
            speed = PARTY_SPEED_VARIANT_2,
            maxSpeed = PARTY_MAX_SPEED_VARIANT_2,
            spread = PARTY_SPREAD_VARIANT,
            emitter = Emitter(duration = PARTY_EMITTER_DURATION_MS, TimeUnit.MILLISECONDS)
                .max(PARTY_EMITTER_MAX_VARIANT)
        )
    )
}
