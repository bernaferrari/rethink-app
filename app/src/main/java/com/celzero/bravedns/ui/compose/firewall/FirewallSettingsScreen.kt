package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkTheme

@OptIn(ExperimentalMaterial3Api::class)
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
            if (onBackClick != null) {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.firewall_mode_info_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.firewall_act_universal_tab),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    FirewallSettingItem(
                        title = stringResource(id = R.string.univ_firewall_heading),
                        description = stringResource(id = R.string.universal_firewall_explanation),
                        iconId = R.drawable.universal_firewall,
                        onClick = onUniversalFirewallClick
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    FirewallSettingItem(
                        title = stringResource(id = R.string.univ_view_blocked_ip),
                        description = stringResource(id = R.string.univ_view_blocked_ip_desc),
                        iconId = R.drawable.universal_ip_rule,
                        onClick = onCustomIpDomainClick
                    )
                }
            }

            Text(
                text = stringResource(id = R.string.lbl_app_wise),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                FirewallSettingItem(
                    title = stringResource(id = R.string.app_ip_domain_rules),
                    description = stringResource(id = R.string.app_ip_domain_rules_desc),
                    iconId = R.drawable.ic_ip_address,
                    onClick = onAppWiseIpDomainClick
                )
            }
        }
    }
}

@Composable
fun FirewallSettingItem(
    title: String,
    description: String,
    iconId: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp).padding(4.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}
