package com.celzero.bravedns.ui.compose.configure

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

@Composable
fun ConfigureScreen(
    isDebug: Boolean,
    onAppsClick: () -> Unit,
    onDnsClick: () -> Unit,
    onFirewallClick: () -> Unit,
    onProxyClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onOthersClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAntiCensorshipClick: () -> Unit,
    onAdvancedClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.app_name_small_case),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(id = R.string.settings_title_desc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            ConfigureCard(
                title = stringResource(id = R.string.apps_info_title),
                iconId = R.drawable.ic_app_info_accent,
                onClick = onAppsClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.dns_mode_info_title),
                iconId = R.drawable.dns_home_screen,
                onClick = onDnsClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.firewall_mode_info_title),
                iconId = R.drawable.firewall_home_screen,
                onClick = onFirewallClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.cd_custom_dns_proxy_name_default),
                iconId = R.drawable.ic_proxy,
                onClick = onProxyClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.lbl_network).replaceFirstChar { it.uppercase() },
                iconId = R.drawable.ic_network_tunnel,
                onClick = onNetworkClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.title_settings),
                iconId = R.drawable.ic_other_settings,
                onClick = onOthersClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.lbl_logs).replaceFirstChar { it.uppercase() },
                iconId = R.drawable.ic_logs_accent,
                onClick = onLogsClick
            )

            ConfigureCard(
                title = stringResource(id = R.string.anti_censorship_title).replaceFirstChar { it.uppercase() },
                iconId = R.drawable.ic_anti_dpi,
                onClick = onAntiCensorshipClick
            )

            if (isDebug) {
                ConfigureCard(
                    title = stringResource(id = R.string.lbl_advanced).replaceFirstChar { it.uppercase() },
                    iconId = R.drawable.ic_advanced_settings,
                    onClick = onAdvancedClick
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for FAB/Navigation
        }
    }
}

@Composable
fun ConfigureCard(
    title: String,
    iconId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = iconId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
