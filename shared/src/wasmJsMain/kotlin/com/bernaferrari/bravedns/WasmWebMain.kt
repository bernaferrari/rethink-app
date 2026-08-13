/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Browser entry: mounts the shared RethinkAppContent shell (QuietGuard-style) via wasmJs.
 */
package com.bernaferrari.bravedns

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.bernaferrari.bravedns.di.RethinkKoinApp
import com.bernaferrari.bravedns.di.RethinkWebDemoDependencies
import com.bernaferrari.bravedns.ui.compose.RethinkAppContent
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.plugin.module.dsl.startKoin

private const val WELCOME_COMPLETED_KEY = "rethink.web.welcome.completed"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    val demoDependencies = startKoin<RethinkKoinApp>().koin.get<RethinkWebDemoDependencies>()
    val hasCompletedWelcome =
        runCatching { window.localStorage.getItem(WELCOME_COMPLETED_KEY) == "true" }
            .getOrDefault(false)
    ComposeViewport(body) {
        RethinkAppContent(
            darkTheme = false,
            reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches,
            demoDependencies = demoDependencies,
            showWelcomeInitially = !hasCompletedWelcome,
            onWelcomeFinished = {
                runCatching {
                    window.localStorage.setItem(WELCOME_COMPLETED_KEY, "true")
                }
            },
        )
    }
}
