package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
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
            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ),
                        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge)
                    )
                    .padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacing2xl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(id = R.string.firewall_mode_info_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.universal_firewall_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

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

            Spacer(modifier = Modifier.height(Dimensions.spacing2xl))
        }
    }
}
