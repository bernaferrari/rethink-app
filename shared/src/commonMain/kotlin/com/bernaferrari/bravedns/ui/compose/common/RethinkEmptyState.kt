@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.common

import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

private val EmptyStateIconWell = 72.dp

@Composable
fun RethinkEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SharedDimensions.spacingLg,
                vertical = SharedDimensions.spacingXl,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            // Expressive shape echoes the Home beacon; sized as an illustration, not a list icon.
            Surface(
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(EmptyStateIconWell),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(SharedDimensions.iconSizeLg),
                    )
                }
            }
            Spacer(Modifier.height(SharedDimensions.spacingLg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SharedDimensions.spacingSm),
            )
        }
        if (ctaLabel != null && onCtaClick != null) {
            Spacer(Modifier.height(SharedDimensions.spacingLg))
            FilledTonalButton(onClick = onCtaClick, shapes = ButtonDefaults.shapes()) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
fun RethinkLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SharedDimensions.spacingLg,
                vertical = SharedDimensions.spacingXl,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator()
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SharedDimensions.spacingMd),
            )
        }
    }
}
