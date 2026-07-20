/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Browser entry: mounts commonMain RethinkDemoApp via Compose Multiplatform wasmJs.
 */
package com.celzero.bravedns

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.celzero.bravedns.ui.compose.RethinkDemoApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        RethinkDemoApp(
            darkTheme = false,
            demoBanner = "Web preview (wasmJs) — UI from commonMain; no VPN/DNS/Room here.",
        )
    }
}
