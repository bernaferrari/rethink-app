package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigureAccentsHarmonizeTest {

    @Test
    fun sameHue_isUnchanged() {
        val red = Color.hsv(0f, 0.6f, 0.9f)
        assertClose(red, harmonize(red, Color.hsv(0f, 0.2f, 0.4f)))
    }

    @Test
    fun smallDistance_movesHalfway() {
        // 20° apart → rotate 10° toward the source.
        val result = harmonize(Color.hsv(100f, 0.5f, 0.8f), Color.hsv(120f, 0.5f, 0.8f))
        assertClose(Color.hsv(110f, 0.5f, 0.8f), result)
    }

    @Test
    fun largeDistance_isCappedAt15Degrees_andTakesTheShortWayAround() {
        // 350° → 30° is 40° forward across 0°, so rotate +15° to 5°.
        val result = harmonize(Color.hsv(350f, 0.7f, 0.7f), Color.hsv(30f, 0.7f, 0.7f))
        assertClose(Color.hsv(5f, 0.7f, 0.7f), result)
    }

    private fun assertClose(expected: Color, actual: Color) {
        val tolerance = 0.01f
        assertTrue(
            abs(expected.red - actual.red) < tolerance &&
                abs(expected.green - actual.green) < tolerance &&
                abs(expected.blue - actual.blue) < tolerance,
            "expected $expected but was $actual",
        )
        assertEquals(expected.alpha, actual.alpha)
    }
}
