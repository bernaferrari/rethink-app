package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallSettingsScreen(
    onUniversalFirewallClick: () -> Unit,
    onCustomIpDomainClick: () -> Unit,
    onAppWiseIpDomainClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.firewall_mode_info_title),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                SectionHeader(title = stringResource(id = R.string.firewall_act_universal_tab))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.univ_firewall_heading),
                        supporting = stringResource(id = R.string.universal_firewall_explanation),
                        leadingIconPainter = painterResource(id = R.drawable.universal_firewall),
                        position = CardPosition.First,
                        onClick = onUniversalFirewallClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.univ_view_blocked_ip),
                        supporting = stringResource(id = R.string.univ_view_blocked_ip_desc),
                        leadingIconPainter = painterResource(id = R.drawable.universal_ip_rule),
                        position = CardPosition.Last,
                        onClick = onCustomIpDomainClick
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.lbl_app_wise))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.app_ip_domain_rules),
                        supporting = stringResource(id = R.string.app_ip_domain_rules_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_ip_address),
                        position = CardPosition.Single,
                        onClick = onAppWiseIpDomainClick
                    )
                }
            }
        }
    }
}
