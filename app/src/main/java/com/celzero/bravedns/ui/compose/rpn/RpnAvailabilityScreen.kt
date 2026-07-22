/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import Logger
import Logger.LOG_TAG_UI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnAvailabilityScreen(onBackClick: () -> Unit) {
    var items by remember { mutableStateOf<List<RpnAvailabilityItem>>(emptyList()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        loadFailed = false
        val selected =
            runCatching {
                withContext(Dispatchers.IO) {
                    RpnProxyManager.getWinServers()
                        .filter { it.isEnabled && !it.id.equals("AUTO", ignoreCase = true) }
                        .sortedBy { it.name }
                }
            }.getOrElse {
                Logger.w(LOG_TAG_UI, "RpnAvailabilityScreen failed to load locations: ${it.message}")
                loadFailed = true
                emptyList()
            }
        items = selected.map { it.toAvailabilityItem(RpnAvailabilityStatus.Loading) }

        selected.forEach { server ->
            val reachable =
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(8_000L) { VpnController.isRpnReachable(server.key) }
                }
            val status =
                when (reachable) {
                    true -> RpnAvailabilityStatus.Active
                    false -> RpnAvailabilityStatus.Inactive
                    null -> RpnAvailabilityStatus.Unavailable
                }
            items = items.map { item -> if (item.key == server.key) item.copy(status = status) else item }
            Logger.i(LOG_TAG_UI, "RpnAvailabilityScreen server=${server.key}, reachable=$reachable")
        }
    }

    val strength = items.count { it.status == RpnAvailabilityStatus.Active }
    val maxStrength = items.size
    val progress = if (maxStrength > 0) strength.toFloat() / maxStrength else 0f
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.rpn_availability_title),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimensions.screenPaddingHorizontal,
                            vertical = Dimensions.spacingSm,
                        ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(Dimensions.spacingLg)) {
                    Text(
                        text = stringResource(R.string.rpn_availability_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.rpn_availability_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimensions.screenPaddingHorizontal,
                            vertical = Dimensions.spacingLg,
                        ),
            ) {
                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 8.dp,
                    )
                    Text(
                        text = "$strength/$maxStrength",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (items.isEmpty()) {
                    Text(
                        text =
                            stringResource(
                                if (loadFailed) R.string.rpn_availability_load_failed
                                else R.string.rpn_availability_no_selected,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimensions.spacingMd),
                    )
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            items.forEachIndexed { index, item ->
                                AvailabilityRow(item)
                                if (index != items.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { refreshToken++ },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.rpn_availability_retry)) }
            }
        }
    }
}

@Composable
private fun AvailabilityRow(item: RpnAvailabilityItem) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = item.name, style = MaterialTheme.typography.bodyMedium)
        when (item.status) {
            RpnAvailabilityStatus.Loading ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            RpnAvailabilityStatus.Active ->
                AvailabilityStatusText(R.string.lbl_active, MaterialTheme.colorScheme.primary)
            RpnAvailabilityStatus.Inactive ->
                AvailabilityStatusText(R.string.lbl_inactive, MaterialTheme.colorScheme.error)
            RpnAvailabilityStatus.Unavailable ->
                AvailabilityStatusText(
                    R.string.rpn_availability_check_failed,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun AvailabilityStatusText(resId: Int, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = stringResource(resId),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private data class RpnAvailabilityItem(
    val key: String,
    val name: String,
    val status: RpnAvailabilityStatus,
)

private enum class RpnAvailabilityStatus {
    Loading,
    Active,
    Inactive,
    Unavailable,
}

private fun CountryConfig.toAvailabilityItem(status: RpnAvailabilityStatus) =
    RpnAvailabilityItem(
        key = key,
        name = name.ifBlank { cc },
        status = status,
    )
