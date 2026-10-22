@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * The welcome flow deliberately has one destination. It introduces the product without making a
 * first-run user complete a carousel before they can protect their connection.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.ui.text.style.LineBreak
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

/** A narrow single column reads as a composed first-run card rather than a stretched form. */
private val WelcomeContentMaxWidth = 560.dp

data class RethinkWelcomeFeature(
    val label: String,
    val icon: ImageVector,
    /** One line on the value this feature delivers; onboarding should explain, not just list. */
    val description: String? = null,
)

data class RethinkWelcomeContent(
    val title: String,
    val description: String,
    val heroIcon: ImageVector,
    val features: List<RethinkWelcomeFeature> = emptyList(),
)

@Composable
fun RethinkWelcomeScreen(
    content: RethinkWelcomeContent,
    ctaLabel: String,
    onFinish: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = WelcomeContentMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = SharedDimensions.spacingXl,
                        vertical = SharedDimensions.spacing3xl,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                // A short first-run flow should feel composed, not pinned to the top edge. The
                // scroll container still keeps it usable on small or landscape viewports.
                verticalArrangement = Arrangement.spacedBy(
                    space = SharedDimensions.spacingXl,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                RethinkWelcomeHero(content)

                if (content.features.isNotEmpty()) {
                    RethinkWelcomeFeatures(content.features)
                }

                Button(
                    onClick = onFinish,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SharedDimensions.buttonHeight),
                ) {
                    Text(ctaLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RethinkWelcomeHero(content: RethinkWelcomeContent) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        // The same 12-sided cookie Home shows when protected: first run introduces the mark the
        // user will see every day.
        Surface(
            modifier = Modifier.size(SharedDimensions.iconSizeXl * 2),
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = content.heroIcon,
                    contentDescription = null,
                    modifier = Modifier.size(SharedDimensions.iconSizeXl),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Text(
                // Keep hyphenated names ("Re-Rethink") whole; balance the heading's lines.
                text = content.title.replace('-', '\u2011'),
                style = MaterialTheme.typography.headlineLarge.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RethinkWelcomeFeatures(features: List<RethinkWelcomeFeature>) {
    RethinkListGroup {
        features.forEachIndexed { index, feature ->
            RethinkListItem(
                headline = feature.label,
                supporting = feature.description,
                leadingIcon = feature.icon,
                leadingIconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                position = cardPositionFor(index, features.lastIndex),
            )
        }
    }
}
