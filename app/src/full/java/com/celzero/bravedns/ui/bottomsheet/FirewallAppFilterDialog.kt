/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.ui.bottomsheet

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.activity.AppListActivity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FirewallAppFilterDialog(private val activity: FragmentActivity) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val firewallFilter = AppListActivity.filters.value?.firewallFilter
        ?: AppListActivity.FirewallFilter.ALL

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                FirewallAppFilterContent()
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

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    @Composable
    private fun FirewallAppFilterContent() {
        val scope = rememberCoroutineScope()
        val initialFilters = AppListActivity.filters.value
        var topFilter by remember {
            mutableStateOf(initialFilters?.topLevelFilter ?: AppListActivity.TopLevelFilter.ALL)
        }
        val selectedCategories = remember {
            mutableStateListOf<String>().apply {
                if (initialFilters != null) {
                    addAll(initialFilters.categoryFilters)
                }
            }
        }
        val categories = remember { mutableStateListOf<String>() }

        LaunchedEffect(topFilter) {
            val result = fetchCategories(topFilter)
            categories.clear()
            categories.addAll(result)
            selectedCategories.retainAll(result.toSet())
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(Color(fetchColor(activity, R.attr.border)), MaterialTheme.shapes.small)
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = activity.getString(R.string.fapps_filter_filter_heading),
                style = MaterialTheme.typography.titleMedium,
                color = Color(fetchColor(activity, R.attr.secondaryTextColor)),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TopFilterChip(
                    label = activity.getString(R.string.lbl_all),
                    selected = topFilter == AppListActivity.TopLevelFilter.ALL,
                    onClick = { topFilter = AppListActivity.TopLevelFilter.ALL }
                )
                TopFilterChip(
                    label = activity.getString(R.string.fapps_filter_parent_installed),
                    selected = topFilter == AppListActivity.TopLevelFilter.INSTALLED,
                    onClick = { topFilter = AppListActivity.TopLevelFilter.INSTALLED }
                )
                TopFilterChip(
                    label = activity.getString(R.string.fapps_filter_parent_system),
                    selected = topFilter == AppListActivity.TopLevelFilter.SYSTEM,
                    onClick = { topFilter = AppListActivity.TopLevelFilter.SYSTEM }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = activity.getString(R.string.fapps_filter_categories_heading),
                style = MaterialTheme.typography.titleMedium,
                color = Color(fetchColor(activity, R.attr.secondaryTextColor)),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategories.contains(category),
                        onClick = {
                            if (selectedCategories.contains(category)) {
                                selectedCategories.remove(category)
                            } else {
                                selectedCategories.add(category)
                            }
                        },
                        label = { Text(text = category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        selectedCategories.clear()
                        topFilter = AppListActivity.TopLevelFilter.ALL
                        val cleared = AppListActivity.Filters().apply {
                            topLevelFilter = AppListActivity.TopLevelFilter.ALL
                            firewallFilter = this@FirewallAppFilterDialog.firewallFilter
                        }
                        AppListActivity.filters.postValue(cleared)
                        dialog.dismiss()
                    }
                ) {
                    Text(text = activity.getString(R.string.fapps_filter_clear_btn))
                }
                TextButton(
                    onClick = {
                        val applied = AppListActivity.Filters().apply {
                            topLevelFilter = topFilter
                            firewallFilter = this@FirewallAppFilterDialog.firewallFilter
                            categoryFilters = selectedCategories.toMutableSet()
                        }
                        AppListActivity.filters.postValue(applied)
                        dialog.dismiss()
                    }
                ) {
                    Text(text = activity.getString(R.string.lbl_apply))
                }
            }
        }
    }

    @Composable
    private fun TopFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            }
        )
    }

    private suspend fun fetchCategories(filter: AppListActivity.TopLevelFilter): List<String> {
        return withContext(Dispatchers.IO) {
            when (filter) {
                AppListActivity.TopLevelFilter.ALL -> FirewallManager.getAllCategories()
                AppListActivity.TopLevelFilter.INSTALLED -> FirewallManager.getCategoriesForInstalledApps()
                AppListActivity.TopLevelFilter.SYSTEM -> FirewallManager.getCategoriesForSystemApps()
            }
        }
    }

    private fun fetchColor(context: FragmentActivity, attr: Int): Int {
        return com.celzero.bravedns.util.UIUtils.fetchColor(context, attr)
    }
}
