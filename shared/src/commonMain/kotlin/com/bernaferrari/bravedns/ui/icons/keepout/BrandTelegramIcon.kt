package com.bernaferrari.bravedns.ui.icons.keepout

// Telegram brand mark, flattened from the former res/drawable/ic_telegram.xml.
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
internal val brandTelegram: ImageVector
  get() {
    if (_brandTelegram != null) {
      return _brandTelegram!!
    }
    _brandTelegram =
      ImageVector.Builder(
          name = "telegram",
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
            moveTo(22.885f, 2.498f)
            lineTo(15.944f, 22.33f)
            quadTo(15.614f, 23.274f, 14.67f, 22.944f)
            quadTo(14.262f, 22.801f, 14.086f, 22.406f)
            lineTo(10.242f, 13.758f)
            lineTo(1.594f, 9.914f)
            quadTo(1.137f, 9.711f, 1.01f, 9.381f)
            quadTo(0.883f, 9.051f, 1.086f, 8.594f)
            quadTo(1.262f, 8.199f, 1.67f, 8.056f)
            lineTo(21.502f, 1.115f)
            quadTo(21.867f, 0.857f, 22.233f, 0.978f)
            quadTo(22.636f, 1.03f, 22.856f, 1.462f)
            quadTo(23.061f, 1.731f, 23.061f, 2f)
            quadTo(23.061f, 2.249f, 22.885f, 2.498f)
            close()
            moveTo(10.779f, 11.807f)
            lineTo(18.194f, 4.391f)
            lineTo(4.711f, 9.111f)
            lineTo(10.779f, 11.807f)
            close()
            moveTo(12.193f, 13.221f)
            lineTo(14.889f, 19.289f)
            lineTo(19.609f, 5.806f)
            lineTo(12.193f, 13.221f)
            close()
          }
        }
        .build()
    return _brandTelegram!!
  }

internal var _brandTelegram: ImageVector? = null
