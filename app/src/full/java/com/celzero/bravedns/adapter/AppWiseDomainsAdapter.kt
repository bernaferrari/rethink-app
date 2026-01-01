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
package com.celzero.bravedns.adapter

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.removeBeginningTrailingCommas
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import io.github.aakira.napier.Napier
import kotlin.math.log2

class AppWiseDomainsAdapter(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    val uid: Int,
    val isActiveConn: Boolean = false,
    val onShowDomainRules: (String) -> Unit
) {

    private var maxValue: Int = 0
    private var minPercentage: Int = 100
    private val refreshToken = mutableStateOf(0)
    private val pendingCloseDialog = mutableStateOf<AppConnection?>(null)

    companion object {
        private const val TAG = "AppWiseDomainsAdapter"
    }

    private fun calculatePercentage(c: Double): Int {
        val value = (log2(c) * 100).toInt()
        if (value > maxValue) {
            maxValue = value
        }
        return if (maxValue == 0) {
            0
        } else {
            val percentage = (value * 100 / maxValue)
            if (percentage < minPercentage && percentage != 0) {
                minPercentage = percentage
            }
            percentage
        }
    }

    @Composable
    fun DomainRow(conn: AppConnection) {
        val countText = conn.count.toString()
        val (primaryText, secondaryText) =
            if (isActiveConn) {
                val ip = beautifyIpString(conn.ipAddress)
                val name = conn.appOrDnsName.orEmpty()
                ip to name
            } else {
                conn.appOrDnsName to conn.ipAddress
            }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { handleClick(conn) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = conn.flag, style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = primaryText.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    if (!secondaryText.isNullOrEmpty()) {
                        Text(text = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isActiveConn && !conn.appOrDnsName.isNullOrEmpty()) {
                        DomainProgress(conn, refreshToken.value)
                    }
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun CloseDialogHost() {
        val conn = pendingCloseDialog.value ?: return
        AlertDialog(
            onDismissRequest = { pendingCloseDialog.value = null },
            title = { Text(text = context.getString(R.string.close_conns_dialog_title)) },
            text = {
                Text(
                    text = context.getString(R.string.close_conns_dialog_desc, conn.ipAddress)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        VpnController.closeConnectionsByUidDomain(
                            conn.uid,
                            conn.ipAddress,
                            "app-wise-domains-manual-close"
                        )
                        Napier.i("$TAG closed connection for uid: ${conn.uid}, domain: ${conn.appOrDnsName}")
                        showToastUiCentered(
                            context,
                            context.getString(R.string.config_add_success_toast),
                            Toast.LENGTH_LONG
                        )
                        pendingCloseDialog.value = null
                    }
                ) {
                    Text(text = context.getString(R.string.lbl_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCloseDialog.value = null }) {
                    Text(text = context.getString(R.string.lbl_cancel))
                }
            }
        )
    }

    @Composable
    private fun DomainProgress(conn: AppConnection, refresh: Int) {
        if (refresh == Int.MIN_VALUE) {
            return
        }
        val status = DomainRulesManager.status(conn.appOrDnsName.orEmpty(), uid)
        val color =
            when (status) {
                DomainRulesManager.Status.NONE ->
                    UIUtils.fetchToggleBtnColors(context, R.color.chipTextNeutral)
                DomainRulesManager.Status.BLOCK ->
                    UIUtils.fetchToggleBtnColors(context, R.color.accentBad)
                DomainRulesManager.Status.TRUST ->
                    UIUtils.fetchToggleBtnColors(context, R.color.accentGood)
            }
        var p = calculatePercentage(conn.count.toDouble())
        if (p == 0) {
            p = minPercentage / 2
        }
        LinearProgressIndicator(
            progress = p / 100f,
            color = Color(color),
            trackColor = Color(UIUtils.fetchColor(context, R.attr.background)),
            modifier = Modifier.fillMaxWidth()
        )
    }

    private fun handleClick(conn: AppConnection) {
        if (isActiveConn) {
            pendingCloseDialog.value = conn
            return
        }
        openBottomSheet(conn)
    }

    private fun openBottomSheet(appConn: AppConnection) {
        if (isActiveConn) {
            Napier.i("$TAG active connection - no bottom sheet")
            return
        }

        val domain = appConn.appOrDnsName
        if (domain.isNullOrEmpty()) {
            Napier.w("$TAG missing domain for uid: $uid, ip: ${appConn.ipAddress}")
            return
        }
        onShowDomainRules(domain)
    }

    private fun beautifyIpString(d: String): String {
        return removeBeginningTrailingCommas(d).replace(",,", ",").replace(",", ", ")
    }

    fun notifyRulesChanged() {
        refreshToken.value = refreshToken.value + 1
    }

}
