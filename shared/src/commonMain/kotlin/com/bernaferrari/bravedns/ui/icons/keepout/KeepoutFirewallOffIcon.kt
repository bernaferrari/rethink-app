package com.bernaferrari.bravedns.ui.icons.keepout

// Firewall off for an app (bypass DNS & firewall): the wall with Material's off-slash.
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
internal val keepoutFirewallOff: ImageVector
  get() {
    if (cachedKeepoutFirewallOff != null) {
      return cachedKeepoutFirewallOff!!
    }
    cachedKeepoutFirewallOff =
      ImageVector.Builder(
          name = "firewall_off",
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
            moveTo(4.342f, 1.929f)
            lineTo(21.342f, 20.729f)
            quadTo(22.012f, 21.471f, 21.271f, 22.142f)
            quadTo(20.529f, 22.812f, 19.858f, 22.071f)
            lineTo(2.858f, 3.271f)
            quadTo(2.188f, 2.529f, 2.929f, 1.858f)
            quadTo(3.671f, 1.188f, 4.342f, 1.929f)
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
            moveTo(6.75f, 9.83f)
            lineTo(6.75f, 14.17f)
            lineTo(2.5f, 14.17f)
            lineTo(2.5f, 9.83f)
            close()
            moveTo(21.5f, 9.83f)
            lineTo(21.5f, 14.17f)
            lineTo(17.433f, 14.17f)
            lineTo(17.25f, 13.968f)
            lineTo(17.25f, 9.83f)
            close()
            moveTo(12.75f, 16.446f)
            lineTo(15.964f, 20f)
            lineTo(12.75f, 20f)
            close()
            moveTo(11.25f, 7.333f)
            lineTo(8.236f, 4f)
            lineTo(11.25f, 4f)
            close()
            moveTo(2.697f, 5.329f)
            lineTo(5.411f, 8.33f)
            lineTo(2.5f, 8.33f)
            lineTo(2.5f, 6.25f)
            curveTo(2.5f, 5.922f, 2.57f, 5.61f, 2.697f, 5.329f)
            close()
            moveTo(21.5f, 15.67f)
            lineTo(21.5f, 17.75f)
            curveTo(21.5f, 18.02f, 21.452f, 18.279f, 21.365f, 18.519f)
            lineTo(18.789f, 15.67f)
            close()
            moveTo(8.75f, 12.023f)
            lineTo(10.22f, 13.649f)
            curveTo(9.402f, 13.553f, 8.764f, 12.864f, 8.75f, 12.023f)
            close()
            moveTo(15.23f, 11.734f)
            lineTo(14.01f, 10.385f)
            curveTo(14.641f, 10.551f, 15.126f, 11.079f, 15.23f, 11.734f)
            close()
            moveTo(10.683f, 14.16f)
            lineTo(10.692f, 14.17f)
            lineTo(8.25f, 14.17f)
            lineTo(8.25f, 13.96f)
            curveTo(8.25f, 14.07f, 8.34f, 14.16f, 8.45f, 14.16f)
            close()
            moveTo(15.75f, 9.83f)
            lineTo(15.75f, 10.03f)
            curveTo(15.75f, 9.92f, 15.66f, 9.83f, 15.55f, 9.83f)
            close()
          }
        }
        .build()
    return cachedKeepoutFirewallOff!!
  }

internal var cachedKeepoutFirewallOff: ImageVector? = null
