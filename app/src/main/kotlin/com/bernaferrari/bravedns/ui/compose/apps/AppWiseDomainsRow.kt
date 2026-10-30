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
package com.bernaferrari.bravedns.ui.compose.apps


import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.logs.RethinkAppConnectionItem
import com.bernaferrari.bravedns.ui.compose.logs.RethinkAppConnectionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered


@Composable
fun DomainRow(
    conn: AppConnection,
    uid: Int,
    refreshToken: Int,
    onIpClick: (AppConnection) -> Unit,
    position: CardPosition = CardPosition.Single,
) {
    val ruleState =
        if (conn.appOrDnsName.isNullOrEmpty()) {
            RethinkFirewallRuleStatus.None
        } else {
            when (DomainRulesManager.status(conn.appOrDnsName.orEmpty(), uid)) {
                DomainRulesManager.Status.NONE -> RethinkFirewallRuleStatus.None
                DomainRulesManager.Status.BLOCK -> RethinkFirewallRuleStatus.Block
                DomainRulesManager.Status.TRUST -> RethinkFirewallRuleStatus.Trust
            }
        }
    val activity =
        if (conn.appOrDnsName.isNullOrEmpty() || refreshToken == Int.MIN_VALUE) {
            null
        } else {
            connectionActivity(conn.count)
        }
    RethinkAppConnectionRow(
        item = RethinkAppConnectionItem(
            flag = conn.flag,
            title = conn.appOrDnsName.orEmpty(),
            supporting = conn.ipAddress,
            count = conn.count.toString(),
            ruleState = ruleState,
            activity = activity,
        ),
        onClick = { onIpClick(conn) },
        position = position,
    )
}

@Composable
fun CloseConnsDialog(
    conn: AppConnection,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val successToast = stringResource(R.string.config_add_success_toast)
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.close_conns_dialog_title),
        message = stringResource(R.string.close_conns_dialog_desc, conn.ipAddress),
        confirmText = stringResource(R.string.lbl_proceed),
        dismissText = stringResource(R.string.lbl_cancel),
        onConfirm = {
            VpnController.closeConnectionsByUidDomain(
                conn.uid,
                conn.ipAddress,
                "app-wise-domains-manual-close"
            )
            showToastUiCentered(
                context,
                successToast,
                Toast.LENGTH_LONG
            )
            onConfirm()
        },
        onDismiss = onDismiss
    )
}
