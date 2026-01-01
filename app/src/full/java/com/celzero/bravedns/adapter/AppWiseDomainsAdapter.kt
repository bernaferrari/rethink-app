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
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.AppDomainRulesDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.removeBeginningTrailingCommas
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlin.math.log2

class AppWiseDomainsAdapter(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    val uid: Int,
    val isActiveConn: Boolean = false
) :
    PagingDataAdapter<AppConnection, AppWiseDomainsAdapter.ConnectionDetailsViewHolder>(
        DIFF_CALLBACK
    ) {

    private var maxValue: Int = 0
    private var minPercentage: Int = 100

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AppConnection>() {
                override fun areItemsTheSame(oldConnection: AppConnection, newConnection: AppConnection) =
                    oldConnection == newConnection

                override fun areContentsTheSame(oldConnection: AppConnection, newConnection: AppConnection) =
                    oldConnection == newConnection
            }

        private const val TAG = "AppWiseDomainsAdapter"
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConnectionDetailsViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return ConnectionDetailsViewHolder(composeView)
    }

    override fun onBindViewHolder(
        holder: ConnectionDetailsViewHolder,
        position: Int
    ) {
        val appConnection: AppConnection = getItem(position) ?: return
        holder.update(appConnection)
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

    inner class ConnectionDetailsViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {
        fun update(conn: AppConnection) {
            composeView.setContent {
                RethinkTheme {
                    DomainRow(conn)
                }
            }
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
                        DomainProgress(conn)
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
    private fun DomainProgress(conn: AppConnection) {
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
            showCloseConnectionDialog(conn)
            return
        }
        openBottomSheet(conn)
    }

    private fun showCloseConnectionDialog(appConn: AppConnection) {
        val dialog = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
            .setTitle(context.getString(R.string.close_conns_dialog_title))
            .setMessage(context.getString(R.string.close_conns_dialog_desc, appConn.ipAddress))
            .setPositiveButton(R.string.lbl_proceed) { _, _ ->
                VpnController.closeConnectionsByUidDomain(
                    appConn.uid,
                    appConn.ipAddress,
                    "app-wise-domains-manual-close"
                )
                Napier.i("$TAG closed connection for uid: ${appConn.uid}, domain: ${appConn.appOrDnsName}")
                showToastUiCentered(
                    context,
                    context.getString(R.string.config_add_success_toast),
                    Toast.LENGTH_LONG
                )
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
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

        val activity = context as? FragmentActivity
        if (activity == null) {
            Napier.w("$TAG invalid context for app domain dialog")
            return
        }
        AppDomainRulesDialog(
            activity,
            uid,
            domain,
            RecyclerView.NO_POSITION
        ) { pos ->
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos)
            } else {
                notifyDataSetChanged()
            }
        }.show()
    }

    private fun beautifyIpString(d: String): String {
        return removeBeginningTrailingCommas(d).replace(",,", ",").replace(",", ", ")
    }

    private fun notifyDataset(position: Int) {
        try {
            if (position >= 0 && position < itemCount) {
                notifyItemChanged(position)
            } else {
                Napier.w("$TAG invalid position: $position, itemCount: $itemCount, refreshing adapter")
                refresh()
            }
        } catch (e: Exception) {
            Napier.e("$TAG error notifying position $position: ${e.message}", e)
            refresh()
        }
    }
}
