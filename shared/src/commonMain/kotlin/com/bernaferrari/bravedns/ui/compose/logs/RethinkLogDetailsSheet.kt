/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkLogDetailEntry(
    val label: String,
    val value: String,
    val isError: Boolean = false,
)

/** Target-neutral log-detail sheet. Hosts provide only the app artwork, if available. */
@Composable
fun RethinkLogDetailsSheet(
    title: String,
    details: List<RethinkLogDetailEntry>,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appIcon: (@Composable () -> Unit)? = null,
) {
    RethinkModalBottomSheet(onDismissRequest = onDismiss, includeBottomSpacer = true) { dismissSheet ->
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            RethinkSheetHeader(title = title, leading = appIcon)
            details.forEach { entry ->
                RethinkKeyValueRow(
                    label = entry.label,
                    value = entry.value,
                    modifier = Modifier.padding(vertical = SharedDimensions.spacingXs),
                    valueColor = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    valueFontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(SharedDimensions.spacingXl))
            TextButton(onClick = dismissSheet, modifier = Modifier.align(Alignment.End)) {
                Text(text = dismissLabel)
            }
        }
    }
}
