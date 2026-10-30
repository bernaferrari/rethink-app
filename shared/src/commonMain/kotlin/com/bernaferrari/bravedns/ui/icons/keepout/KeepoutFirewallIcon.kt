package com.bernaferrari.bravedns.ui.icons.keepout

// Firewall: a brick wall whose centre brick is Keepout's keep-out bar.
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
internal val keepoutFirewall: ImageVector
  get() {
    if (cachedKeepoutFirewall != null) {
      return cachedKeepoutFirewall!!
    }
    cachedKeepoutFirewall =
      ImageVector.Builder(
          name = "firewall",
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
            moveTo(21.5f, 15.67f)
            lineTo(21.5f, 17.75f)
            curveTo(21.5f, 18.993f, 20.493f, 20f, 19.25f, 20f)
            lineTo(12.75f, 20f)
            lineTo(12.75f, 15.67f)
            close()
            moveTo(11.25f, 15.67f)
            lineTo(11.25f, 20f)
            lineTo(4.75f, 20f)
            curveTo(3.507f, 20f, 2.5f, 18.993f, 2.5f, 17.75f)
            lineTo(2.5f, 15.67f)
            close()
            moveTo(12.75f, 8.33f)
            lineTo(12.75f, 4f)
            lineTo(19.25f, 4f)
            curveTo(20.493f, 4f, 21.5f, 5.007f, 21.5f, 6.25f)
            lineTo(21.5f, 8.33f)
            close()
            moveTo(4.75f, 4f)
            lineTo(11.25f, 4f)
            lineTo(11.25f, 8.33f)
            lineTo(2.5f, 8.33f)
            lineTo(2.5f, 6.25f)
            curveTo(2.5f, 5.007f, 3.507f, 4f, 4.75f, 4f)
            close()
            moveTo(10.415f, 10.33f)
            lineTo(13.585f, 10.33f)
            curveTo(14.505f, 10.33f, 15.25f, 11.075f, 15.25f, 11.995f)
            curveTo(15.25f, 12.915f, 14.505f, 13.66f, 13.585f, 13.66f)
            lineTo(10.415f, 13.66f)
            curveTo(9.495f, 13.66f, 8.75f, 12.915f, 8.75f, 11.995f)
            curveTo(8.75f, 11.075f, 9.495f, 10.33f, 10.415f, 10.33f)
            close()
            moveTo(6.75f, 9.83f)
            lineTo(6.75f, 14.17f)
            lineTo(2.5f, 14.17f)
            lineTo(2.5f, 9.83f)
            close()
            moveTo(21.5f, 9.83f)
            lineTo(21.5f, 14.17f)
            lineTo(17.25f, 14.17f)
            lineTo(17.25f, 9.83f)
            close()
            moveTo(15.75f, 13.96f)
            lineTo(15.75f, 14.17f)
            lineTo(8.25f, 14.17f)
            lineTo(8.25f, 13.96f)
            curveTo(8.25f, 14.07f, 8.34f, 14.16f, 8.45f, 14.16f)
            lineTo(15.55f, 14.16f)
            curveTo(15.66f, 14.16f, 15.75f, 14.07f, 15.75f, 13.96f)
            close()
            moveTo(8.45f, 9.83f)
            curveTo(8.34f, 9.83f, 8.25f, 9.92f, 8.25f, 10.03f)
            lineTo(8.25f, 9.83f)
            close()
            moveTo(15.75f, 9.83f)
            lineTo(15.75f, 10.03f)
            curveTo(15.75f, 9.92f, 15.66f, 9.83f, 15.55f, 9.83f)
            close()
          }
        }
        .build()
    return cachedKeepoutFirewall!!
  }

internal var cachedKeepoutFirewall: ImageVector? = null
