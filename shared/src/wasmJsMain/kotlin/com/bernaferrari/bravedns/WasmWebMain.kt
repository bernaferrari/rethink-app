/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Browser entry: mounts commonMain RethinkDemoApp via Compose Multiplatform wasmJs.
 */
package com.bernaferrari.bravedns

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.bernaferrari.bravedns.di.RethinkKoinApp
import com.bernaferrari.bravedns.di.RethinkWebDemoDependencies
import com.bernaferrari.bravedns.ui.compose.RethinkDemoApp
import kotlinx.browser.document
import org.koin.plugin.module.dsl.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    val demoDependencies = startKoin<RethinkKoinApp>().koin.get<RethinkWebDemoDependencies>()
    ComposeViewport(body) {
        RethinkDemoApp(
            darkTheme = false,
            demoDependencies = demoDependencies,
        )
    }
}
