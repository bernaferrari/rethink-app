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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.BottomSheetFirewallSortFilterBinding
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.activity.AppListActivity
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FirewallAppFilterDialog(private val activity: FragmentActivity) : KoinComponent {
    private val binding =
        BottomSheetFirewallSortFilterBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val filters = AppListActivity.Filters()

    init {
        dialog.setContentView(binding.root)
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
        initView()
        initClickListeners()
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

    private fun initView() {
        val currentFilters = AppListActivity.filters.value

        remakeParentFilterChipsUi()
        if (currentFilters == null) {
            applyParentFilter(AppListActivity.TopLevelFilter.ALL.id)
            return
        }

        filters.firewallFilter = currentFilters.firewallFilter
        filters.categoryFilters.addAll(currentFilters.categoryFilters)

        applyParentFilter(currentFilters.topLevelFilter.id)
        setFilter(currentFilters.topLevelFilter, currentFilters.categoryFilters)
    }

    private fun initClickListeners() {
        binding.fsApply.setOnClickListener {
            AppListActivity.filters.postValue(filters)
            dialog.dismiss()
        }

        binding.fsClear.setOnClickListener {
            val newFilters = AppListActivity.filters.value
            if (newFilters == null) {
                dialog.dismiss()
                return@setOnClickListener
            }
            newFilters.categoryFilters.clear()
            newFilters.topLevelFilter = AppListActivity.TopLevelFilter.ALL
            AppListActivity.filters.postValue(newFilters)
            dialog.dismiss()
        }
    }

    private fun setFilter(
        topLevelFilter: AppListActivity.TopLevelFilter,
        categories: MutableSet<String>
    ) {
        val topView: Chip =
            binding.ffaParentChipGroup.findViewWithTag(topLevelFilter.id) ?: return
        binding.ffaParentChipGroup.check(topView.id)
        colorUpChipIcon(topView)

        categories.forEach {
            val childCategory: Chip = binding.ffaChipGroup.findViewWithTag(it) ?: return
            binding.ffaChipGroup.check(childCategory.id)
        }
    }

    private fun remakeParentFilterChipsUi() {
        binding.ffaParentChipGroup.removeAllViews()

        val all =
            makeParentChip(
                AppListActivity.TopLevelFilter.ALL.id,
                activity.getString(R.string.lbl_all),
                true
            )
        val installed =
            makeParentChip(
                AppListActivity.TopLevelFilter.INSTALLED.id,
                activity.getString(R.string.fapps_filter_parent_installed),
                false
            )
        val system =
            makeParentChip(
                AppListActivity.TopLevelFilter.SYSTEM.id,
                activity.getString(R.string.fapps_filter_parent_system),
                false
            )

        binding.ffaParentChipGroup.addView(all)
        binding.ffaParentChipGroup.addView(installed)
        binding.ffaParentChipGroup.addView(system)
    }

    private fun makeParentChip(id: Int, label: String, checked: Boolean): Chip {
        val chip =
            LayoutInflater.from(activity).inflate(R.layout.item_chip_filter, binding.root, false)
                as Chip
        chip.tag = id
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applyParentFilter(button.tag)
                colorUpChipIcon(chip)
            }
        }

        return chip
    }

    private fun colorUpChipIcon(chip: Chip) {
        val colorFilter =
            PorterDuffColorFilter(
                ContextCompat.getColor(activity, R.color.primaryText),
                PorterDuff.Mode.SRC_IN
            )
        chip.checkedIcon?.colorFilter = colorFilter
        chip.chipIcon?.colorFilter = colorFilter
    }

    private fun applyParentFilter(tag: Any) {
        when (tag) {
            AppListActivity.TopLevelFilter.ALL.id -> {
                filters.topLevelFilter = AppListActivity.TopLevelFilter.ALL
                io {
                    val categories = FirewallManager.getAllCategories()
                    uiCtx { remakeChildFilterChipsUi(categories) }
                }
            }
            AppListActivity.TopLevelFilter.INSTALLED.id -> {
                filters.topLevelFilter = AppListActivity.TopLevelFilter.INSTALLED
                io {
                    val categories = FirewallManager.getCategoriesForInstalledApps()
                    uiCtx { remakeChildFilterChipsUi(categories) }
                }
            }
            AppListActivity.TopLevelFilter.SYSTEM.id -> {
                filters.topLevelFilter = AppListActivity.TopLevelFilter.SYSTEM
                io {
                    val categories = FirewallManager.getCategoriesForSystemApps()
                    uiCtx { remakeChildFilterChipsUi(categories) }
                }
            }
        }
    }

    private fun remakeChildFilterChipsUi(categories: List<String>) {
        binding.ffaChipGroup.removeAllViews()
        for (c in categories) {
            if (filters.categoryFilters.contains(c)) {
                binding.ffaChipGroup.addView(makeChildChip(c, true))
            } else {
                binding.ffaChipGroup.addView(makeChildChip(c, false))
            }
        }
    }

    private fun makeChildChip(title: String, checked: Boolean): Chip {
        val chip =
            LayoutInflater.from(activity).inflate(R.layout.item_chip_filter, binding.root, false)
                as Chip
        chip.text = title
        chip.tag = title
        chip.isChecked = checked
        if (checked) colorUpChipIcon(chip)

        chip.setOnCheckedChangeListener { compoundButton: CompoundButton, isSelected: Boolean ->
            applyChildFilter(compoundButton.tag, isSelected)
            colorUpChipIcon(chip)
        }
        return chip
    }

    private fun applyChildFilter(tag: Any, show: Boolean) {
        if (show) {
            filters.categoryFilters.add(tag.toString())
        } else {
            filters.categoryFilters.remove(tag.toString())
        }
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
