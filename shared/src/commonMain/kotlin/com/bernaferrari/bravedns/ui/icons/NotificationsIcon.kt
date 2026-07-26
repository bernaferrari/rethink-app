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
internal val filledNotifications: ImageVector
  get() {
    if (_filledNotifications != null) {
      return _filledNotifications!!
    }
    _filledNotifications =
      ImageVector.Builder(
          name = "notifications",
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
            moveTo(5f, 19f)
            quadTo(4.58f, 19f, 4.29f, 18.71f)
            quadTo(4f, 18.43f, 4f, 18f)
            reflectiveQuadTo(4.29f, 17.29f)
            reflectiveQuadTo(5f, 17f)
            horizontalLineTo(6f)
            verticalLineTo(10f)
            quadTo(6f, 7.93f, 7.25f, 6.31f)
            reflectiveQuadTo(10.5f, 4.2f)
            verticalLineTo(3.5f)
            quadToRelative(0f, -0.63f, 0.44f, -1.06f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadToRelative(1.06f, 0.44f)
            reflectiveQuadTo(13.5f, 3.5f)
            verticalLineTo(4.2f)
            quadToRelative(2f, 0.5f, 3.25f, 2.11f)
            reflectiveQuadTo(18f, 10f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(1f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 18f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(19f, 19f)
            horizontalLineTo(5f)
            close()
            moveToRelative(7f, 3f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(10f, 20f)
            horizontalLineToRelative(4f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(12f, 22f)
            close()
          }
        }
        .build()
    return _filledNotifications!!
  }

internal var _filledNotifications: ImageVector? = null
