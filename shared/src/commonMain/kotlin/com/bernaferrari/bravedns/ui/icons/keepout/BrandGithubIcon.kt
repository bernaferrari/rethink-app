package com.bernaferrari.bravedns.ui.icons.keepout

// Github brand mark, flattened from the former res/drawable/ic_github.xml.
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
internal val brandGithub: ImageVector
  get() {
    if (_brandGithub != null) {
      return _brandGithub!!
    }
    _brandGithub =
      ImageVector.Builder(
          name = "github",
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
            moveTo(15f, 22f)
            lineTo(15f, 18.13f)
            quadTo(15f, 18.091f, 15.003f, 18.052f)
            quadTo(15.087f, 16.985f, 14.342f, 16.216f)
            quadTo(13.646f, 15.498f, 14.364f, 14.802f)
            quadTo(14.609f, 14.564f, 14.949f, 14.526f)
            quadTo(20.5f, 13.907f, 20.5f, 8.52f)
            quadTo(20.5f, 6.745f, 19.276f, 5.46f)
            quadTo(18.844f, 5.006f, 19.063f, 4.42f)
            quadTo(19.526f, 3.179f, 19.189f, 1.957f)
            quadTo(18.341f, 2.114f, 16.557f, 3.311f)
            quadTo(16.178f, 3.564f, 15.738f, 3.445f)
            quadTo(12.5f, 2.568f, 9.262f, 3.445f)
            quadTo(8.822f, 3.564f, 8.443f, 3.311f)
            quadTo(6.659f, 2.114f, 5.811f, 1.957f)
            quadTo(5.474f, 3.179f, 5.937f, 4.42f)
            quadTo(6.156f, 5.006f, 5.724f, 5.46f)
            quadTo(4.49f, 6.755f, 4.5f, 8.545f)
            quadTo(4.5f, 13.867f, 10.063f, 14.558f)
            quadTo(10.559f, 14.619f, 10.777f, 14.898f)
            quadTo(10.994f, 15.177f, 10.932f, 15.673f)
            quadTo(10.891f, 16.005f, 10.659f, 16.245f)
            quadTo(9.923f, 17.005f, 9.998f, 18.06f)
            quadTo(10f, 18.095f, 10f, 18.13f)
            lineTo(10f, 18.882f)
            quadTo(10.048f, 19.132f, 10f, 19.33f)
            lineTo(10f, 22f)
            quadTo(10f, 23f, 9f, 23f)
            quadTo(8f, 23f, 8f, 22f)
            lineTo(8f, 20.234f)
            quadTo(4.905f, 20.615f, 2.826f, 17.976f)
            quadTo(2.101f, 17.056f, 1.757f, 16.97f)
            quadTo(1.272f, 16.849f, 1.09f, 16.546f)
            quadTo(0.909f, 16.243f, 1.03f, 15.757f)
            quadTo(1.272f, 14.787f, 2.243f, 15.03f)
            quadTo(3.249f, 15.282f, 4.397f, 16.738f)
            quadTo(5.825f, 18.551f, 8f, 18.207f)
            lineTo(8f, 18.163f)
            quadTo(7.935f, 17.139f, 8.287f, 16.266f)
            quadTo(2.5f, 14.85f, 2.5f, 8.555f)
            quadTo(2.488f, 6.269f, 3.875f, 4.538f)
            quadTo(3.335f, 2.556f, 4.171f, 0.606f)
            quadTo(4.356f, 0.175f, 4.806f, 0.041f)
            quadTo(6.336f, -0.413f, 9.179f, 1.402f)
            quadTo(12.5f, 0.608f, 15.821f, 1.402f)
            quadTo(18.664f, -0.413f, 20.194f, 0.041f)
            quadTo(20.644f, 0.175f, 20.829f, 0.606f)
            quadTo(21.665f, 2.556f, 21.125f, 4.538f)
            quadTo(22.5f, 6.254f, 22.5f, 8.52f)
            quadTo(22.5f, 14.893f, 16.721f, 16.251f)
            quadTo(17.073f, 17.132f, 17f, 18.167f)
            lineTo(17f, 22f)
            quadTo(17f, 23f, 16f, 23f)
            quadTo(15f, 23f, 15f, 22f)
            close()
          }
        }
        .build()
    return _brandGithub!!
  }

internal var _brandGithub: ImageVector? = null
