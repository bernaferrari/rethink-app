package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                Surface(
                    shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Dimensions.spacingLg,
                            vertical = Dimensions.spacingMd
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
                    ) {
                        Text(
                            text = stringResource(id = R.string.firewall_mode_info_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.universal_firewall_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
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
            }

            item {
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
}
