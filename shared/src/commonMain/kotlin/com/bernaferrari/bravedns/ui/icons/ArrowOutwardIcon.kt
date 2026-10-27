package com.bernaferrari.bravedns.ui.icons

// Material Symbols "arrow_outward" (FILL=1, opsz=24), drawn by hand: an outgoing connection.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val filledArrowOutward: ImageVector by lazy {
    ImageVector.Builder(
        name = "arrow_outward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(14.59f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(16f, 9.41f)
            verticalLineTo(18f)
            horizontalLineTo(18f)
            verticalLineTo(6f)
            close()
        }
    }.build()
}
