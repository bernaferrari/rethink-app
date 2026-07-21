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
import androidx.compose.ui.unit.dp

/** A touch-first Compose replacement for the legacy view-ID spotlight tour. */
@Composable
fun HomeGuidedTour(onComplete: () -> Unit) {
    val steps = remember {
        listOf(
            "Protection" to "Start or stop the VPN protection from the card at the top of Home.",
            "DNS" to "Choose DNS, trusted-DNS bypass behavior, and blocklists from DNS settings.",
            "Firewall" to "Set universal and app-specific network rules from Firewall.",
            "Proxy" to "Configure RPN and WireGuard routing from Proxy.",
            "Logs" to "Inspect network and DNS activity from Logs.",
            "Rethink Plus" to "Manage your RPN subscription, locations, and account recovery here.",
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val (title, description) = steps[index]
    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text("${index + 1}/${steps.size} · $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
                Text(description)
            }
        },
        confirmButton = { TextButton(onClick = { if (index == steps.lastIndex) onComplete() else index++ }) { Text(if (index == steps.lastIndex) "Done" else "Next") } },
        dismissButton = { TextButton(onClick = onComplete) { Text("Skip") } },
    )
}
