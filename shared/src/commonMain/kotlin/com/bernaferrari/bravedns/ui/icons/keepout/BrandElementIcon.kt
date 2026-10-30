package com.bernaferrari.bravedns.ui.icons.keepout

// Element brand mark, flattened from the former res/drawable/ic_element.xml.
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
internal val brandElement: ImageVector
  get() {
    if (_brandElement != null) {
      return _brandElement!!
    }
    _brandElement =
      ImageVector.Builder(
          name = "element",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(2f, 13.5f)
            quadTo(2f, 9.979f, 4.49f, 7.49f)
            quadTo(6.979f, 5f, 10.5f, 5f)
            quadTo(10.811f, 5f, 11.03f, 5.22f)
            quadTo(11.25f, 5.439f, 11.25f, 5.75f)
            quadTo(11.25f, 6.061f, 11.03f, 6.28f)
            quadTo(10.811f, 6.5f, 10.5f, 6.5f)
            quadTo(7.601f, 6.5f, 5.55f, 8.55f)
            quadTo(3.5f, 10.601f, 3.5f, 13.5f)
            quadTo(3.5f, 13.811f, 3.28f, 14.03f)
            quadTo(3.061f, 14.25f, 2.75f, 14.25f)
            quadTo(2.439f, 14.25f, 2.22f, 14.03f)
            quadTo(2f, 13.811f, 2f, 13.5f)
            close()
            moveTo(22f, 10.5f)
            quadTo(22f, 14.021f, 19.51f, 16.51f)
            quadTo(17.021f, 19f, 13.5f, 19f)
            quadTo(13.189f, 19f, 12.97f, 18.78f)
            quadTo(12.75f, 18.561f, 12.75f, 18.25f)
            quadTo(12.75f, 17.939f, 12.97f, 17.72f)
            quadTo(13.189f, 17.5f, 13.5f, 17.5f)
            quadTo(16.399f, 17.5f, 18.45f, 15.45f)
            quadTo(20.5f, 13.399f, 20.5f, 10.5f)
            quadTo(20.5f, 10.189f, 20.72f, 9.97f)
            quadTo(20.939f, 9.75f, 21.25f, 9.75f)
            quadTo(21.561f, 9.75f, 21.78f, 9.97f)
            quadTo(22f, 10.189f, 22f, 10.5f)
            close()
            moveTo(10.5f, 2f)
            quadTo(13.078f, 2f, 15.222f, 3.432f)
            quadTo(17.366f, 4.865f, 18.353f, 7.247f)
            quadTo(19f, 8.809f, 19f, 10.5f)
            quadTo(19f, 10.811f, 18.78f, 11.03f)
            quadTo(18.561f, 11.25f, 18.25f, 11.25f)
            quadTo(17.939f, 11.25f, 17.72f, 11.03f)
            quadTo(17.5f, 10.811f, 17.5f, 10.5f)
            quadTo(17.5f, 9.107f, 16.967f, 7.821f)
            quadTo(16.154f, 5.859f, 14.389f, 4.68f)
            quadTo(12.623f, 3.5f, 10.5f, 3.5f)
            quadTo(10.189f, 3.5f, 9.97f, 3.28f)
            quadTo(9.75f, 3.061f, 9.75f, 2.75f)
            quadTo(9.75f, 2.439f, 9.97f, 2.22f)
            quadTo(10.189f, 2f, 10.5f, 2f)
            close()
            moveTo(13.5f, 22f)
            quadTo(10.922f, 22f, 8.778f, 20.567f)
            quadTo(6.634f, 19.135f, 5.647f, 16.753f)
            quadTo(5f, 15.191f, 5f, 13.5f)
            quadTo(5f, 13.189f, 5.22f, 12.97f)
            quadTo(5.439f, 12.75f, 5.75f, 12.75f)
            quadTo(6.061f, 12.75f, 6.28f, 12.97f)
            quadTo(6.5f, 13.189f, 6.5f, 13.5f)
            quadTo(6.5f, 14.893f, 7.033f, 16.179f)
            quadTo(7.846f, 18.141f, 9.611f, 19.32f)
            quadTo(11.377f, 20.5f, 13.5f, 20.5f)
            quadTo(13.811f, 20.5f, 14.03f, 20.72f)
            quadTo(14.25f, 20.939f, 14.25f, 21.25f)
            quadTo(14.25f, 21.561f, 14.03f, 21.78f)
            quadTo(13.811f, 22f, 13.5f, 22f)
            close()
          }
        }
        .build()
    return _brandElement!!
  }

internal var _brandElement: ImageVector? = null
