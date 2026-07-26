/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkGuidedTourStep(val title: String, val description: String)

/** Shared touch-first onboarding tour. Platform hosts only supply localized copy. */
@Composable
fun RethinkHomeGuidedTour(
    steps: List<RethinkGuidedTourStep>,
    stepTitle: (index: Int, total: Int, title: String) -> String,
    next: String,
    done: String,
    skip: String,
    onComplete: () -> Unit,
) {
    if (steps.isEmpty()) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val current = steps[index]
    RethinkConfirmDialog(
        onDismissRequest = onComplete,
        title = stepTitle(index + 1, steps.size, current.title),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
                Text(current.description)
            }
        },
        confirmText = if (index == steps.lastIndex) done else next,
        onConfirm = { if (index == steps.lastIndex) onComplete() else index++ },
        dismissText = skip,
        onDismiss = onComplete,
    )
}
