/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.configure

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions

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
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimensions.spacingLg, horizontal = Dimensions.spacingSm)
            ) {
                Text(
                    text = stringResource(id = R.string.app_name_small_case),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.alpha(Dimensions.Opacity.LOW)
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                Text(
                    text = stringResource(id = R.string.settings_title_desc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.alpha(Dimensions.Opacity.MEDIUM)
                )
            }

            // Configure options
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

            Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Dimensions.cardCornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = iconId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSizeMd)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Dimensions.iconSizeMd)
                    .alpha(Dimensions.Opacity.LOW)
            )
        }
    }
}
