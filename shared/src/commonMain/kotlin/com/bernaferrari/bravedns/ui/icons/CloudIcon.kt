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
internal val filledCloud: ImageVector
  get() {
    if (_filledCloud != null) {
      return _filledCloud!!
    }
    _filledCloud =
      ImageVector.Builder(
          name = "cloud",
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
            moveTo(6.5f, 20f)
            quadTo(4.23f, 20f, 2.61f, 18.43f)
            reflectiveQuadTo(1f, 14.58f)
            quadTo(1f, 12.63f, 2.18f, 11.1f)
            reflectiveQuadTo(5.25f, 9.15f)
            quadTo(5.88f, 6.85f, 7.75f, 5.43f)
            reflectiveQuadTo(12f, 4f)
            quadToRelative(2.93f, 0f, 4.96f, 2.04f)
            reflectiveQuadTo(19f, 11f)
            quadToRelative(1.73f, 0.2f, 2.86f, 1.49f)
            reflectiveQuadTo(23f, 15.5f)
            quadToRelative(0f, 1.88f, -1.31f, 3.19f)
            reflectiveQuadTo(18.5f, 20f)
            horizontalLineTo(6.5f)
            close()
          }
        }
        .build()
    return _filledCloud!!
  }

internal var _filledCloud: ImageVector? = null
