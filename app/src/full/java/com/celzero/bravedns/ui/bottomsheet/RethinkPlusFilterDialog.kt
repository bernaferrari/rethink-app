/*
 * Copyright 2022 RethinkDNS and its authors
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.rethink.RethinkBlocklistFilterHost
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog

class RethinkPlusFilterDialog(
    context: Context,
    private val filterHost: RethinkBlocklistFilterHost?,
    private val fileTags: List<FileTag>,
    private val persistentState: PersistentState
) {
    private val dialog = BottomSheetDialog(context, getThemeId(context))
    private var filters: RethinkBlocklistState.Filters? = null

    init {
        filters = filterHost?.filterObserver()?.value
        val composeView = ComposeView(context)
        composeView.setContent {
            RethinkTheme {
                RethinkPlusFilterContent()
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
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(context: Context): Int {
        val isDark =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    @Composable
    private fun RethinkPlusFilterContent() {
        val subGroups =
            remember(fileTags) {
                fileTags.map { it.subg }.filter { it.isNotBlank() }.distinct()
            }
        var selectedSubgroups by
            remember { mutableStateOf(filters?.subGroups?.toSet() ?: emptySet()) }
        val borderColor = Color(UIUtils.fetchColor(dialog.context, R.attr.border))

        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Text(
                text = dialog.context.getString(R.string.bsrf_sub_group_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subGroups.forEach { label ->
                    val selected = selectedSubgroups.contains(label)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedSubgroups =
                                if (selected) {
                                    selectedSubgroups - label
                                } else {
                                    selectedSubgroups + label
                                }
                        },
                        label = { Text(text = label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        filterHost?.filterObserver()?.postValue(RethinkBlocklistState.Filters())
                        dialog.dismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = dialog.context.getString(R.string.bsrf_clear_filter))
                }

                Button(
                    onClick = {
                        val updated = filters ?: RethinkBlocklistState.Filters()
                        updated.subGroups.clear()
                        updated.subGroups.addAll(selectedSubgroups)
                        filterHost?.filterObserver()?.postValue(updated)
                        dialog.dismiss()
                    },
                    modifier = Modifier.weight(2f)
                ) {
                    Text(text = dialog.context.getString(R.string.lbl_apply))
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}
