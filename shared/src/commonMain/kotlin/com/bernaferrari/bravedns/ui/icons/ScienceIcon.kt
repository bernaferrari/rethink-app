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
internal val filledScience: ImageVector
  get() {
    if (_filledScience != null) {
      return _filledScience!!
    }
    _filledScience =
      ImageVector.Builder(
          name = "science",
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
            moveTo(5f, 21f)
            quadTo(3.73f, 21f, 3.19f, 19.86f)
            reflectiveQuadTo(3.45f, 17.75f)
            lineTo(9f, 11f)
            verticalLineTo(5f)
            horizontalLineTo(8f)
            quadTo(7.58f, 5f, 7.29f, 4.71f)
            reflectiveQuadTo(7f, 4f)
            quadTo(7f, 3.57f, 7.29f, 3.29f)
            reflectiveQuadTo(8f, 3f)
            horizontalLineToRelative(8f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(17f, 4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(16f, 5f)
            horizontalLineTo(15f)
            verticalLineToRelative(6f)
            lineToRelative(5.55f, 6.75f)
            quadToRelative(0.8f, 0.98f, 0.26f, 2.11f)
            quadTo(20.28f, 21f, 19f, 21f)
            horizontalLineTo(5f)
            close()
          }
        }
        .build()
    return _filledScience!!
  }

internal var _filledScience: ImageVector? = null
