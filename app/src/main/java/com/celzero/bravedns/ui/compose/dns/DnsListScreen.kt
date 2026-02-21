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
package com.celzero.bravedns.ui.compose.dns

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsListScreen(
    appConfig: AppConfig,
    onConfigureOtherDns: (Int) -> Unit,
    onConfigureRethinkBasic: (Int) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    var selectedType by remember { mutableStateOf<AppConfig.DnsType?>(null) }
    var selectedWorking by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val id = if (appConfig.isSmartDnsEnabled()) Backend.Plus else Backend.Preferred
            val state = VpnController.getDnsStatus(id)
            val working = if (state == null) false else when (Transaction.Status.fromId(state)) {
                Transaction.Status.COMPLETE, Transaction.Status.START -> true
                else -> false
            }
            selectedType = appConfig.getDnsType()
            selectedWorking = working
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_dns_servers),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            // Description card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dns_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // DNS protocol cards grid
            item {
                SectionHeader(title = stringResource(R.string.lbl_configure))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DnsCard(
                            label = stringResource(R.string.dc_doh),
                            title = stringResource(R.string.cd_custom_doh_url_name_default),
                            dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                            type = AppConfig.DnsType.DOH,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureOtherDns(DnsScreenType.DOH.index) }
                        )
                        DnsCard(
                            label = stringResource(R.string.lbl_dot_abbr),
                            title = stringResource(R.string.lbl_dot),
                            dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                            type = AppConfig.DnsType.DOT,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureOtherDns(DnsScreenType.DOT.index) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DnsCard(
                            label = stringResource(R.string.dc_dns_crypt),
                            title = stringResource(R.string.cd_dns_crypt_name_default),
                            dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green, R.drawable.dot_accent),
                            type = AppConfig.DnsType.DNSCRYPT,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureOtherDns(DnsScreenType.DNS_CRYPT.index) }
                        )
                        DnsCard(
                            label = stringResource(R.string.lbl_dp_abbr),
                            title = stringResource(R.string.lbl_dp),
                            dots = listOf(R.drawable.dot_red),
                            type = AppConfig.DnsType.DNS_PROXY,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureOtherDns(DnsScreenType.DNS_PROXY.index) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DnsCard(
                            label = stringResource(R.string.lbl_odoh_abbr),
                            title = stringResource(R.string.lbl_odoh),
                            dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green, R.drawable.dot_accent),
                            type = AppConfig.DnsType.ODOH,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureOtherDns(DnsScreenType.ODOH.index) }
                        )
                        DnsCard(
                            label = stringResource(R.string.dc_rethink_dns_radio),
                            title = stringResource(R.string.lbl_rdns),
                            dots = listOf(R.drawable.dot_red, R.drawable.dot_yellow, R.drawable.dot_green),
                            type = AppConfig.DnsType.RETHINK_REMOTE,
                            selectedType = selectedType,
                            selectedWorking = selectedWorking,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfigureRethinkBasic(0) }
                        )
                    }
                }
            }

            // Legend
            item { LegendRow() }
        }
    }
}

// ─── DNS Card ─────────────────────────────────────────────────────────────────

@Composable
private fun DnsCard(
    label: String,
    title: String,
    dots: List<Int>,
    type: AppConfig.DnsType,
    selectedType: AppConfig.DnsType?,
    selectedWorking: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isSelected = selectedType == type
    val containerColor = when {
        isSelected && selectedWorking -> MaterialTheme.colorScheme.primaryContainer
        isSelected && !selectedWorking -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val labelColor = when {
        isSelected && selectedWorking -> MaterialTheme.colorScheme.onPrimaryContainer
        isSelected && !selectedWorking -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = when {
        isSelected && selectedWorking -> MaterialTheme.colorScheme.primary
        isSelected && !selectedWorking -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Protocol name — small label
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                textAlign = TextAlign.Center
            )

            // Abbreviation — large and prominent
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                textAlign = TextAlign.Center
            )

            // Capability dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dots.forEach { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// ─── Legend Row ───────────────────────────────────────────────────────────────

@Composable
private fun LegendRow() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.spacingLg, vertical = Dimensions.spacingMd)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(R.drawable.dot_red, stringResource(R.string.lbl_fast))
            LegendItem(R.drawable.dot_yellow, stringResource(R.string.lbl_private))
            LegendItem(R.drawable.dot_green, stringResource(R.string.lbl_secure))
            LegendItem(R.drawable.dot_accent, stringResource(R.string.lbl_anonymous))
        }
    }
}

@Composable
private fun LegendItem(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(painter = painterResource(id = icon), contentDescription = null, modifier = Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
