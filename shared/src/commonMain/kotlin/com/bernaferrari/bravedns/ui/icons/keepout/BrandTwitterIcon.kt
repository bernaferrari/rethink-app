package com.bernaferrari.bravedns.ui.icons.keepout

// Twitter brand mark, flattened from the former res/drawable/ic_twitter.xml.
// 24dp viewport, fill-only paths (strokes flattened); tint at use, like MaterialSymbols.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val brandTwitter: ImageVector
  get() {
    if (_brandTwitter != null) {
      return _brandTwitter!!
    }
    _brandTwitter =
      ImageVector.Builder(
          name = "twitter",
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
            moveTo(3f, 2f)
            lineTo(8.6f, 2f)
            lineTo(12.8f, 8f)
            lineTo(18f, 2f)
            lineTo(21f, 2f)
            lineTo(14.2f, 9.9f)
            lineTo(22f, 22f)
            lineTo(16.4f, 22f)
            lineTo(11.8f, 15.4f)
            lineTo(6.1f, 22f)
            lineTo(3.1f, 22f)
            lineTo(10.5f, 13.5f)
            close()
            moveTo(7.3f, 4.3f)
            lineTo(5.4f, 4.3f)
            lineTo(16.7f, 19.7f)
            lineTo(18.6f, 19.7f)
            close()
          }
        }
        .build()
    return _brandTwitter!!
  }

internal var _brandTwitter: ImageVector? = null
