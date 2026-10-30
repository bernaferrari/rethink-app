package com.bernaferrari.bravedns.ui.icons.keepout

// Mastodon brand mark, flattened from the former res/drawable/ic_mastodon.xml.
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
internal val brandMastodon: ImageVector
  get() {
    if (cachedBrandMastodon != null) {
      return cachedBrandMastodon!!
    }
    cachedBrandMastodon =
      ImageVector.Builder(
          name = "mastodon",
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
            moveTo(12.038f, 2f)
            curveTo(14.524f, 2.02f, 16.913f, 2.311f, 18.305f, 2.95f)
            curveTo(18.349f, 2.969f, 21.376f, 4.282f, 21.378f, 8.819f)
            curveTo(21.379f, 8.93f, 21.401f, 11.906f, 20.997f, 13.984f)
            curveTo(20.869f, 14.645f, 20.41f, 15.351f, 19.685f, 15.959f)
            curveTo(18.73f, 16.759f, 17.306f, 17.41f, 15.76f, 17.595f)
            curveTo(14.51f, 17.744f, 13.28f, 17.878f, 11.969f, 17.817f)
            curveTo(10.866f, 17.767f, 9.884f, 17.634f, 9.185f, 17.515f)
            curveTo(9.387f, 18.04f, 9.791f, 18.3f, 10.28f, 18.434f)
            curveTo(10.823f, 18.581f, 11.444f, 18.605f, 12.052f, 18.626f)
            curveTo(13.604f, 18.679f, 14.984f, 18.245f, 14.984f, 18.245f)
            curveTo(15.206f, 18.175f, 15.448f, 18.212f, 15.639f, 18.345f)
            curveTo(15.83f, 18.478f, 15.949f, 18.693f, 15.959f, 18.926f)
            lineTo(16.029f, 20.449f)
            curveTo(16.041f, 20.737f, 15.888f, 21.007f, 15.634f, 21.144f)
            curveTo(15.634f, 21.144f, 14.346f, 21.854f, 12.045f, 21.981f)
            lineTo(12.044f, 21.981f)
            curveTo(10.824f, 22.048f, 9.309f, 21.952f, 7.545f, 21.485f)
            curveTo(5.474f, 20.936f, 4.249f, 19.571f, 3.54f, 17.77f)
            curveTo(2.889f, 16.118f, 2.689f, 14.08f, 2.639f, 12.025f)
            curveTo(2.618f, 11.162f, 2.621f, 10.334f, 2.625f, 9.589f)
            curveTo(2.626f, 9.323f, 2.628f, 9.067f, 2.628f, 8.824f)
            curveTo(2.628f, 4.298f, 5.637f, 2.976f, 5.7f, 2.95f)
            curveTo(7.095f, 2.311f, 9.483f, 2.02f, 11.966f, 2f)
            close()
            moveTo(19.525f, 13.698f)
            lineTo(19.536f, 13.639f)
            curveTo(19.924f, 11.645f, 19.878f, 8.833f, 19.878f, 8.833f)
            lineTo(19.878f, 8.824f)
            curveTo(19.878f, 5.37f, 17.691f, 4.319f, 17.691f, 4.319f)
            curveTo(17.69f, 4.318f, 17.688f, 4.316f, 17.685f, 4.316f)
            curveTo(16.429f, 3.739f, 14.269f, 3.519f, 12.028f, 3.5f)
            lineTo(11.976f, 3.5f)
            curveTo(9.735f, 3.519f, 7.576f, 3.739f, 6.32f, 4.316f)
            lineTo(6.312f, 4.319f)
            curveTo(6.312f, 4.319f, 4.128f, 5.37f, 4.128f, 8.824f)
            curveTo(4.128f, 9.067f, 4.126f, 9.323f, 4.125f, 9.59f)
            curveTo(4.121f, 10.324f, 4.117f, 11.138f, 4.139f, 11.988f)
            curveTo(4.184f, 13.858f, 4.344f, 15.716f, 4.935f, 17.219f)
            curveTo(5.469f, 18.574f, 6.371f, 19.621f, 7.929f, 20.034f)
            curveTo(9.51f, 20.452f, 10.868f, 20.542f, 11.961f, 20.483f)
            curveTo(13.163f, 20.416f, 14.031f, 20.169f, 14.506f, 19.996f)
            lineTo(14.501f, 19.904f)
            curveTo(13.863f, 20.031f, 12.96f, 20.157f, 12.001f, 20.125f)
            curveTo(9.965f, 20.055f, 7.915f, 19.746f, 7.584f, 17.247f)
            curveTo(7.554f, 17.024f, 7.539f, 16.798f, 7.54f, 16.573f)
            curveTo(7.54f, 16.342f, 7.646f, 16.125f, 7.827f, 15.983f)
            curveTo(8.009f, 15.84f, 8.245f, 15.789f, 8.469f, 15.844f)
            curveTo(8.469f, 15.844f, 10.042f, 16.228f, 12.038f, 16.319f)
            curveTo(13.264f, 16.375f, 14.414f, 16.244f, 15.583f, 16.105f)
            curveTo(16.817f, 15.958f, 17.959f, 15.448f, 18.721f, 14.809f)
            curveTo(19.141f, 14.458f, 19.45f, 14.08f, 19.525f, 13.696f)
            close()
            moveTo(12.75f, 9.167f)
            lineTo(12.75f, 11.705f)
            curveTo(12.75f, 12.119f, 12.414f, 12.455f, 12f, 12.455f)
            curveTo(11.586f, 12.455f, 11.25f, 12.119f, 11.25f, 11.705f)
            lineTo(11.25f, 9.176f)
            curveTo(11.159f, 8.858f, 10.717f, 7.628f, 9.386f, 7.628f)
            curveTo(8.988f, 7.628f, 8.711f, 7.715f, 8.53f, 7.871f)
            curveTo(8.135f, 8.211f, 8.198f, 8.784f, 8.198f, 8.784f)
            curveTo(8.199f, 8.805f, 8.2f, 8.826f, 8.2f, 8.847f)
            lineTo(8.2f, 13.39f)
            curveTo(8.2f, 13.804f, 7.864f, 14.14f, 7.45f, 14.14f)
            curveTo(7.036f, 14.14f, 6.7f, 13.804f, 6.7f, 13.39f)
            lineTo(6.7f, 8.854f)
            curveTo(6.694f, 8.589f, 6.718f, 7.454f, 7.551f, 6.735f)
            curveTo(7.945f, 6.395f, 8.521f, 6.128f, 9.386f, 6.128f)
            curveTo(10.656f, 6.128f, 11.475f, 6.755f, 11.984f, 7.414f)
            curveTo(12.48f, 6.727f, 13.284f, 6.066f, 14.534f, 6.066f)
            curveTo(17.266f, 6.066f, 17.351f, 9.06f, 17.351f, 9.06f)
            lineTo(17.351f, 13.361f)
            curveTo(17.351f, 13.775f, 17.015f, 14.111f, 16.601f, 14.111f)
            curveTo(16.188f, 14.111f, 15.851f, 13.775f, 15.851f, 13.361f)
            lineTo(15.851f, 9.084f)
            curveTo(15.851f, 9.084f, 15.869f, 7.566f, 14.534f, 7.566f)
            curveTo(13.249f, 7.566f, 12.835f, 8.842f, 12.75f, 9.167f)
            close()
            moveTo(6.7f, 8.854f)
            curveTo(6.701f, 8.891f, 6.702f, 8.911f, 6.702f, 8.911f)
            lineTo(6.7f, 8.854f)
            close()
          }
        }
        .build()
    return cachedBrandMastodon!!
  }

internal var cachedBrandMastodon: ImageVector? = null
