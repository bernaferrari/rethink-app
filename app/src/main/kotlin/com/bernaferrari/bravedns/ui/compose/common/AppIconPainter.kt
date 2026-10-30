/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.ui.compose.common

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.core.graphics.drawable.toBitmap
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide cache of decoded launcher icons, keyed by package name and bounded by bitmap
 * bytes. Only real app icons are cached; the themed default icon is never stored, so a theme
 * switch can't leave a stale placeholder behind.
 */
private object AppIconCache {
    private const val BYTES_PER_KB = 1024
    private const val MAX_SIZE_KB = 16 * 1024

    private val cache =
        object : LruCache<String, ImageBitmap>(MAX_SIZE_KB) {
            override fun sizeOf(key: String, value: ImageBitmap): Int =
                (value.width * value.height * 4 / BYTES_PER_KB).coerceAtLeast(1)
        }

    fun get(packageName: String): ImageBitmap? = if (packageName.isBlank()) null else cache.get(packageName)

    /** Loads (off the main thread) and caches the icon; null when the package has none. */
    suspend fun load(context: Context, packageName: String, appName: String?): ImageBitmap? {
        get(packageName)?.let { return it }
        if (packageName.isBlank() || Utilities.isNonApp(packageName) || appName == Constants.UNKNOWN_APP) {
            return null
        }
        val bitmap =
            withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
        if (bitmap != null) cache.put(packageName, bitmap)
        return bitmap
    }
}

/**
 * Painter for an installed app's launcher icon, decoded once and served from [AppIconCache].
 * With [fallbackToDefault], the themed default icon shows only once the lookup finished without
 * an icon; while it loads this returns null, so a real icon never flashes in after a robot.
 */
@Composable
fun rememberAppIconPainter(
    packageName: String,
    appName: String? = null,
    fallbackToDefault: Boolean = false,
): Painter? = rememberResolvedAppIconPainter(packageName, appName, fallbackToDefault)

/** Like [rememberAppIconPainter], resolving the package from a (possibly multi-user) [uid]. */
@Composable
fun rememberAppIconPainter(uid: Int, fallbackToDefault: Boolean = false): Painter? {
    val context = LocalContext.current
    // null while resolving; "" once resolved to no package.
    val packageName by produceState<String?>(initialValue = null, uid) {
        value =
            withContext(Dispatchers.IO) {
                val candidateUids = listOf(uid, FirewallManager.appId(uid, mainUserOnly = true)).distinct()
                candidateUids.firstNotNullOfOrNull { FirewallManager.getPackageNameByUid(it) }
                    ?: candidateUids.firstNotNullOfOrNull {
                        Utilities.getPackageInfoForUid(context, it)?.firstOrNull()
                    }
            }.orEmpty()
    }
    return rememberResolvedAppIconPainter(packageName, appName = null, fallbackToDefault = fallbackToDefault)
}

/** [packageName] null means the package is still being resolved: nothing to show yet. */
@Composable
private fun rememberResolvedAppIconPainter(
    packageName: String?,
    appName: String?,
    fallbackToDefault: Boolean,
): Painter? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(packageName?.let(AppIconCache::get)) }
    var settled by remember(packageName) { mutableStateOf(bitmap != null) }
    LaunchedEffect(packageName, appName) {
        if (packageName == null) return@LaunchedEffect
        if (bitmap == null) bitmap = AppIconCache.load(context, packageName, appName)
        settled = true
    }
    val iconPainter = remember(bitmap) { bitmap?.let(::BitmapPainter) }
    val fallback = if (fallbackToDefault && iconPainter == null && settled) rememberDrawablePainter(Utilities.getDefaultIcon(context)) else null
    return iconPainter ?: fallback
}

/**
 * The one app icon: the cached launcher icon, or the default app icon when the package has none.
 * While the icon loads the slot stays empty (same size), so rows don't jump or flash a robot. No backplate — launcher icons carry their own shape. List rows use the
 * default [SharedDimensions.iconContainerMd]; pass [Dp.Unspecified] to fill a slot the shared
 * layout already sized.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    appName: String? = null,
    size: Dp = SharedDimensions.iconContainerMd,
) {
    AppIconImage(rememberAppIconPainter(packageName, appName, fallbackToDefault = true), modifier, size)
}

/** [AppIcon] for an app known only by its (possibly multi-user) [uid]. */
@Composable
fun AppIcon(
    uid: Int,
    modifier: Modifier = Modifier,
    size: Dp = SharedDimensions.iconContainerMd,
) {
    AppIconImage(rememberAppIconPainter(uid, fallbackToDefault = true), modifier, size)
}

@Composable
private fun AppIconImage(painter: Painter?, modifier: Modifier, size: Dp) {
    if (painter != null) Image(painter, null, modifier.iconSize(size)) else Spacer(modifier.iconSize(size))
}

private fun Modifier.iconSize(size: Dp): Modifier = if (size.isSpecified) size(size) else fillMaxSize()
