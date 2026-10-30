package com.bernaferrari.bravedns.ui.compose.theme

import com.dropbox.differ.Color
import com.dropbox.differ.Image
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.Mask
import com.github.takahirom.roborazzi.RoborazziOptions
import kotlin.math.abs
import kotlin.math.roundToInt

/** Native Mac/Linux rasterization differs by at most two RGB levels in these five fixtures. */
internal object NativeRoundingImageComparator : ImageComparator {
    private val fixtures = setOf(
        "log_rows_light", "log_rows_dark", "log_rows_reduced_motion",
        "status_color_chips_light", "status_color_chips_dark",
    )

    fun compareOptionsFor(name: String): RoborazziOptions.CompareOptions = if (name in fixtures) {
        RoborazziOptions.CompareOptions(
            imageComparator = this,
            resultValidator = { it.pixelDifferences == 0 },
        )
    } else RoborazziOptions.CompareOptions()

    override fun compare(left: Image, right: Image, diff: Mask?): ImageComparator.ComparisonResult {
        require(diff == null) { "Rounding fixtures must compare every pixel without masks" }
        val width = maxOf(left.width, right.width)
        val height = maxOf(left.height, right.height)
        val count = maxOf(1, width * height)
        if (left.width != right.width || left.height != right.height) {
            return ImageComparator.ComparisonResult(count, count, width, height)
        }
        var differences = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (differs(left.getPixel(x, y), right.getPixel(x, y))) differences++
        }
        return ImageComparator.ComparisonResult(differences, count, width, height)
    }

    private fun differs(left: Color, right: Color): Boolean = left.a != right.a ||
        channelDifference(left.r, right.r) > 2 || channelDifference(left.g, right.g) > 2 ||
        channelDifference(left.b, right.b) > 2

    // The screenshot format is ARGB_8888. Round-trip normalized library channels to exact 8-bit values.
    private fun channelDifference(left: Float, right: Float): Int =
        abs((left * 255).roundToInt() - (right * 255).roundToInt())
}
