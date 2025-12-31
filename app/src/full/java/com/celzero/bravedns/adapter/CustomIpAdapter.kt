/*
 * Copyright 2023 RethinkDNS and its authors
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
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.ui.activity.CustomRulesActivity
import com.celzero.bravedns.ui.bottomsheet.CustomIpRulesDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getCountryCode
import com.celzero.bravedns.util.Utilities.getFlag
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomIpAdapter(
    private val context: Context,
    private val type: CustomRulesActivity.RULES,
    private val eventLogger: EventLogger
) :
    PagingDataAdapter<CustomIp, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private val selectedItems = mutableSetOf<CustomIp>()
    private var isSelectionMode = false

    companion object {
        private const val TAG = "CustomIpAdapter"
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<CustomIp>() {
                override fun areItemsTheSame(oldConnection: CustomIp, newConnection: CustomIp) =
                    oldConnection.uid == newConnection.uid &&
                        oldConnection.ipAddress == newConnection.ipAddress &&
                        oldConnection.port == newConnection.port

                override fun areContentsTheSame(oldConnection: CustomIp, newConnection: CustomIp) =
                    oldConnection.status == newConnection.status &&
                        oldConnection.proxyCC == newConnection.proxyCC &&
                        oldConnection.proxyId == newConnection.proxyId &&
                        oldConnection.modifiedDateTime == newConnection.modifiedDateTime
            }
    }

    data class ToggleBtnUi(val txtColor: Int, val bgColor: Int)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return if (viewType == VIEW_TYPE_HEADER) {
            CustomIpsViewHolderWithHeader(composeView)
        } else {
            CustomIpsViewHolderWithoutHeader(composeView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val customIp: CustomIp = getItem(position) ?: return
        when (holder) {
            is CustomIpsViewHolderWithHeader -> holder.update(customIp)
            is CustomIpsViewHolderWithoutHeader -> holder.update(customIp)
            else -> Napier.w("$TAG unknown view holder in CustomIpAdapter")
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (type == CustomRulesActivity.RULES.APP_SPECIFIC_RULES) {
            return VIEW_TYPE_ITEM
        }

        return if (position == 0) {
            VIEW_TYPE_HEADER
        } else if (getItem(position - 1)?.uid != getItem(position)?.uid) {
            VIEW_TYPE_HEADER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    fun getSelectedItems(): List<CustomIp> = selectedItems.toList()

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        try {
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        } catch (e: Exception) {
            Napier.e("$TAG error clearing selection: ${e.message}", e)
        }
    }

    private fun getToggleBtnUiParams(id: IpRulesManager.IpRuleStatus): ToggleBtnUi {
        return when (id) {
            IpRulesManager.IpRuleStatus.NONE ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextNeutral),
                    fetchColor(context, R.attr.chipBgColorNeutral)
                )
            IpRulesManager.IpRuleStatus.BLOCK ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextNegative),
                    fetchColor(context, R.attr.chipBgColorNegative)
                )
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextPositive),
                    fetchColor(context, R.attr.chipBgColorPositive)
                )
            IpRulesManager.IpRuleStatus.TRUST ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextPositive),
                    fetchColor(context, R.attr.chipBgColorPositive)
                )
        }
    }

    private fun findSelectedIpRule(ruleId: Int): IpRulesManager.IpRuleStatus? {
        return when (ruleId) {
            IpRulesManager.IpRuleStatus.NONE.id -> IpRulesManager.IpRuleStatus.NONE
            IpRulesManager.IpRuleStatus.BLOCK.id -> IpRulesManager.IpRuleStatus.BLOCK
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL.id -> IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL
            IpRulesManager.IpRuleStatus.TRUST.id -> IpRulesManager.IpRuleStatus.TRUST
            else -> null
        }
    }

    inner class CustomIpsViewHolderWithHeader(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(ci: CustomIp) {
            io {
                val appNames = FirewallManager.getAppNamesByUid(ci.uid)
                val appName = getAppName(ci.uid, appNames)
                val appInfo = FirewallManager.getAppInfoByUid(ci.uid)
                val iconDrawable =
                    Utilities.getIcon(
                        context,
                        appInfo?.packageName ?: "",
                        appInfo?.appName ?: ""
                    )
                uiCtx {
                    composeView.setContent {
                        RethinkTheme {
                            IpRow(
                                customIp = ci,
                                showHeader = true,
                                appName = appName,
                                appIcon = iconDrawable
                            )
                        }
                    }
                }
            }
        }
    }

    inner class CustomIpsViewHolderWithoutHeader(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(ci: CustomIp) {
            composeView.setContent {
                RethinkTheme {
                    IpRow(
                        customIp = ci,
                        showHeader = false,
                        appName = "",
                        appIcon = null
                    )
                }
            }
        }
    }

    @Composable
    private fun IpRow(customIp: CustomIp, showHeader: Boolean, appName: String, appIcon: Drawable?) {
        val isSelected = selectedItems.contains(customIp)
        val status = findSelectedIpRule(customIp.status) ?: return
        val flag = updateFlagIfAvailable(customIp)
        val (statusText, statusIconText) = statusInfo(status, customIp.modifiedDateTime)
        val toggleUi = getToggleBtnUiParams(status)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                toggleSelection(customIp)
                            } else {
                                showBtmSheet(customIp)
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedItems.add(customIp)
                            try {
                                if (itemCount > 0) {
                                    notifyItemRangeChanged(0, itemCount)
                                }
                            } catch (e: Exception) {
                                Napier.e("$TAG error in long click: ${e.message}", e)
                            }
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AndroidView(
                        factory = { ctx -> AppCompatImageView(ctx) },
                        update = { imageView ->
                            Glide.with(imageView)
                                .load(appIcon)
                                .error(Utilities.getDefaultIcon(context))
                                .into(imageView)
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { openAppWiseRulesActivity(customIp.uid) }) {
                        Text(text = context.getString(R.string.ssv_see_more))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { toggleSelection(customIp) },
                    enabled = isSelectionMode
                )
                Text(text = flag, style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            context.getString(
                                R.string.ci_ip_label,
                                customIp.ipAddress,
                                customIp.port.toString()
                            ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = statusIconText,
                    color = Color(toggleUi.txtColor),
                    modifier =
                        Modifier
                            .background(Color(toggleUi.bgColor))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                IconButton(onClick = { showEditIpDialog(customIp) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_icon),
                        contentDescription = null
                    )
                }
                IconButton(onClick = { showBtmSheet(customIp) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription = null
                    )
                }
            }
            Spacer(modifier = Modifier.fillMaxWidth())
        }
    }

    private fun showBtmSheet(customIp: CustomIp) {
        val activity = context as? CustomRulesActivity
        if (activity == null) {
            Napier.w("$TAG invalid context for custom ip dialog")
            return
        }
        CustomIpRulesDialog(activity, customIp).show()
    }

    private fun toggleSelection(item: CustomIp) {
        if (!isSelectionMode) return
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        try {
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        } catch (_: Exception) {
            notifyDataSetChanged()
        }
    }

    private fun openAppWiseRulesActivity(uid: Int) {
        val intent = Intent(context, CustomRulesActivity::class.java)
        intent.putExtra(
            Constants.VIEW_PAGER_SCREEN_TO_LOAD,
            CustomRulesActivity.Tabs.IP_RULES.screen
        )
        intent.putExtra(Constants.INTENT_UID, uid)
        context.startActivity(intent)
    }

    private fun getAppName(uid: Int, appNames: List<String>): String {
        if (uid == UID_EVERYBODY) {
            return context
                .getString(R.string.firewall_act_universal_tab)
                .replaceFirstChar(Char::titlecase)
        }

        if (appNames.isEmpty()) {
            return context.getString(R.string.network_log_app_name_unknown) + " ($uid)"
        }

        val packageCount = appNames.count()
        return if (packageCount >= 2) {
            context.getString(
                R.string.ctbs_app_other_apps,
                appNames[0],
                packageCount.minus(1).toString()
            )
        } else {
            appNames[0]
        }
    }

    private fun updateFlagIfAvailable(ip: CustomIp): String {
        if (ip.wildcard) return "--"
        val inetAddr =
            try {
                IPAddressString(ip.ipAddress).hostAddress.toInetAddress()
            } catch (_: Exception) {
                null
            }
        return getFlag(getCountryCode(inetAddr, context))
    }

    private fun statusInfo(status: IpRulesManager.IpRuleStatus, modifiedTs: Long): Pair<String, String> {
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - modifiedTs
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        return when (status) {
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.ci_bypass_universal_txt),
                    time
                ) to context.getString(R.string.ci_bypass_universal_initial)
            IpRulesManager.IpRuleStatus.BLOCK ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.lbl_blocked),
                    time
                ) to context.getString(R.string.ci_blocked_initial)
            IpRulesManager.IpRuleStatus.NONE ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.ci_no_rule_txt),
                    time
                ) to context.getString(R.string.ci_no_rule_initial)
            IpRulesManager.IpRuleStatus.TRUST ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.ci_trust_txt),
                    time
                ) to context.getString(R.string.ci_trust_initial)
        }
    }

    private fun showEditIpDialog(customIp: CustomIp) {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val composeView = ComposeView(context)
        composeView.setContent {
            RethinkTheme {
                EditCustomIpDialogContent(
                    customIp = customIp,
                    onDismiss = { dialog.dismiss() },
                    onSubmit = { ipText, status, onError ->
                        handleIp(ipText, customIp, status, onError)
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    private fun handleIp(
        input: String,
        customIp: CustomIp,
        status: IpRulesManager.IpRuleStatus,
        onError: (String) -> Unit
    ) {
        ui {
            val ipString = Utilities.removeLeadingAndTrailingDots(input)
            var ip: IPAddress? = null
            var port: Int? = null

            ioCtx {
                val ipPair = IpRulesManager.getIpNetPort(ipString)
                ip = ipPair.first
                port = ipPair.second
            }

            if (ip == null || ipString.isEmpty()) {
                onError(context.getString(R.string.ci_dialog_error_invalid_ip))
                return@ui
            }
            Napier.i("$TAG ip: $ip, port: $port, status: $status")
            updateCustomIp(customIp, ip, port, status)
        }
    }

    private fun updateCustomIp(
        prev: CustomIp,
        ipString: IPAddress?,
        port: Int?,
        status: IpRulesManager.IpRuleStatus
    ) {
        if (ipString == null) return
        io { IpRulesManager.replaceIpRule(prev, ipString, port, status, "", "") }
        logEvent("Updated Custom IP rule: Prev[$prev], New[IP: $ipString, Port: ${port ?: "0"}, Status: $status]")
    }

    @Composable
    private fun EditCustomIpDialogContent(
        customIp: CustomIp,
        onDismiss: () -> Unit,
        onSubmit: (String, IpRulesManager.IpRuleStatus, (String) -> Unit) -> Unit
    ) {
        val initialText =
            if (customIp.port != 0) {
                IpRulesManager.joinIpNetPort(customIp.ipAddress, customIp.port)
            } else {
                customIp.ipAddress
            }
        var ipText by remember { mutableStateOf(initialText) }
        var errorText by remember { mutableStateOf("") }
        val trustLabel =
            if (customIp.uid == UID_EVERYBODY) {
                context.getString(R.string.bypass_universal)
            } else {
                context.getString(R.string.ci_trust_rule)
            }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = context.getString(R.string.ci_dialog_title), style = MaterialTheme.typography.titleMedium)
            Text(text = context.getString(R.string.ci_dialog_desc), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = ipText,
                onValueChange = {
                    ipText = it
                    if (errorText.isNotBlank()) errorText = ""
                },
                label = { Text(text = context.getString(R.string.ci_dialog_edittext_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = context.getString(R.string.fapps_info_dialog_positive_btn))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        errorText = ""
                        val status =
                            if (customIp.uid == UID_EVERYBODY) {
                                IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL
                            } else {
                                IpRulesManager.IpRuleStatus.TRUST
                            }
                        onSubmit(ipText, status) { errorText = it }
                    }
                ) {
                    Text(text = trustLabel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        errorText = ""
                        onSubmit(ipText, IpRulesManager.IpRuleStatus.BLOCK) { errorText = it }
                    }
                ) {
                    Text(text = context.getString(R.string.block))
                }
            }
        }
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom IP", EventSource.UI, false, details)
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private fun ui(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.Main) { f() }
    }
}
