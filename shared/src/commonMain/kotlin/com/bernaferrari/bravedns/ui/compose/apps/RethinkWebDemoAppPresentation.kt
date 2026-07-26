/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.apps

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Visual metadata for the stable web-demo app catalog.
 *
 * This deliberately lives beside [RethinkWebDemoAppCatalog], rather than in generic installed-app
 * metadata: Android resolves real icons from the package manager, while the browser demo needs a
 * portable, deterministic representation.
 */
data class RethinkWebDemoAppPresentation(
    val backgroundColor: Color,
    val icon: ImageVector? = null,
    val letter: String? = null,
    val iconTint: Color = Color.White,
)

object RethinkWebDemoAppPresentations {
    private val byPackageName = mapOf(
        "com.demo.browser" to RethinkWebDemoAppPresentation(Color(0xFF4285F4), MaterialSymbols.Filled.Language),
        "com.demo.calendar" to RethinkWebDemoAppPresentation(Color(0xFF4285F4), MaterialSymbols.Filled.CalendarMonth),
        "com.demo.github" to RethinkWebDemoAppPresentation(Color(0xFF24292F), MaterialSymbols.Filled.Code),
        "com.demo.messenger" to RethinkWebDemoAppPresentation(Color(0xFF25D366), MaterialSymbols.AutoMirrored.Filled.Chat),
        "com.demo.reader" to RethinkWebDemoAppPresentation(Color(0xFF7E57C2), MaterialSymbols.Filled.AutoStories),
        "android.systemui" to RethinkWebDemoAppPresentation(Color(0xFF5F6368), MaterialSymbols.Filled.Settings),
        "com.demo.weather" to RethinkWebDemoAppPresentation(Color(0xFFFFB300), MaterialSymbols.Filled.WbSunny),
        "com.google.android.gm" to RethinkWebDemoAppPresentation(Color(0xFFEA4335), MaterialSymbols.Filled.Email),
        "com.spotify.music" to RethinkWebDemoAppPresentation(Color(0xFF1DB954), MaterialSymbols.Filled.MusicNote),
        "com.instagram.android" to RethinkWebDemoAppPresentation(Color(0xFFE1306C), MaterialSymbols.Filled.CameraAlt),
        "com.google.android.youtube" to RethinkWebDemoAppPresentation(Color(0xFFFF0000), MaterialSymbols.Filled.PlayArrow),
        "com.twitter.android" to RethinkWebDemoAppPresentation(Color.Black, letter = "𝕏"),
        "com.facebook.katana" to RethinkWebDemoAppPresentation(Color(0xFF1877F2), MaterialSymbols.Filled.Group),
        "com.google.android.apps.maps" to RethinkWebDemoAppPresentation(Color(0xFF34A853), MaterialSymbols.Filled.Map),
        "com.amazon.mShop.android.shopping" to RethinkWebDemoAppPresentation(Color(0xFFFF9900), MaterialSymbols.Filled.ShoppingCart),
        "com.netflix.mediaclient" to RethinkWebDemoAppPresentation(Color(0xFFE50914), MaterialSymbols.Filled.Movie),
        "com.discord" to RethinkWebDemoAppPresentation(Color(0xFF5865F2), MaterialSymbols.Filled.Headset),
        "com.android.vending" to RethinkWebDemoAppPresentation(Color(0xFF01875F), MaterialSymbols.Filled.Shop),
        "com.google.android.gms" to RethinkWebDemoAppPresentation(Color(0xFF5F6368), MaterialSymbols.Filled.Apps),
        "com.reddit.frontpage" to RethinkWebDemoAppPresentation(Color(0xFFFF4500), MaterialSymbols.Filled.Forum),
        "org.telegram.messenger" to RethinkWebDemoAppPresentation(Color(0xFF2AABEE), MaterialSymbols.AutoMirrored.Filled.Send),
        "com.zhiliaoapp.musically" to RethinkWebDemoAppPresentation(Color.Black, MaterialSymbols.Filled.MusicNote, iconTint = Color(0xFFFE2C55)),
        "com.Slack" to RethinkWebDemoAppPresentation(Color(0xFF4A154B), MaterialSymbols.Filled.Work),
        "com.linkedin.android" to RethinkWebDemoAppPresentation(Color(0xFF0A66C2), MaterialSymbols.Filled.BusinessCenter),
        "org.thoughtcrime.securesms" to RethinkWebDemoAppPresentation(Color(0xFF3A76F0), MaterialSymbols.Filled.Lock),
        "com.google.android.apps.photos" to RethinkWebDemoAppPresentation(Color(0xFFFBBC04), MaterialSymbols.Filled.PhotoLibrary, iconTint = Color(0xFF202124)),
        "com.google.android.apps.docs" to RethinkWebDemoAppPresentation(Color(0xFF0F9D58), MaterialSymbols.Filled.Cloud),
        "com.ubercab" to RethinkWebDemoAppPresentation(Color.Black, MaterialSymbols.Filled.LocalTaxi),
        "com.rethinkdns.app" to RethinkWebDemoAppPresentation(Color(0xFF804136), MaterialSymbols.Filled.Shield),
    )
    private val packageByLabel = buildMap {
        RethinkWebDemoAppCatalog.entries.forEach { put(it.appName.lowercase(), it.packageName) }
        put("mail", "com.google.android.gm")
    }
    private val fallbackPalette = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
        Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFF8D6E63),
    )

    fun packageNameFor(label: String): String? = packageByLabel[label.lowercase()]

    fun presentationFor(packageName: String?, label: String): RethinkWebDemoAppPresentation {
        val resolvedPackage = packageName ?: packageNameFor(label)
        return resolvedPackage?.let(byPackageName::get)
            ?: fallbackPresentation(label)
    }

    private fun fallbackPresentation(label: String): RethinkWebDemoAppPresentation {
        val color = fallbackPalette[label.fold(0) { total, character -> total + character.code } % fallbackPalette.size]
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        return RethinkWebDemoAppPresentation(color, letter = letter)
    }
}

/** The one portable demo-app icon renderer used by lists, statistics, and log filters. */
@Composable
fun RethinkWebDemoAppIcon(
    packageName: String?,
    appName: String,
    modifier: Modifier = Modifier,
) {
    val visual = RethinkWebDemoAppPresentations.presentationFor(packageName, appName)
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(11.dp),
        color = visual.backgroundColor,
        contentColor = visual.iconTint,
    ) {
        visual.icon?.let { icon ->
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp))
        } ?: Text(
            text = visual.letter.orEmpty(),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}
