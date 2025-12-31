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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.databinding.BottomSheetRethinkPlusFilterBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.rethink.RethinkBlocklistFilterHost
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip

class RethinkPlusFilterDialog(
    context: Context,
    private val filterHost: RethinkBlocklistFilterHost?,
    private val fileTags: List<FileTag>,
    private val persistentState: PersistentState
) {
    private val binding =
        BottomSheetRethinkPlusFilterBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context, getThemeId(context))
    private var filters: RethinkBlocklistState.Filters? = null

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

    private fun getThemeId(context: Context): Int {
        val isDark =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun initView() {
        filters = filterHost?.filterObserver()?.value
        makeChipSubGroup(fileTags)
    }

    private fun makeChipSubGroup(ft: List<FileTag>) {
        binding.rpfFilterChipSubGroup.removeAllViews()
        ft.distinctBy { it.subg }
            .forEach {
                if (it.subg.isBlank()) return@forEach

                val checked = filters?.subGroups?.contains(it.subg) == true
                binding.rpfFilterChipSubGroup.addView(remakeChipSubgroup(it.subg, checked))
            }
    }

    private fun initClickListeners() {
        binding.rpfApply.setOnClickListener {
            dialog.dismiss()
            if (filters == null) return@setOnClickListener

            filterHost?.filterObserver()?.postValue(filters)
        }

        binding.rpfClear.setOnClickListener {
            filterHost?.filterObserver()?.postValue(RethinkBlocklistState.Filters())
            dialog.dismiss()
        }
    }

    private fun remakeChipSubgroup(label: String, checked: Boolean): Chip {
        val chip =
            LayoutInflater.from(binding.root.context)
                .inflate(R.layout.item_chip_filter, binding.root, false) as Chip
        chip.tag = label
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applySubgroupFilter(button.tag as String)
                colorUpChipIcon(chip)
            } else {
                removeSubgroupFilter(button.tag as String)
            }
        }

        return chip
    }

    private fun colorUpChipIcon(chip: Chip) {
        val colorFilter =
            PorterDuffColorFilter(
                ContextCompat.getColor(binding.root.context, R.color.primaryText),
                PorterDuff.Mode.SRC_IN
            )
        chip.checkedIcon?.colorFilter = colorFilter
        chip.chipIcon?.colorFilter = colorFilter
    }

    private fun applySubgroupFilter(tag: String) {
        if (filters == null) {
            filters = RethinkBlocklistState.Filters()
        }
        filters!!.subGroups.add(tag)
    }

    private fun removeSubgroupFilter(tag: String) {
        if (filters == null) return

        filters!!.subGroups.remove(tag)
    }
}
