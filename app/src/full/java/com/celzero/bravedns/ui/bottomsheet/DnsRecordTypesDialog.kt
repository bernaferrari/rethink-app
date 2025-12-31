/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.bottomsheet

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog

class DnsRecordTypesDialog(
    private val context: Context,
    private val persistentState: PersistentState,
    private val onDismiss: () -> Unit
) {
    private val dialog = BottomSheetDialog(context, getThemeId())

    init {
        val composeView = ComposeView(context)
        composeView.setContent {
            RethinkTheme {
                DnsRecordTypesContent()
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        dialog.setOnDismissListener { onDismiss() }
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    @Composable
    private fun DnsRecordTypesContent() {
        var isAutoMode by remember { mutableStateOf(persistentState.dnsRecordTypesAutoMode) }
        val selected = remember {
            mutableStateListOf<String>().apply {
                addAll(getInitialSelection(persistentState.dnsRecordTypesAutoMode))
            }
        }

        val allTypes = remember {
            ResourceRecordTypes.entries.filter { it != ResourceRecordTypes.UNKNOWN }
        }

        val sortedTypes by remember(isAutoMode, selected) {
            derivedStateOf {
                allTypes.sortedWith(
                    compareByDescending<ResourceRecordTypes> {
                        if (isAutoMode) true else selected.contains(it.name)
                    }.thenBy { it.name }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(Color(fetchToggleBtnColors(context, R.color.defaultToggleBtnBg)), RoundedCornerShape(4.dp))
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.cd_allowed_dns_record_types_heading),
                style = MaterialTheme.typography.titleLarge,
                color = Color(UIUtils.fetchColor(context, R.attr.primaryLightColorText)),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            )
            Text(
                text = context.getString(R.string.cd_allowed_dns_record_types_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(UIUtils.fetchColor(context, R.attr.primaryLightColorText)),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )

            ModeToggleRow(
                isAutoMode = isAutoMode,
                onAutoSelected = {
                    isAutoMode = true
                    persistentState.dnsRecordTypesAutoMode = true
                },
                onManualSelected = {
                    isAutoMode = false
                    persistentState.dnsRecordTypesAutoMode = false
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
                    .background(Color.Transparent)
            ) {
                items(sortedTypes) { type ->
                    RecordTypeRow(
                        type = type,
                        isAutoMode = isAutoMode,
                        isSelected = if (isAutoMode) true else selected.contains(type.name),
                        onToggle = {
                            if (isAutoMode) return@RecordTypeRow
                            if (selected.contains(type.name)) {
                                selected.remove(type.name)
                            } else {
                                selected.add(type.name)
                            }
                            persistentState.setAllowedDnsRecordTypes(selected.toSet())
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ModeToggleRow(
        isAutoMode: Boolean,
        onAutoSelected: () -> Unit,
        onManualSelected: () -> Unit
    ) {
        val selectedBg = Color(fetchToggleBtnColors(context, R.color.accentGood))
        val unselectedBg = Color(fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
        val selectedText = Color(UIUtils.fetchColor(context, R.attr.homeScreenHeaderTextColor))
        val unselectedText = Color(UIUtils.fetchColor(context, R.attr.primaryTextColor))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onAutoSelected,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isAutoMode) selectedBg else unselectedBg,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = context.getString(R.string.settings_ip_text_ipv46),
                    color = if (isAutoMode) selectedText else unselectedText
                )
            }
            TextButton(
                onClick = onManualSelected,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (!isAutoMode) selectedBg else unselectedBg,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = context.getString(R.string.lbl_manual),
                    color = if (!isAutoMode) selectedText else unselectedText
                )
            }
        }
    }

    @Composable
    private fun RecordTypeRow(
        type: ResourceRecordTypes,
        isAutoMode: Boolean,
        isSelected: Boolean,
        onToggle: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .background(Color.Transparent)
                .alpha(if (isAutoMode) 0.6f else 1f)
                .clickable(enabled = !isAutoMode) { onToggle() }
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color(UIUtils.fetchColor(context, R.attr.primaryTextColor)),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = type.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(UIUtils.fetchColor(context, R.attr.primaryLightColorText))
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = if (isAutoMode) null else { _ -> onToggle() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (isAutoMode) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    private fun getInitialSelection(autoMode: Boolean): List<String> {
        if (!autoMode) {
            return persistentState.getAllowedDnsRecordTypes().toList()
        }
        val storedSelection = persistentState.allowedDnsRecordTypesString
        if (storedSelection.isNotEmpty()) {
            return storedSelection.split(",").filter { it.isNotEmpty() }
        }
        return listOf(
            ResourceRecordTypes.A.name,
            ResourceRecordTypes.AAAA.name,
            ResourceRecordTypes.CNAME.name,
            ResourceRecordTypes.HTTPS.name,
            ResourceRecordTypes.SVCB.name
        )
    }
}
