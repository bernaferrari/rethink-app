package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R

/** A touch-first Compose replacement for the legacy view-ID spotlight tour. */
@Composable
fun HomeGuidedTour(onComplete: () -> Unit) {
    val steps = listOf(
        stringResource(R.string.home_tour_protection_title) to stringResource(R.string.home_tour_protection_desc),
        stringResource(R.string.home_tour_dns_title) to stringResource(R.string.home_tour_dns_desc),
        stringResource(R.string.home_tour_firewall_title) to stringResource(R.string.home_tour_firewall_desc),
        stringResource(R.string.home_tour_proxy_title) to stringResource(R.string.home_tour_proxy_desc),
        stringResource(R.string.home_tour_logs_title) to stringResource(R.string.home_tour_logs_desc),
        stringResource(R.string.home_tour_plus_title) to stringResource(R.string.home_tour_plus_desc),
    )
    var index by remember { mutableIntStateOf(0) }
    val (title, description) = steps[index]
    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text(stringResource(R.string.home_tour_step, index + 1, steps.size, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
                Text(description)
            }
        },
        confirmButton = { TextButton(onClick = { if (index == steps.lastIndex) onComplete() else index++ }) { Text(stringResource(if (index == steps.lastIndex) R.string.home_tour_done else R.string.home_tour_next)) } },
        dismissButton = { TextButton(onClick = onComplete) { Text(stringResource(R.string.home_tour_skip)) } },
    )
}
