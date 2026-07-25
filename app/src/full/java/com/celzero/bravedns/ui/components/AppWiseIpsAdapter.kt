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
package com.celzero.bravedns.ui.components


import androidx.compose.runtime.Composable
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.ui.compose.logs.RethinkAppConnectionItem
import com.celzero.bravedns.ui.compose.logs.RethinkAppConnectionRow
import com.celzero.bravedns.ui.compose.logs.RethinkConnectionRuleState
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.removeBeginningTrailingCommas
import kotlin.math.log2


private fun calculatePercentage(c: Double, maxValue: Int): Pair<Int, Int> {
    val value = (log2(c) * 100).toInt()
    val newMaxValue = if (value > maxValue) value else maxValue
    return if (newMaxValue == 0) {
        0 to 0
    } else {
        val percentage = (value * 100 / newMaxValue)
        percentage to newMaxValue
    }
}

@Composable
fun IpRow(
    conn: AppConnection,
    isAsn: Boolean,
    refreshToken: Int,
    onIpClick: (AppConnection) -> Unit
) {
    val flagText =
        if (isAsn) {
            val cc = Utilities.getFlag(conn.flag)
            if (cc.isEmpty()) "--" else cc
        } else {
            conn.flag
        }
    val titleText = if (isAsn) conn.appOrDnsName else conn.ipAddress
    val secondaryText =
        if (isAsn) conn.ipAddress else conn.appOrDnsName?.let { beautifyDomainString(it) }

    val ruleState =
        if (isAsn) {
            RethinkConnectionRuleState.None
        } else {
            when (IpRulesManager.getMostSpecificRuleMatch(conn.uid, conn.ipAddress)) {
                IpRulesManager.IpRuleStatus.NONE -> RethinkConnectionRuleState.None
                IpRulesManager.IpRuleStatus.BLOCK -> RethinkConnectionRuleState.Block
                IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL -> RethinkConnectionRuleState.Bypass
                IpRulesManager.IpRuleStatus.TRUST -> RethinkConnectionRuleState.Trust
            }
        }
    val activity =
        if (isAsn || refreshToken == Int.MIN_VALUE) {
            null
        } else {
            val score = (log2(conn.count.toDouble()) * 100).toInt()
            if (score <= 0) 0.1f else (score / 500f).coerceAtMost(1f)
        }
    RethinkAppConnectionRow(
        item = RethinkAppConnectionItem(
            flag = flagText,
            title = titleText.orEmpty(),
            supporting = secondaryText,
            count = conn.count.toString(),
            ruleState = ruleState,
            activity = activity,
        ),
        onClick = { onIpClick(conn) },
    )
}

private fun beautifyDomainString(d: String): String {
    return removeBeginningTrailingCommas(d).replace(",,", ",").replace(",", ", ")
}
