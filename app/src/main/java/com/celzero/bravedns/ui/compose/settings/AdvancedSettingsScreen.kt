/* Copyright 2024 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.service.PersistentState

@Composable
fun AdvancedSettingsScreen(
    persistentState: PersistentState,
    onBackClick: (() -> Unit)? = null,
) {
    if (!DEBUG) return
    var experimentalEnabled by remember { mutableStateOf(persistentState.nwEngExperimentalFeatures) }
    var autoDialEnabled by remember { mutableStateOf(persistentState.autoDialsParallel) }
    var panicEnabled by remember { mutableStateOf(persistentState.panicRandom) }
    RethinkAdvancedSettingsScreen(
        strings = RethinkAdvancedSettingsStrings(
            title = stringResource(R.string.lbl_advanced),
            subtitle = stringResource(R.string.adv_set_experimental_desc),
            experimentalTitle = stringResource(R.string.adv_set_experimental_title),
            autoDialTitle = stringResource(R.string.set_auto_dial_title),
            autoDialDescription = stringResource(R.string.set_auto_dial_desc),
            panicTitle = "Random panic",
            panicDescription = "Debug-only chaos mode for tunnel reliability testing.",
        ),
        experimentalEnabled = experimentalEnabled,
        autoDialEnabled = autoDialEnabled,
        panicEnabled = panicEnabled,
        onExperimentalChange = { experimentalEnabled = it; persistentState.nwEngExperimentalFeatures = it },
        onAutoDialChange = { autoDialEnabled = it; persistentState.autoDialsParallel = it },
        onPanicChange = { panicEnabled = it; persistentState.panicRandom = it },
        onBackClick = onBackClick,
    )
}
