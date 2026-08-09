/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Portable home dashboard shells — string/state in, no Android resources.
 * App wires real ViewModels; wasmJs web demo uses static/demo values.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion

@Composable
fun StartStopButtonShared(
    isActive: Boolean,
    startLabel: String,
    stopLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !motion.reducedMotion) 0.97f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "buttonScale",
    )
    val containerColor =
        if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor =
        if (isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    Button(
        onClick = onClick,
        modifier = modifier.height(SharedDimensions.heroButtonHeight).scale(scale),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = if (isActive) stopLabel else startLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StatCardShared(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SharedDimensions.spacingXs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
fun HomeDashboardShared(
    isVpnActive: Boolean,
    statusLine: String,
    startLabel: String,
    stopLabel: String,
    onToggleVpn: () -> Unit,
    statBlocked: String,
    statBlockedLabel: String,
    statQueries: String,
    statQueriesLabel: String,
    statApps: String,
    statAppsLabel: String,
    banner: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SharedDimensions.spacingXl),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Text(
            text = statusLine,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (banner != null) {
            Text(
                text = banner,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StartStopButtonShared(
            isActive = isVpnActive,
            startLabel = startLabel,
            stopLabel = stopLabel,
            onClick = onToggleVpn,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            StatCardShared(
                value = statBlocked,
                label = statBlockedLabel,
                modifier = Modifier.weight(1f),
            )
            StatCardShared(
                value = statQueries,
                label = statQueriesLabel,
                modifier = Modifier.weight(1f),
            )
            StatCardShared(
                value = statApps,
                label = statAppsLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
