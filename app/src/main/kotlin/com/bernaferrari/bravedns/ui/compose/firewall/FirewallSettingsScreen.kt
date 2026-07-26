package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R

/** Android resource adapter for the common Firewall Settings renderer. */
@Composable
fun FirewallSettingsScreen(
    onUniversalFirewallClick: () -> Unit,
    onCustomIpDomainClick: () -> Unit,
    onAppWiseIpDomainClick: () -> Unit,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
) {
    RethinkFirewallSettingsScreen(
        strings = RethinkFirewallSettingsStrings(
            title = stringResource(R.string.firewall_mode_info_title),
            subtitle = stringResource(R.string.universal_firewall_explanation),
            universalSection = stringResource(R.string.firewall_act_universal_tab),
            universalTitle = stringResource(R.string.univ_firewall_heading),
            universalDescription = stringResource(R.string.universal_firewall_explanation),
            blockedTitle = stringResource(R.string.univ_view_blocked_ip),
            blockedDescription = stringResource(R.string.univ_view_blocked_ip_desc),
            appWiseSection = stringResource(R.string.lbl_app_wise),
            appWiseTitle = stringResource(R.string.app_ip_domain_rules),
            appWiseDescription = stringResource(R.string.app_ip_domain_rules_desc),
        ),
        onUniversalFirewallClick = onUniversalFirewallClick,
        onCustomIpDomainClick = onCustomIpDomainClick,
        onAppWiseIpDomainClick = onAppWiseIpDomainClick,
        initialFocusKey = initialFocusKey,
        onBackClick = onBackClick,
    )
}
