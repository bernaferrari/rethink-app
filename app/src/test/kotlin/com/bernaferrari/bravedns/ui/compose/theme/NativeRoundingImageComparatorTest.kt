package com.bernaferrari.bravedns.ui.compose.theme

import com.dropbox.differ.Color
import com.dropbox.differ.Image
import com.github.takahirom.roborazzi.RoborazziOptions
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class NativeRoundingImageComparatorTest {
    private val fixtures = listOf("log_rows_light", "log_rows_dark", "log_rows_reduced_motion",
        "status_color_chips_light", "status_color_chips_dark")

    @Test fun allFiveOriginalLinuxFailuresPassOnlyTheReviewedRoundingPolicy() {
        for (name in fixtures) {
            val expected = reference(name)
            val actual = linux(name)
            val original = RoborazziOptions.CompareOptions().imageComparator.compare(PngImage(expected), PngImage(actual))
            assertTrue("$name reproduces the original strict-comparator failure", original.pixelDifferences > 0)
            assertTrue(name, accepted(name, expected, actual))
        }
    }

    @Test fun twelveOtherFixturesAndUnreviewedNamesKeepTheOriginalComparator() {
        val others = requireNotNull(File("src/test/screenshots").listFiles())
            .filter { it.extension == "png" }.map { it.nameWithoutExtension }.filterNot { it in fixtures }
        assertEquals(12, others.size)
        for (name in others + "log_rows_unreviewed") {
            assertFalse(name, NativeRoundingImageComparator.compareOptionsFor(name).imageComparator === NativeRoundingImageComparator)
        }
    }

    @Test fun aSinglePixelThreeLevelsAwayInAnyRgbChannelStillFails() {
        val name = "log_rows_light"
        val expected = reference(name)
        for (shift in listOf(0, 8, 16)) {
            val changed = linux(name)
            val pixel = expected.getRGB(0, 0)
            val value = pixel ushr shift and 255
            val next = if (value > 252) value - 3 else value + 3
            changed.setRGB(0, 0, (pixel and (255 shl shift).inv()) or (next shl shift))
            assertFalse("RGB channel $shift", accepted(name, expected, changed))
        }
    }

    @Test fun aSingleAlphaLevelAtOnePixelStillFails() {
        val name = "log_rows_light"
        val changed = linux(name)
        changed.setRGB(0, 0, changed.getRGB(0, 0) xor (1 shl 24))
        assertFalse(accepted(name, reference(name), changed))
    }

    @Test fun onePixelDimensionChangesStillFail() {
        val name = "log_rows_light"
        val expected = reference(name)
        for ((width, height) in listOf(expected.width + 1 to expected.height,
            expected.width - 1 to expected.height, expected.width to expected.height + 1)) {
            assertFalse(accepted(name, expected, BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)))
        }
    }

    @Test fun movingOnlyTheRealTextByOnePixelStillFails() {
        val name = "log_rows_light"
        val changed = linux(name)
        val text = changed.getSubimage(240, 80, 420, 55)
        val copy = BufferedImage(420, 55, BufferedImage.TYPE_INT_ARGB)
        copy.createGraphics().apply { drawImage(text, 0, 0, null); dispose() }
        changed.createGraphics().apply {
            color = java.awt.Color(changed.getRGB(240, 80), true)
            fillRect(240, 80, 420, 55)
            drawImage(copy, 241, 80, null)
            dispose()
        }
        assertFalse(accepted(name, reference(name), changed))
    }

    @Test fun movingTheRealLayoutByOnePixelStillFails() {
        val name = "log_rows_light"
        val original = linux(name)
        val changed = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_ARGB)
        changed.createGraphics().apply {
            color = java.awt.Color(original.getRGB(0, 0), true)
            fillRect(0, 0, changed.width, changed.height)
            drawImage(original, 1, 0, null)
            dispose()
        }
        assertFalse(accepted(name, reference(name), changed))
    }

    @Test fun changingARealStatusFillByEightLevelsStillFails() {
        val name = "status_color_chips_light"
        val changed = linux(name)
        val fill = changed.getRGB(60, 70)
        var replaced = 0
        for (y in 0 until changed.height) for (x in 0 until changed.width) {
            if (changed.getRGB(x, y) == fill) { changed.setRGB(x, y, fill + (8 shl 8)); replaced++ }
        }
        assertTrue("Mutate the actual solid chip fill", replaced > 1000)
        assertFalse(accepted(name, reference(name), changed))
    }

    private fun accepted(name: String, expected: BufferedImage, actual: BufferedImage): Boolean {
        val options = NativeRoundingImageComparator.compareOptionsFor(name)
        return options.resultValidator(options.imageComparator.compare(PngImage(expected), PngImage(actual)))
    }

    private fun reference(name: String): BufferedImage = requireNotNull(javaClass.classLoader)
        .getResourceAsStream("roborazzi/mac/$name.png").use { ImageIO.read(requireNotNull(it)) }
    private fun linux(name: String): BufferedImage = requireNotNull(javaClass.classLoader)
        .getResourceAsStream("roborazzi/linux/$name.png").use { ImageIO.read(requireNotNull(it)) }
}

private class PngImage(private val image: BufferedImage) : Image {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override fun getPixel(x: Int, y: Int): Color {
        val pixel = image.getRGB(x, y)
        return Color((pixel ushr 16 and 255) / 255f, (pixel ushr 8 and 255) / 255f,
            (pixel and 255) / 255f, (pixel ushr 24 and 255) / 255f)
    }
}
