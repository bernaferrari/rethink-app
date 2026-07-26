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
package com.bernaferrari.bravedns.ui.components


import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.logs.RethinkAppConnectionItem
import com.bernaferrari.bravedns.ui.compose.logs.RethinkAppConnectionRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkConnectionRuleState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.util.Utilities.removeBeginningTrailingCommas
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import kotlin.math.log2


@Composable
fun DomainRow(
    conn: AppConnection,
    uid: Int,
    isActiveConn: Boolean,
    refreshToken: Int,
    onIpClick: (AppConnection) -> Unit
) {
    val (primaryText, secondaryText) =
        if (isActiveConn) {
            val ip = beautifyIpString(conn.ipAddress)
            val name = conn.appOrDnsName.orEmpty()
            ip to name
        } else {
            conn.appOrDnsName to conn.ipAddress
        }

    val ruleState =
        if (isActiveConn || conn.appOrDnsName.isNullOrEmpty()) {
            RethinkConnectionRuleState.None
        } else {
            when (DomainRulesManager.status(conn.appOrDnsName.orEmpty(), uid)) {
                DomainRulesManager.Status.NONE -> RethinkConnectionRuleState.None
                DomainRulesManager.Status.BLOCK -> RethinkConnectionRuleState.Block
                DomainRulesManager.Status.TRUST -> RethinkConnectionRuleState.Trust
            }
        }
    val activity =
        if (isActiveConn || conn.appOrDnsName.isNullOrEmpty() || refreshToken == Int.MIN_VALUE) {
            null
        } else {
            (calculatePercentage(conn.count.toDouble()).coerceAtLeast(5) / 100f)
        }
    RethinkAppConnectionRow(
        item = RethinkAppConnectionItem(
            flag = conn.flag,
            title = primaryText.orEmpty(),
            supporting = secondaryText,
            count = conn.count.toString(),
            ruleState = ruleState,
            activity = activity,
        ),
        onClick = { onIpClick(conn) },
    )
}

@Composable
fun CloseConnsDialog(
    conn: AppConnection,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = context.getString(R.string.close_conns_dialog_title),
        message = context.getString(R.string.close_conns_dialog_desc, conn.ipAddress),
        confirmText = context.getString(R.string.lbl_proceed),
        dismissText = context.getString(R.string.lbl_cancel),
        onConfirm = {
            VpnController.closeConnectionsByUidDomain(
                conn.uid,
                conn.ipAddress,
                "app-wise-domains-manual-close"
            )
            showToastUiCentered(
                context,
                context.getString(R.string.config_add_success_toast),
                Toast.LENGTH_LONG
            )
            onConfirm()
        },
        onDismiss = onDismiss
    )
}

private fun calculatePercentage(c: Double): Int {
    // If not available, it becomes a per-item progress which is less useful.
    // For now, let's use a reasonable default or assume max is handled elsewhere.
    val value = (log2(c) * 100).toInt()    // In a LazyList, computing global max is expensive or requires a separate pass.
    return (value % 100) // Fallback
}

private fun beautifyIpString(d: String): String {
    return removeBeginningTrailingCommas(d).replace(",,", ",").replace(",", ", ")
}
