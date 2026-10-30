package com.bernaferrari.bravedns.ui.icons

// Generated from Google Material Symbols Rounded's Kotlin vector endpoint.
// FILL=1 is Filled and FILL=0 is Outlined; opsz=24, wght=400, GRAD=0, ROND=50.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val filledArrowDownward: ImageVector
  get() {
    if (cachedFilledArrowDownward != null) {
      return cachedFilledArrowDownward!!
    }
    cachedFilledArrowDownward =
      ImageVector.Builder(
          name = "arrow_downward",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(11f, 16.18f)
            verticalLineTo(5f)
            quadTo(11f, 4.57f, 11.29f, 4.29f)
            reflectiveQuadTo(12f, 4f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(13f, 5f)
            verticalLineTo(16.18f)
            lineToRelative(4.9f, -4.9f)
            quadToRelative(0.3f, -0.3f, 0.7f, -0.29f)
            reflectiveQuadToRelative(0.7f, 0.31f)
            quadToRelative(0.28f, 0.3f, 0.29f, 0.7f)
            reflectiveQuadTo(19.3f, 12.7f)
            lineToRelative(-6.6f, 6.6f)
            quadToRelative(-0.15f, 0.15f, -0.33f, 0.21f)
            reflectiveQuadTo(12f, 19.58f)
            reflectiveQuadTo(11.63f, 19.51f)
            reflectiveQuadTo(11.3f, 19.3f)
            lineTo(4.7f, 12.7f)
            quadTo(4.43f, 12.43f, 4.43f, 12.01f)
            reflectiveQuadTo(4.7f, 11.3f)
            quadTo(5f, 11f, 5.41f, 11f)
            reflectiveQuadToRelative(0.71f, 0.3f)
            lineTo(11f, 16.18f)
            close()
          }
        }
        .build()
    return cachedFilledArrowDownward!!
  }

internal var cachedFilledArrowDownward: ImageVector? = null
