/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Portable home dashboard shells — string/state in, no Android resources.
 * App wires real ViewModels; wasmJs web demo uses static/demo values.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

@Composable
fun StartStopButtonShared(
    isActive: Boolean,
    startLabel: String,
    stopLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(SharedDimensions.heroButtonHeight),
        shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) scheme.onSurface else scheme.primary,
            contentColor = if (isActive) scheme.surface else scheme.onPrimary,
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
private fun QuietMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietMetric(statBlocked, statBlockedLabel, Modifier.weight(1f))
            QuietMetric(statQueries, statQueriesLabel, Modifier.weight(1f))
            QuietMetric(statApps, statAppsLabel, Modifier.weight(1f))
        }
    }
}
