/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class DnsListActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()

    private var selectedType by mutableStateOf<AppConfig.DnsType?>(null)
    private var selectedWorking by mutableStateOf(false)

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            RethinkTheme {
                DnsListScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSelectedStatus()
    }

    private fun updateSelectedStatus() {
        io {
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
            uiCtx {
                selectedType = appConfig.getDnsType()
                selectedWorking = working
            }
        }
    }

    private fun invokeRethinkActivity() {
        val intent = Intent(this, ConfigureRethinkBasicActivity::class.java)
        intent.putExtra(
            ConfigureRethinkBasicActivity.INTENT,
            ConfigureRethinkBasicActivity.FragmentLoader.DB_LIST.ordinal
        )
        startActivity(intent)
    }

    @Composable
    private fun DnsListScreen() {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DnsCard(
                    label = stringResourceCompat(R.string.dc_doh),
                    title = stringResourceCompat(R.string.cd_custom_doh_url_name_default),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.DOH,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        startActivity(
                            ConfigureOtherDnsActivity.getIntent(
                                this@DnsListActivity,
                                ConfigureOtherDnsActivity.DnsScreen.DOH.index
                            )
                        )
                    }
                )
                DnsCard(
                    label = stringResourceCompat(R.string.lbl_dot_abbr),
                    title = stringResourceCompat(R.string.lbl_dot),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.DOT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        startActivity(
                            ConfigureOtherDnsActivity.getIntent(
                                this@DnsListActivity,
                                ConfigureOtherDnsActivity.DnsScreen.DOT.index
                            )
                        )
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DnsCard(
                    label = stringResourceCompat(R.string.dc_dns_crypt),
                    title = stringResourceCompat(R.string.cd_dns_crypt_name_default),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green, R.drawable.dot_accent),
                    type = AppConfig.DnsType.DNSCRYPT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        startActivity(
                            ConfigureOtherDnsActivity.getIntent(
                                this@DnsListActivity,
                                ConfigureOtherDnsActivity.DnsScreen.DNS_CRYPT.index
                            )
                        )
                    }
                )
                DnsCard(
                    label = stringResourceCompat(R.string.lbl_dp_abbr),
                    title = stringResourceCompat(R.string.lbl_dp),
                    dots = listOf(R.drawable.dot_red),
                    type = AppConfig.DnsType.DNS_PROXY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        startActivity(
                            ConfigureOtherDnsActivity.getIntent(
                                this@DnsListActivity,
                                ConfigureOtherDnsActivity.DnsScreen.DNS_PROXY.index
                            )
                        )
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DnsCard(
                    label = stringResourceCompat(R.string.lbl_odoh_abbr),
                    title = stringResourceCompat(R.string.lbl_odoh),
                    dots = listOf(R.drawable.dot_yellow, R.drawable.dot_green, R.drawable.dot_accent),
                    type = AppConfig.DnsType.ODOH,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        startActivity(
                            ConfigureOtherDnsActivity.getIntent(
                                this@DnsListActivity,
                                ConfigureOtherDnsActivity.DnsScreen.ODOH.index
                            )
                        )
                    }
                )
                DnsCard(
                    label = stringResourceCompat(R.string.dc_rethink_dns_radio),
                    title = stringResourceCompat(R.string.lbl_rdns),
                    dots = listOf(R.drawable.dot_red, R.drawable.dot_yellow, R.drawable.dot_green),
                    type = AppConfig.DnsType.RETHINK_REMOTE,
                    modifier = Modifier.weight(1f),
                    onClick = { invokeRethinkActivity() }
                )
            }

            LegendRow()
        }
    }

    @Composable
    private fun DnsCard(
        label: String,
        title: String,
        dots: List<Int>,
        type: AppConfig.DnsType,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val isSelected = selectedType == type
        val strokeColor =
            if (isSelected) {
                val attr = if (selectedWorking) R.color.accentGood else R.color.accentBad
                UIUtils.fetchToggleBtnColors(this, attr)
            } else {
                0
            }
        val textColor =
            if (isSelected) {
                if (selectedWorking) fetchColor(this, R.attr.secondaryTextColor) else fetchColor(this, R.attr.accentBad)
            } else {
                fetchColor(this, R.attr.primaryTextColor)
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
            LegendItem(R.drawable.dot_red, stringResourceCompat(R.string.lbl_fast))
            LegendItem(R.drawable.dot_yellow, stringResourceCompat(R.string.lbl_private))
            LegendItem(R.drawable.dot_green, stringResourceCompat(R.string.lbl_secure))
            LegendItem(R.drawable.dot_accent, stringResourceCompat(R.string.lbl_anonymous))
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

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) context.getString(id, *args) else context.getString(id)
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}
