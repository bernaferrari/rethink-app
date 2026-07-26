/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bernaferrari.bravedns.ui.compose.settings.RethinkSubscriptionCelebration

/** Android window adapter for the shared purchase celebration. */
@Composable
fun SubscriptionAnimDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RethinkSubscriptionCelebration(onComplete = onDismiss)
    }
}
