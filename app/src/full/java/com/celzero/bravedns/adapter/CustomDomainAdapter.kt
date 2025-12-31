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
import android.widget.Toast
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
import androidx.compose.material3.FilterChip
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
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.DomainRulesManager.isValidDomain
import com.celzero.bravedns.service.DomainRulesManager.isWildCardEntry
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.activity.CustomRulesActivity
import com.celzero.bravedns.ui.bottomsheet.CustomDomainRulesDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomDomainAdapter(
    val context: Context,
    val rule: CustomRulesActivity.RULES,
    val eventLogger: EventLogger
) :
    PagingDataAdapter<CustomDomain, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private val selectedItems = mutableSetOf<CustomDomain>()
    private var isSelectionMode = false
    private lateinit var adapter: CustomDomainAdapter
    private data class ToggleBtnUi(val txtColor: Int, val bgColor: Int)

    companion object {
        private const val TAG = "CustomDomainAdapter"
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<CustomDomain>() {
                override fun areItemsTheSame(
                    oldConnection: CustomDomain,
                    newConnection: CustomDomain
                ): Boolean {
                    return (oldConnection.domain == newConnection.domain &&
                        oldConnection.status == newConnection.status &&
                        oldConnection.proxyId == newConnection.proxyId &&
                        oldConnection.proxyCC == newConnection.proxyCC)
                }

                override fun areContentsTheSame(
                    oldConnection: CustomDomain,
                    newConnection: CustomDomain
                ): Boolean {
                    return oldConnection == newConnection
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        adapter = this
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return if (viewType == VIEW_TYPE_HEADER) {
            CustomDomainViewHolderWithHeader(composeView)
        } else {
            CustomDomainViewHolderWithoutHeader(composeView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val customDomain: CustomDomain = getItem(position) ?: return
        when (holder) {
            is CustomDomainViewHolderWithHeader -> holder.update(customDomain)
            is CustomDomainViewHolderWithoutHeader -> holder.update(customDomain)
            else -> Napier.w("$TAG unknown view holder in CustomDomainRulesAdapter")
        }
    }

    fun getSelectedItems(): List<CustomDomain> = selectedItems.toList()

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

    override fun getItemViewType(position: Int): Int {
        if (rule == CustomRulesActivity.RULES.APP_SPECIFIC_RULES) {
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

    private fun showEditDomainDialog(customDomain: CustomDomain) {
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
                EditCustomDomainDialogContent(
                    customDomain = customDomain,
                    onDismiss = { dialog.dismiss() },
                    onSubmit = { domain, type, status, onError ->
                        handleDomain(domain, type, customDomain, status, onError)
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    private fun handleDomain(
        urlInput: String,
        selectedType: DomainRulesManager.DomainType,
        prevDomain: CustomDomain,
        status: DomainRulesManager.Status,
        onError: (String) -> Unit
    ) {
        val url = urlInput.trim()
        val extractedHost = extractHost(url) ?: run {
            onError(context.getString(R.string.cd_dialog_error_invalid_domain))
            Napier.v("$TAG invalid domain: $url")
            return
        }
        when (selectedType) {
            DomainRulesManager.DomainType.WILDCARD -> {
                if (!isWildCardEntry(extractedHost)) {
                    onError(context.getString(R.string.cd_dialog_error_invalid_wildcard))
                    Napier.v("$TAG invalid wildcard domain: $url")
                    return
                }
            }

            DomainRulesManager.DomainType.DOMAIN -> {
                if (!isValidDomain(extractedHost)) {
                    onError(context.getString(R.string.cd_dialog_error_invalid_domain))
                    Napier.v("$TAG invalid domain: $url")
                    return
                }
            }
        }

        io {
            Napier.v("$TAG domain: $extractedHost, type: $selectedType")
            insertDomain(
                Utilities.removeLeadingAndTrailingDots(extractedHost),
                selectedType,
                prevDomain,
                status
            )
        }
    }

    private fun extractHost(input: String): String? {
        return DomainRulesManager.extractHost(input)
    }

    private suspend fun insertDomain(
        domain: String,
        type: DomainRulesManager.DomainType,
        prevDomain: CustomDomain,
        status: DomainRulesManager.Status
    ) {
        Napier.i("$TAG insert/update domain: $domain, type: $type")
        DomainRulesManager.updateDomainRule(domain, status, type, prevDomain)
        uiCtx {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.cd_toast_edit),
                Toast.LENGTH_SHORT
            )
        }
        logEvent("Custom domain insert/update $domain, status: $status")
    }

    @Composable
    private fun EditCustomDomainDialogContent(
        customDomain: CustomDomain,
        onDismiss: () -> Unit,
        onSubmit: (String, DomainRulesManager.DomainType, DomainRulesManager.Status, (String) -> Unit) -> Unit
    ) {
        var selectedType by remember {
            mutableStateOf(DomainRulesManager.DomainType.getType(customDomain.type))
        }
        var domainText by remember { mutableStateOf(customDomain.domain) }
        var errorText by remember { mutableStateOf("") }

        if (domainText.startsWith("*") || domainText.startsWith(".")) {
            selectedType = DomainRulesManager.DomainType.WILDCARD
        } else {
            selectedType = DomainRulesManager.DomainType.DOMAIN
        }

        val inputLabel =
            context.getString(
                R.string.cd_dialog_edittext_hint,
                context.getString(
                    if (selectedType == DomainRulesManager.DomainType.WILDCARD) {
                        R.string.lbl_wildcard
                    } else {
                        R.string.lbl_domain
                    }
                )
            )

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = context.getString(R.string.cd_dialog_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == DomainRulesManager.DomainType.DOMAIN,
                    onClick = { selectedType = DomainRulesManager.DomainType.DOMAIN },
                    label = { Text(text = context.getString(R.string.lbl_domain)) }
                )
                FilterChip(
                    selected = selectedType == DomainRulesManager.DomainType.WILDCARD,
                    onClick = { selectedType = DomainRulesManager.DomainType.WILDCARD },
                    label = { Text(text = context.getString(R.string.lbl_wildcard)) }
                )
            }
            OutlinedTextField(
                value = domainText,
                onValueChange = { value ->
                    domainText = value
                    selectedType =
                        if (value.startsWith("*") || value.startsWith(".")) {
                            DomainRulesManager.DomainType.WILDCARD
                        } else {
                            DomainRulesManager.DomainType.DOMAIN
                        }
                },
                label = { Text(text = inputLabel) },
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
                        onSubmit(domainText, selectedType, DomainRulesManager.Status.TRUST) {
                            errorText = it
                        }
                    }
                ) {
                    Text(text = context.getString(R.string.ci_trust_rule))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        errorText = ""
                        onSubmit(domainText, selectedType, DomainRulesManager.Status.BLOCK) {
                            errorText = it
                        }
                    }
                ) {
                    Text(text = context.getString(R.string.block))
                }
            }
        }
    }

    inner class CustomDomainViewHolderWithHeader(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(cd: CustomDomain) {
            io {
                val appInfo = FirewallManager.getAppInfoByUid(cd.uid)
                val appNames = FirewallManager.getAppNamesByUid(cd.uid)
                val appName = getAppName(cd.uid, appNames)
                val iconDrawable =
                    Utilities.getIcon(
                        context,
                        appInfo?.packageName ?: "",
                        appInfo?.appName ?: ""
                    )
                uiCtx {
                    composeView.setContent {
                        RethinkTheme {
                            DomainRow(
                                customDomain = cd,
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

    inner class CustomDomainViewHolderWithoutHeader(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(cd: CustomDomain) {
            composeView.setContent {
                RethinkTheme {
                    DomainRow(
                        customDomain = cd,
                        showHeader = false,
                        appName = "",
                        appIcon = null
                    )
                }
            }
        }
    }

    @Composable
    private fun DomainRow(
        customDomain: CustomDomain,
        showHeader: Boolean,
        appName: String,
        appIcon: Drawable?
    ) {
        val isSelected = selectedItems.contains(customDomain)
        val status = DomainRulesManager.Status.getStatus(customDomain.status)
        val (statusText, statusIconText) = statusInfo(status, customDomain.modifiedTs)
        val toggleUi = toggleBtnUi(status)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                toggleSelection(customDomain)
                            } else {
                                showButtonsBottomSheet(customDomain)
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedItems.add(customDomain)
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
                    TextButton(onClick = { openAppWiseRulesActivity(customDomain.uid) }) {
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
                    onCheckedChange = { toggleSelection(customDomain) },
                    enabled = isSelectionMode
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customDomain.domain,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = statusIconText,
                    color = Color(toggleUi.txtColor),
                    modifier =
                        Modifier
                            .background(Color(toggleUi.bgColor))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                IconButton(onClick = { showEditDomainDialog(customDomain) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_icon),
                        contentDescription = null
                    )
                }
                IconButton(onClick = { showButtonsBottomSheet(customDomain) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription = null
                    )
                }
            }
        }
    }

    private fun statusInfo(status: DomainRulesManager.Status, modifiedTs: Long): Pair<String, String> {
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
            DomainRulesManager.Status.TRUST ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.ci_trust_txt),
                    time
                ) to context.getString(R.string.ci_trust_initial)
            DomainRulesManager.Status.BLOCK ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.lbl_blocked),
                    time
                ) to context.getString(R.string.cd_blocked_initial)
            DomainRulesManager.Status.NONE ->
                context.getString(
                    R.string.ci_desc,
                    context.getString(R.string.cd_no_rule_txt),
                    time
                ) to context.getString(R.string.cd_no_rule_initial)
        }
    }

    private fun toggleSelection(item: CustomDomain) {
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
            CustomRulesActivity.Tabs.DOMAIN_RULES.screen
        )
        intent.putExtra(Constants.INTENT_UID, uid)
        context.startActivity(intent)
    }

    private fun getAppName(uid: Int, appNames: List<String>): String {
        if (uid == Constants.UID_EVERYBODY) {
            return context
                .getString(R.string.firewall_act_universal_tab)
                .replaceFirstChar(Char::titlecase)
        }

        if (appNames.isEmpty()) {
            return context.getString(R.string.network_log_app_name_unnamed, "($uid)")
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

    private fun toggleBtnUi(id: DomainRulesManager.Status): ToggleBtnUi {
        return when (id) {
            DomainRulesManager.Status.NONE ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextNeutral),
                    fetchColor(context, R.attr.chipBgColorNeutral)
                )
            DomainRulesManager.Status.BLOCK ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextNegative),
                    fetchColor(context, R.attr.chipBgColorNegative)
                )
            DomainRulesManager.Status.TRUST ->
                ToggleBtnUi(
                    fetchColor(context, R.attr.chipTextPositive),
                    fetchColor(context, R.attr.chipBgColorPositive)
                )
        }
    }

    private fun showButtonsBottomSheet(customDomain: CustomDomain) {
        val activity = context as? CustomRulesActivity
        if (activity == null) {
            Napier.w("$TAG invalid context for custom domain dialog")
            return
        }
        CustomDomainRulesDialog(activity, customDomain).show()
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom Domain", EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
