package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R

/** A touch-first Compose replacement for the legacy view-ID spotlight tour. */
@Composable
fun HomeGuidedTour(onComplete: () -> Unit) {
    val context = LocalContext.current
    RethinkHomeGuidedTour(
        steps = listOf(
            RethinkGuidedTourStep(stringResource(R.string.home_tour_protection_title), stringResource(R.string.home_tour_protection_desc)),
            RethinkGuidedTourStep(stringResource(R.string.home_tour_dns_title), stringResource(R.string.home_tour_dns_desc)),
            RethinkGuidedTourStep(stringResource(R.string.home_tour_firewall_title), stringResource(R.string.home_tour_firewall_desc)),
            RethinkGuidedTourStep(stringResource(R.string.home_tour_proxy_title), stringResource(R.string.home_tour_proxy_desc)),
            RethinkGuidedTourStep(stringResource(R.string.home_tour_logs_title), stringResource(R.string.home_tour_logs_desc)),
            RethinkGuidedTourStep(stringResource(R.string.home_tour_plus_title), stringResource(R.string.home_tour_plus_desc)),
        ),
        stepTitle = { index, total, title -> context.getString(R.string.home_tour_step, index, total, title) },
        next = stringResource(R.string.home_tour_next),
        done = stringResource(R.string.home_tour_done),
        skip = stringResource(R.string.home_tour_skip),
        onComplete = onComplete,
    )
}
