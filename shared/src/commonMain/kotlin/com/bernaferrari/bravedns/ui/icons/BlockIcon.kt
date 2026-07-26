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
internal val filledBlock: ImageVector
  get() {
    if (_filledBlock != null) {
      return _filledBlock!!
    }
    _filledBlock =
      ImageVector.Builder(
          name = "block",
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
            moveTo(8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            reflectiveQuadToRelative(-0.79f, 3.9f)
            reflectiveQuadToRelative(-2.14f, 3.17f)
            quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
            reflectiveQuadTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            close()
            moveToRelative(8.83f, -2.89f)
            quadToRelative(0.4f, -0.3f, 0.75f, -0.65f)
            reflectiveQuadToRelative(0.65f, -0.75f)
            lineTo(7.08f, 5.68f)
            quadTo(6.68f, 5.97f, 6.33f, 6.32f)
            reflectiveQuadTo(5.68f, 7.07f)
            lineTo(16.93f, 18.33f)
            close()
          }
        }
        .build()
    return _filledBlock!!
  }

internal var _filledBlock: ImageVector? = null
