/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * The welcome flow deliberately has one destination. It introduces the product without making a
 * first-run user complete a carousel before they can protect their connection.
 */
package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.rethinkGroupedListShape

data class RethinkWelcomeFeature(
    val label: String,
    val icon: ImageVector,
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
                    .widthIn(max = 560.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SharedDimensions.buttonHeightLg),
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
        Surface(
            modifier = Modifier.size(104.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = content.heroIcon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineLarge,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
    ) {
        features.forEachIndexed { index, feature ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rethinkGroupedListShape(cardPositionFor(index, features.lastIndex)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = SharedDimensions.spacingLg,
                        vertical = SharedDimensions.spacingMd,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Text(
                        text = feature.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
