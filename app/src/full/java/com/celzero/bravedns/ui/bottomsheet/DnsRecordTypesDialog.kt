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
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.BottomSheetDnsRecordTypesBinding
import com.celzero.bravedns.databinding.ItemDnsRecordTypeBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class DnsRecordTypesDialog(
    private val context: Context,
    private val persistentState: PersistentState,
    private val onDismiss: () -> Unit
) {
    private val binding =
        BottomSheetDnsRecordTypesBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context, getThemeId())

    private val manuallySelectedTypes = mutableSetOf<String>()
    private lateinit var adapter: DnsRecordTypesAdapter
    private var isAutoMode = true

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
        dialog.setOnDismissListener { onDismiss() }
        initView()
        setupAutoModeCard()
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

    private fun initView() {
        isAutoMode = persistentState.dnsRecordTypesAutoMode

        manuallySelectedTypes.clear()
        if (!isAutoMode) {
            manuallySelectedTypes.addAll(persistentState.getAllowedDnsRecordTypes())
        } else {
            val storedSelection = persistentState.allowedDnsRecordTypesString
            if (storedSelection.isNotEmpty()) {
                manuallySelectedTypes.addAll(storedSelection.split(",").filter { it.isNotEmpty() })
            } else {
                manuallySelectedTypes.addAll(
                    setOf(
                        ResourceRecordTypes.A.name,
                        ResourceRecordTypes.AAAA.name,
                        ResourceRecordTypes.CNAME.name,
                        ResourceRecordTypes.HTTPS.name,
                        ResourceRecordTypes.SVCB.name
                    )
                )
            }
        }

        val allTypes = ResourceRecordTypes.entries.filter { it != ResourceRecordTypes.UNKNOWN }

        val sortedTypes = allTypes.sortedWith(compareByDescending<ResourceRecordTypes> {
            if (isAutoMode) true else manuallySelectedTypes.contains(it.name)
        }.thenBy {
            it.name
        })

        adapter = DnsRecordTypesAdapter(sortedTypes, manuallySelectedTypes, isAutoMode)
        binding.drbsRecycler.layoutManager = LinearLayoutManager(context)
        binding.drbsRecycler.adapter = adapter

        updateAutoModeUI(isAutoMode, animate = false)
    }

    private fun setupAutoModeCard() {
        if (isAutoMode) {
            binding.drbsModeToggleGroup.check(binding.drbsAutoModeBtn.id)
            selectToggleBtnUi(binding.drbsAutoModeBtn)
            unselectToggleBtnUi(binding.drbsManualModeBtn)
        } else {
            binding.drbsModeToggleGroup.check(binding.drbsManualModeBtn.id)
            selectToggleBtnUi(binding.drbsManualModeBtn)
            unselectToggleBtnUi(binding.drbsAutoModeBtn)
        }

        binding.drbsModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                binding.drbsAutoModeBtn.id -> {
                    isAutoMode = true
                    selectToggleBtnUi(binding.drbsAutoModeBtn)
                    unselectToggleBtnUi(binding.drbsManualModeBtn)
                    persistentState.dnsRecordTypesAutoMode = true
                    updateAutoModeUI(true, animate = true)
                    adapter.updateAutoMode(true)
                    adapter.notifyDataSetChanged()
                }
                binding.drbsManualModeBtn.id -> {
                    isAutoMode = false
                    selectToggleBtnUi(binding.drbsManualModeBtn)
                    unselectToggleBtnUi(binding.drbsAutoModeBtn)
                    persistentState.dnsRecordTypesAutoMode = false
                    updateAutoModeUI(false, animate = true)
                    adapter.updateAutoMode(false)
                    adapter.sortBySelection()
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun selectToggleBtnUi(b: MaterialButton) {
        b.backgroundTintList =
            ColorStateList.valueOf(fetchToggleBtnColors(context, R.color.accentGood))
        b.setTextColor(UIUtils.fetchColor(context, R.attr.homeScreenHeaderTextColor))
    }

    private fun unselectToggleBtnUi(b: MaterialButton) {
        b.backgroundTintList =
            ColorStateList.valueOf(fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
        b.setTextColor(UIUtils.fetchColor(context, R.attr.primaryTextColor))
    }

    private fun updateAutoModeUI(autoMode: Boolean, animate: Boolean) {
        if (autoMode) {
            if (animate) {
                binding.drbsManualSection.animate()
                    .alpha(0.4f)
                    .setDuration(200)
                    .start()
            } else {
                binding.drbsManualSection.alpha = 0.4f
            }
            binding.drbsManualSection.isEnabled = false
        } else {
            if (animate) {
                binding.drbsManualSection.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                binding.drbsManualSection.alpha = 1f
            }
            binding.drbsManualSection.isEnabled = true
        }
    }

    inner class DnsRecordTypesAdapter(
        private var types: List<ResourceRecordTypes>,
        private val selected: MutableSet<String>,
        private var autoMode: Boolean
    ) : RecyclerView.Adapter<DnsRecordTypesAdapter.ViewHolder>() {

        fun updateAutoMode(newAutoMode: Boolean) {
            autoMode = newAutoMode
            if (!newAutoMode) {
                sortBySelection()
            }
        }

        fun sortBySelection() {
            types = types.sortedWith(compareByDescending<ResourceRecordTypes> {
                selected.contains(it.name)
            }.thenBy {
                it.name
            })
        }

        inner class ViewHolder(private val itemBinding: ItemDnsRecordTypeBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(type: ResourceRecordTypes, isLocked: Boolean) {
                itemBinding.itemDnsRecordTypeName.text = type.name
                itemBinding.itemDnsRecordTypeDesc.text = type.desc

                if (isLocked) {
                    itemBinding.itemDnsRecordTypeCheckbox.isChecked = true
                    itemBinding.itemDnsRecordTypeCheckbox.isEnabled = false
                    itemBinding.root.isClickable = false
                    itemBinding.root.alpha = 0.6f
                    itemBinding.root.background = null
                } else {
                    itemBinding.itemDnsRecordTypeCheckbox.isChecked = selected.contains(type.name)
                    itemBinding.itemDnsRecordTypeCheckbox.isEnabled = true
                    itemBinding.root.isClickable = true
                    itemBinding.root.alpha = 1f

                    val typedValue = TypedValue()
                    itemBinding.root.context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        typedValue,
                        true
                    )
                    itemBinding.root.setBackgroundResource(typedValue.resourceId)

                    itemBinding.root.setOnClickListener {
                        val isChecked = !itemBinding.itemDnsRecordTypeCheckbox.isChecked
                        itemBinding.itemDnsRecordTypeCheckbox.isChecked = isChecked

                        if (isChecked) {
                            selected.add(type.name)
                        } else {
                            selected.remove(type.name)
                        }

                        persistentState.setAllowedDnsRecordTypes(selected)

                        sortBySelection()
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDnsRecordTypeBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(types[position], autoMode)
        }

        override fun getItemCount(): Int = types.size
    }
}
