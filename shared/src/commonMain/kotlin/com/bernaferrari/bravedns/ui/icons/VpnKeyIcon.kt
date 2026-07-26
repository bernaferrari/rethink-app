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
internal val filledVpnKey: ImageVector
  get() {
    if (_filledVpnKey != null) {
      return _filledVpnKey!!
    }
    _filledVpnKey =
      ImageVector.Builder(
          name = "vpn_key",
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
            moveTo(7f, 18f)
            quadTo(4.5f, 18f, 2.75f, 16.25f)
            reflectiveQuadTo(1f, 12f)
            reflectiveQuadTo(2.75f, 7.75f)
            reflectiveQuadTo(7f, 6f)
            quadToRelative(2.03f, 0f, 3.54f, 1.14f)
            reflectiveQuadTo(12.65f, 10f)
            horizontalLineTo(21f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(23f, 11.18f, 23f, 12f)
            quadToRelative(0f, 0.9f, -0.63f, 1.45f)
            reflectiveQuadTo(21f, 14f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 18f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(17f, 16f)
            verticalLineTo(14f)
            horizontalLineTo(12.65f)
            quadToRelative(-0.6f, 1.72f, -2.11f, 2.86f)
            reflectiveQuadTo(7f, 18f)
            close()
            moveTo(7f, 14f)
            quadToRelative(0.83f, 0f, 1.41f, -0.59f)
            reflectiveQuadTo(9f, 12f)
            reflectiveQuadTo(8.41f, 10.59f)
            quadTo(7.83f, 10f, 7f, 10f)
            reflectiveQuadTo(5.59f, 10.59f)
            quadTo(5f, 11.18f, 5f, 12f)
            reflectiveQuadToRelative(0.59f, 1.41f)
            reflectiveQuadTo(7f, 14f)
            close()
          }
        }
        .build()
    return _filledVpnKey!!
  }

internal var _filledVpnKey: ImageVector? = null
