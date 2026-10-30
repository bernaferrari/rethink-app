package com.bernaferrari.bravedns.ui.icons.keepout

// On-device blocklists: list lines with the blocked sign (do_not_disturb_on) as a badge.
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
internal val keepoutBlocklists: ImageVector
  get() {
    if (cachedKeepoutBlocklists != null) {
      return cachedKeepoutBlocklists!!
    }
    cachedKeepoutBlocklists =
      ImageVector.Builder(
          name = "blocklists",
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
            moveTo(18.44f, 22.14f)
            quadTo(17.571f, 22.14f, 16.802f, 21.808f)
            quadTo(16.038f, 21.481f, 15.471f, 20.914f)
            quadTo(14.904f, 20.347f, 14.572f, 19.578f)
            quadTo(14.24f, 18.809f, 14.24f, 17.94f)
            quadTo(14.24f, 17.066f, 14.572f, 16.302f)
            quadTo(14.904f, 15.533f, 15.471f, 14.971f)
            quadTo(16.038f, 14.399f, 16.802f, 14.072f)
            quadTo(17.571f, 13.74f, 18.44f, 13.74f)
            quadTo(19.309f, 13.74f, 20.078f, 14.072f)
            quadTo(20.847f, 14.404f, 21.409f, 14.971f)
            quadTo(21.976f, 15.538f, 22.308f, 16.302f)
            quadTo(22.64f, 17.066f, 22.64f, 17.94f)
            quadTo(22.64f, 18.814f, 22.308f, 19.578f)
            quadTo(21.976f, 20.342f, 21.409f, 20.909f)
            quadTo(20.842f, 21.476f, 20.078f, 21.808f)
            quadTo(19.314f, 22.14f, 18.44f, 22.14f)
            close()
            moveTo(4f, 9.5f)
            lineTo(20f, 9.5f)
            quadTo(21f, 9.5f, 21f, 10.5f)
            quadTo(21f, 11.5f, 20f, 11.5f)
            lineTo(4f, 11.5f)
            quadTo(3f, 11.5f, 3f, 10.5f)
            quadTo(3f, 9.5f, 4f, 9.5f)
            close()
            moveTo(4f, 4.5f)
            lineTo(20f, 4.5f)
            quadTo(21f, 4.5f, 21f, 5.5f)
            quadTo(21f, 6.5f, 20f, 6.5f)
            lineTo(4f, 6.5f)
            quadTo(3f, 6.5f, 3f, 5.5f)
            quadTo(3f, 4.5f, 4f, 4.5f)
            close()
            moveTo(4f, 14.5f)
            lineTo(12f, 14.5f)
            quadTo(13f, 14.5f, 13f, 15.5f)
            quadTo(13f, 16.5f, 12f, 16.5f)
            lineTo(4f, 16.5f)
            quadTo(3f, 16.5f, 3f, 15.5f)
            quadTo(3f, 14.5f, 4f, 14.5f)
            close()
            moveTo(4f, 19.5f)
            lineTo(11f, 19.5f)
            quadTo(12f, 19.5f, 12f, 20.5f)
            quadTo(12f, 21.5f, 11f, 21.5f)
            lineTo(4f, 21.5f)
            quadTo(3f, 21.5f, 3f, 20.5f)
            quadTo(3f, 19.5f, 4f, 19.5f)
            close()
            moveTo(16.76f, 18.36f)
            lineTo(20.12f, 18.36f)
            quadTo(20.301f, 18.36f, 20.418f, 18.238f)
            quadTo(20.54f, 18.121f, 20.54f, 17.94f)
            quadTo(20.54f, 17.759f, 20.418f, 17.642f)
            quadTo(20.296f, 17.524f, 20.12f, 17.52f)
            lineTo(16.76f, 17.52f)
            quadTo(16.584f, 17.52f, 16.462f, 17.642f)
            quadTo(16.34f, 17.764f, 16.34f, 17.94f)
            quadTo(16.34f, 18.116f, 16.462f, 18.238f)
            quadTo(16.584f, 18.36f, 16.76f, 18.36f)
            close()
          }
        }
        .build()
    return cachedKeepoutBlocklists!!
  }

internal var cachedKeepoutBlocklists: ImageVector? = null
