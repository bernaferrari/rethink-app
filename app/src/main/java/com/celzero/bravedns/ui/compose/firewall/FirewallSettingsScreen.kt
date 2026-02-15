package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader

@Composable
fun FirewallSettingsScreen(
    onUniversalFirewallClick: () -> Unit,
    onCustomIpDomainClick: () -> Unit,
    onAppWiseIpDomainClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(
                title = stringResource(id = R.string.firewall_mode_info_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            Spacer(modifier = Modifier.height(Dimensions.spacingXs))

            SectionHeader(title = stringResource(id = R.string.firewall_act_universal_tab))
            RethinkListGroup {
                RethinkListItem(
                    headline = stringResource(id = R.string.univ_firewall_heading),
                    supporting = stringResource(id = R.string.universal_firewall_explanation),
                    leadingIconPainter = painterResource(id = R.drawable.universal_firewall),
                    onClick = onUniversalFirewallClick
                )
                RethinkListItem(
                    headline = stringResource(id = R.string.univ_view_blocked_ip),
                    supporting = stringResource(id = R.string.univ_view_blocked_ip_desc),
                    leadingIconPainter = painterResource(id = R.drawable.universal_ip_rule),
                    showDivider = false,
                    onClick = onCustomIpDomainClick
                )
            }

            SectionHeader(title = stringResource(id = R.string.lbl_app_wise))
            RethinkListGroup {
                RethinkListItem(
                    headline = stringResource(id = R.string.app_ip_domain_rules),
                    supporting = stringResource(id = R.string.app_ip_domain_rules_desc),
                    leadingIconPainter = painterResource(id = R.drawable.ic_ip_address),
                    showDivider = false,
                    onClick = onAppWiseIpDomainClick
                )
            }
        }
    }
}
