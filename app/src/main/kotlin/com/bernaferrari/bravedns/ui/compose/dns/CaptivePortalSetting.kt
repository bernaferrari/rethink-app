package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import org.koin.compose.koinInject

@Composable
internal fun CaptivePortalSetting() {
    val preferences = koinInject<PersistentState>()
    var enabled by remember { mutableStateOf(preferences.useSystemDnsOnCaptivePortal) }
    RethinkSwitchListItem(
        headline = stringResource(R.string.port_captive_title),
        supporting = stringResource(R.string.port_captive_description),
        checked = enabled,
        onCheckedChange = { preferences.useSystemDnsOnCaptivePortal = it; enabled = it },
        position = CardPosition.Single,
    )
}
