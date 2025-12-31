package com.celzero.bravedns.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import com.celzero.bravedns.databinding.DialogSubscriptionAnimBinding
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit

class SubscriptionAnimDialog(private val activity: FragmentActivity) {
    private val binding = DialogSubscriptionAnimBinding.inflate(LayoutInflater.from(activity))
    private val dialog = Dialog(activity)

    companion object {
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
    }

    init {
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)
    }

    fun show() {
        dialog.show()
        binding.konfettiView.start(festive())
        binding.konfettiView.postDelayed({
            dialog.dismiss()
        }, DIALOG_DISPLAY_DURATION_MS)
    }

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
}
