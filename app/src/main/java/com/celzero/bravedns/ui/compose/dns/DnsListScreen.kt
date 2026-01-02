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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ConfigureOtherDnsActivity
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val id = if (appConfig.isSmartDnsEnabled()) {
                Backend.Plus
            } else {
                Backend.Preferred
            }
            val state = VpnController.getDnsStatus(id)
            val working =
                if (state == null) {
                    false
                } else {
                    when (Transaction.Status.fromId(state)) {
                        Transaction.Status.COMPLETE,
                        Transaction.Status.START -> true
                        else -> false
                    }
                }
            
            selectedType = appConfig.getDnsType()
            selectedWorking = working
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lbl_dns_servers)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DnsCard(
                    label = stringResource(R.string.dc_doh),
                    title = stringResource(R.string.cd_custom_doh_url_name_default),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.DOH,
                    selectedType = selectedType,
                    selectedWorking = selectedWorking,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfigureOtherDns(ConfigureOtherDnsActivity.DnsScreen.DOH.index)
                    }
                )
                DnsCard(
                    label = stringResource(R.string.lbl_dot_abbr),
                    title = stringResource(R.string.lbl_dot),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.DOT,
                    selectedType = selectedType,
                    selectedWorking = selectedWorking,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfigureOtherDns(ConfigureOtherDnsActivity.DnsScreen.DOT.index)
                    }
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
                    onClick = {
                        onConfigureOtherDns(ConfigureOtherDnsActivity.DnsScreen.DNS_CRYPT.index)
                    }
                )
                DnsCard(
                    label = stringResource(R.string.lbl_dp_abbr),
                    title = stringResource(R.string.lbl_dp),
                    dots = listOf(R.drawable.dot_red),
                    type = AppConfig.DnsType.DNS_PROXY,
                    selectedType = selectedType,
                    selectedWorking = selectedWorking,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfigureOtherDns(ConfigureOtherDnsActivity.DnsScreen.DNS_PROXY.index)
                    }
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
                    onClick = {
                        onConfigureOtherDns(ConfigureOtherDnsActivity.DnsScreen.ODOH.index)
                    }
                )
                DnsCard(
                    label = stringResource(R.string.dc_rethink_dns_radio),
                    title = stringResource(R.string.lbl_rdns),
                    dots = listOf(R.drawable.dot_red, R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.RETHINK_REMOTE,
                    selectedType = selectedType,
                    selectedWorking = selectedWorking,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfigureRethinkBasic(ConfigureRethinkBasicActivity.FragmentLoader.DB_LIST.ordinal)
                    }
                )
            }

            LegendRow()
        }
    }
}

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
    val context = LocalContext.current
    val isSelected = selectedType == type
    val strokeColor =
        if (isSelected) {
            val attr = if (selectedWorking) R.color.accentGood else R.color.accentBad
            UIUtils.fetchToggleBtnColors(context, attr)
        } else {
            0
        }
    val textColor =
        if (isSelected) {
            if (selectedWorking) fetchColor(context, R.attr.secondaryTextColor) else fetchColor(context, R.attr.accentBad)
        } else {
            fetchColor(context, R.attr.primaryTextColor)
        }
    val border =
        if (isSelected) {
            BorderStroke(2.dp, Color(strokeColor))
        } else {
            null
        }

    Card(
        modifier = modifier.aspectRatio(1f),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(textColor),
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color(textColor),
                textAlign = TextAlign.Center
            )
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

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(R.drawable.dot_red, stringResource(R.string.lbl_fast))
        LegendItem(R.drawable.dot_yellow, stringResource(R.string.lbl_private))
        LegendItem(R.drawable.dot_green, stringResource(R.string.lbl_secure))
        LegendItem(R.drawable.dot_accent, stringResource(R.string.lbl_anonymous))
    }
}

@Composable
private fun LegendItem(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(id = icon), contentDescription = null, modifier = Modifier.size(10.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
