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
package com.celzero.bravedns.ui.activity

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.paging.filter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.LocalAdvancedViewAdapter
import com.celzero.bravedns.adapter.LocalSimpleViewAdapter
import com.celzero.bravedns.adapter.RemoteAdvancedViewAdapter
import com.celzero.bravedns.adapter.RemoteSimpleViewAdapter
import com.celzero.bravedns.adapter.RethinkEndpointAdapter
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator.Companion.CUSTOM_DOWNLOAD
import com.celzero.bravedns.customdownloader.RemoteBlocklistCoordinator
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.databinding.FragmentRethinkBlocklistBinding
import com.celzero.bravedns.databinding.FragmentRethinkListBinding
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants.Companion.DOWNLOAD_TAG
import com.celzero.bravedns.download.DownloadConstants.Companion.FILE_TAG
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.RethinkBlocklistManager.RethinkBlocklistType.Companion.getType
import com.celzero.bravedns.service.RethinkBlocklistManager.getStamp
import com.celzero.bravedns.service.RethinkBlocklistManager.getTagsFromStamp
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.RethinkPlusFilterDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.rethink.RethinkBlocklistFilterHost
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.DEAD_PACK
import com.celzero.bravedns.util.Constants.Companion.DEFAULT_RDNS_REMOTE_DNS_NAMES
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.Constants.Companion.RETHINK_STAMP_VERSION
import com.celzero.bravedns.util.CustomLinearLayoutManager
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getRemoteBlocklistStamp
import com.celzero.bravedns.util.Utilities.hasLocalBlocklists
import com.celzero.bravedns.util.Utilities.hasRemoteBlocklists
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RethinkEndpointViewModel
import com.celzero.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.celzero.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.regex.Pattern

class ConfigureRethinkBasicActivity : AppCompatActivity(), RethinkBlocklistFilterHost {
    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()
    private val appConfig by inject<AppConfig>()

    private val rethinkEndpointViewModel: RethinkEndpointViewModel by viewModel()
    private val remoteFileTagViewModel: RethinkRemoteFileTagViewModel by viewModel()
    private val localFileTagViewModel: RethinkLocalFileTagViewModel by viewModel()
    private val remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel by viewModel()
    private val localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel by viewModel()

    private var blocklistBinding: FragmentRethinkBlocklistBinding? = null
    private var listBinding: FragmentRethinkListBinding? = null

    private var blocklistType: RethinkBlocklistManager.RethinkBlocklistType =
        RethinkBlocklistManager.RethinkBlocklistType.REMOTE
    private var remoteName: String = ""
    private var remoteUrl: String = ""
    private var modifiedStamp: String = ""

    private val filters = MutableLiveData<RethinkBlocklistState.Filters>()

    private var advanceRemoteViewAdapter: RemoteAdvancedViewAdapter? = null
    private var advanceLocalViewAdapter: LocalAdvancedViewAdapter? = null
    private var localSimpleViewAdapter: LocalSimpleViewAdapter? = null
    private var remoteSimpleViewAdapter: RemoteSimpleViewAdapter? = null

    private var blocklistBackCallbackAdded = false

    private var listLayoutManager: RecyclerView.LayoutManager? = null
    private var listAdapter: RethinkEndpointAdapter? = null

    private var uid: Int = Constants.MISSING_UID

    enum class FragmentLoader {
        REMOTE,
        LOCAL,
        DB_LIST
    }

    companion object {
        const val INTENT = "RethinkDns_Intent"
        const val RETHINK_BLOCKLIST_TYPE = "RethinkBlocklistType"
        const val RETHINK_BLOCKLIST_NAME = "RethinkBlocklistName"
        const val RETHINK_BLOCKLIST_URL = "RethinkBlocklistUrl"
        const val UID = "UID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        Logger.v(LOG_TAG_UI, "init configure rethink base activity")
        val type = intent.getIntExtra(INTENT, FragmentLoader.REMOTE.ordinal)
        val screen = FragmentLoader.entries[type]

        uid = intent.getIntExtra(UID, Constants.MISSING_UID)
        remoteName = intent.getStringExtra(RETHINK_BLOCKLIST_NAME) ?: ""
        remoteUrl = intent.getStringExtra(RETHINK_BLOCKLIST_URL) ?: ""

        blocklistType =
            if (screen == FragmentLoader.LOCAL) {
                RethinkBlocklistManager.RethinkBlocklistType.LOCAL
            } else {
                RethinkBlocklistManager.RethinkBlocklistType.REMOTE
            }

        setContent {
            RethinkTheme {
                RethinkBasicContent(screen)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        blocklistBinding = null
        listBinding = null
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun RethinkBasicContent(screen: FragmentLoader) {
        val lifecycleOwner = LocalLifecycleOwner.current
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val view =
                    when (screen) {
                        FragmentLoader.DB_LIST -> {
                            val binding =
                                listBinding ?: FragmentRethinkListBinding.inflate(LayoutInflater.from(context))
                            if (listBinding == null) {
                                listBinding = binding
                                initRethinkList(binding, lifecycleOwner)
                            }
                            binding.root
                        }
                        else -> {
                            val binding =
                                blocklistBinding
                                    ?: FragmentRethinkBlocklistBinding.inflate(LayoutInflater.from(context))
                            if (blocklistBinding == null) {
                                blocklistBinding = binding
                                initBlocklist(binding, lifecycleOwner)
                            }
                            binding.root
                        }
                    }
                view
            }
        )
    }

    override fun filterObserver(): MutableLiveData<RethinkBlocklistState.Filters> {
        return filters
    }

    private fun initBlocklist(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        modifiedStamp = getStamp()

        val typeName =
            if (blocklistType.isLocal()) {
                getString(R.string.lbl_on_device)
            } else {
                getString(R.string.rdns_plus)
            }
        binding.lbBlocklistApplyBtn.text =
            getString(R.string.ct_ip_details, getString(R.string.lbl_apply), typeName)

        io {
            val flags = getTagsFromStamp(modifiedStamp, blocklistType)
            RethinkBlocklistState.updateFileTagList(flags)
        }

        hasBlocklist(binding, lifecycleOwner)

        selectToggleBtnUi(binding.lbSimpleToggleBtn)
        unselectToggleBtnUi(binding.lbAdvToggleBtn)

        remakeFilterChipsUi(binding)
        initBlocklistObservers(binding, lifecycleOwner)
        initBlocklistClickListeners(binding, lifecycleOwner)
    }

    private fun initBlocklistObservers(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        if (blocklistType.isLocal()) {
            observeWorkManager(binding, lifecycleOwner)
        }

        RethinkBlocklistState.selectedFileTags.observe(lifecycleOwner) {
            if (it == null) return@observe
            io { modifiedStamp = getStamp(it, blocklistType) }
        }

        filters.observe(lifecycleOwner) {
            if (it == null) return@observe

            if (blocklistType.isRemote()) {
                remoteFileTagViewModel.setFilter(it)
                binding.lbAdvancedRecycler.smoothScrollToPosition(0)
            } else {
                localFileTagViewModel.setFilter(it)
                binding.lbAdvancedRecycler.smoothScrollToPosition(0)
            }
            updateFilteredTxtUi(binding, it)
        }
    }

    private fun updateFilteredTxtUi(binding: FragmentRethinkBlocklistBinding, filter: RethinkBlocklistState.Filters) {
        if (filter.subGroups.isEmpty()) {
            binding.lbAdvancedFilterLabelTv.text =
                htmlToSpannedText(
                    getString(R.string.rt_filter_desc, filter.filterSelected.name.lowercase())
                )
        } else {
            binding.lbAdvancedFilterLabelTv.text =
                htmlToSpannedText(
                    getString(
                        R.string.rt_filter_desc_subgroups,
                        filter.filterSelected.name.lowercase(),
                        "",
                        filter.subGroups
                    )
                )
        }
    }

    private fun hasBlocklist(
        binding: FragmentRethinkBlocklistBinding,
        lifecycleOwner: LifecycleOwner
    ) {
        go {
            uiCtx {
                val blocklistsExist = withContext(Dispatchers.IO) { hasBlocklists() }
                if (blocklistsExist) {
                    setListAdapter(binding, lifecycleOwner)
                    setSimpleAdapter(binding, lifecycleOwner)
                    showConfigureUi(binding)
                    hideDownloadUi(binding)
                    return@uiCtx
                }

                showDownloadUi(binding)
                hideConfigureUi(binding)
            }
        }
    }

    private fun hasBlocklists(): Boolean {
        return if (blocklistType.isLocal()) {
            hasLocalBlocklists(this, persistentState.localBlocklistTimestamp)
        } else {
            hasRemoteBlocklists(this, persistentState.remoteBlocklistTimestamp)
        }
    }

    private fun showDownloadUi(binding: FragmentRethinkBlocklistBinding) {
        if (blocklistType.isLocal()) {
            binding.lbDownloadLayout.visibility = View.VISIBLE
        } else {
            binding.lbDownloadProgressRemote.visibility = View.VISIBLE
            downloadBlocklist(binding, blocklistType)
        }
    }

    private fun showConfigureUi(binding: FragmentRethinkBlocklistBinding) {
        binding.lbConfigureLayout.visibility = View.VISIBLE
    }

    private fun hideDownloadUi(binding: FragmentRethinkBlocklistBinding) {
        binding.lbDownloadLayout.visibility = View.GONE
        binding.lbDownloadProgressRemote.visibility = View.GONE
    }

    private fun hideConfigureUi(binding: FragmentRethinkBlocklistBinding) {
        binding.lbConfigureLayout.visibility = View.GONE
    }

    private fun isStampChanged(): Boolean {
        if (DEFAULT_RDNS_REMOTE_DNS_NAMES.contains(remoteName)) {
            return false
        }

        return getStamp() != modifiedStamp
    }

    private fun initBlocklistClickListeners(
        binding: FragmentRethinkBlocklistBinding,
        lifecycleOwner: LifecycleOwner
    ) {
        binding.lbDownloadBtn.setOnClickListener {
            binding.lbDownloadBtn.isEnabled = false
            binding.lbDownloadBtn.isClickable = false

            downloadBlocklist(binding, blocklistType)
        }

        binding.lbCancelDownloadBtn.setOnClickListener {
            cancelDownload()
            finish()
        }

        binding.lbBlocklistApplyBtn.setOnClickListener {
            setStamp(modifiedStamp)
            finish()
        }

        binding.lbBlocklistCancelBtn.setOnClickListener {
            io {
                val stamp = getStamp()
                val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
                updateSelectedFileTags(list.toMutableSet())
                setStamp(stamp)
                Logger.i(LOG_TAG_UI, "revert to old stamp for blocklist type: ${blocklistType.name}, $stamp, $list")
                uiCtx {
                    finish()
                }
            }
        }

        binding.lbListToggleGroup.addOnButtonCheckedListener(listViewToggleListener(binding))

        binding.lbAdvSearchFilterIcon.setOnClickListener { openFilterBottomSheet() }

        binding.lbAdvSearchSv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                if (isRethinkStampSearch(query)) {
                    return false
                }
                addQueryToFilters(query)
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (isRethinkStampSearch(query)) {
                    return false
                }
                addQueryToFilters(query)
                return false
            }
        })

        if (!blocklistBackCallbackAdded) {
            blocklistBackCallbackAdded = true
            onBackPressedDispatcher.addCallback(this) {
                if (!isStampChanged()) {
                    finish()
                    return@addCallback
                }

                showApplyChangesDialog()
            }
        }
    }

    private fun cancelDownload() {
        appDownloadManager.cancelDownload(type = RethinkBlocklistManager.DownloadType.LOCAL)
    }

    private fun downloadBlocklist(
        binding: FragmentRethinkBlocklistBinding,
        type: RethinkBlocklistManager.RethinkBlocklistType
    ) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDownloadDialog(binding, type)
            return
        }

        proceedWithBlocklistDownload(binding, type)
    }

    private fun showLockdownDownloadDialog(
        binding: FragmentRethinkBlocklistBinding,
        type: RethinkBlocklistManager.RethinkBlocklistType
    ) {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.lockdown_download_enable_inapp)
        builder.setMessage(R.string.lockdown_download_message)
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.lockdown_download_enable_inapp) { _, _ ->
            persistentState.useCustomDownloadManager = true
            downloadBlocklist(binding, type)
        }
        builder.setNegativeButton(R.string.lbl_cancel) { dialog, _ ->
            dialog.dismiss()
            proceedWithBlocklistDownload(binding, type)
        }
        builder.create().show()
    }

    private fun proceedWithBlocklistDownload(
        binding: FragmentRethinkBlocklistBinding,
        type: RethinkBlocklistManager.RethinkBlocklistType
    ) {
        ui {
            if (type.isLocal()) {
                var status = AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                ioCtx {
                    status =
                        appDownloadManager.downloadLocalBlocklist(
                            persistentState.localBlocklistTimestamp,
                            isRedownload = false
                        )
                }
                handleDownloadStatus(binding, status)
            } else {
                ioCtx {
                    appDownloadManager.downloadRemoteBlocklist(
                        persistentState.remoteBlocklistTimestamp,
                        isRedownload = true
                    )
                }
                binding.lbDownloadProgressRemote.visibility = View.GONE
                hasBlocklist(binding, this)
            }
        }
    }

    private fun handleDownloadStatus(
        binding: FragmentRethinkBlocklistBinding,
        status: AppDownloadManager.DownloadManagerStatus
    ) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.STARTED -> {
                observeWorkManager(binding, this)
            }
            AppDownloadManager.DownloadManagerStatus.NOT_STARTED -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.SUCCESS -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                onDownloadFail(binding)
            }
            AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                showToastUiCentered(
                    this,
                    "Download latest version to update the blocklists",
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun showApplyChangesDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(getString(R.string.rt_dialog_title))
        builder.setMessage(getString(R.string.rt_dialog_message))
        builder.setCancelable(true)
        builder.setPositiveButton(getString(R.string.lbl_apply)) { _, _ ->
            setStamp(modifiedStamp)
            finish()
        }
        builder.setNeutralButton(getString(R.string.rt_dialog_neutral)) { _, _ ->
            // no-op
        }
        builder.setNegativeButton(getString(R.string.notif_dialog_pause_dialog_negative)) { _, _ ->
            finish()
        }
        builder.create().show()
    }

    private fun setStamp(stamp: String?) {
        Logger.i(LOG_TAG_UI, "set stamp for blocklist type: ${blocklistType.name} with $stamp")
        if (stamp == null) {
            Logger.i(LOG_TAG_UI, "stamp is null")
            return
        }

        io {
            val blocklistCount = getTagsFromStamp(stamp, blocklistType).size
            if (blocklistType.isLocal()) {
                persistentState.localBlocklistStamp = stamp
                persistentState.numberOfLocalBlocklists = blocklistCount
                persistentState.blocklistEnabled = true
                Logger.i(LOG_TAG_UI, "set stamp for local blocklist with $stamp, $blocklistCount")
            } else {
                appConfig.updateRethinkEndpoint(
                    Constants.RETHINK_DNS_PLUS,
                    getRemoteUrl(stamp),
                    blocklistCount
                )
                appConfig.enableRethinkDnsPlus()
                Logger.i(LOG_TAG_UI, "set stamp for remote blocklist with $stamp, $blocklistCount")
            }
        }
    }

    private fun getRemoteUrl(stamp: String): String {
        return if (remoteUrl.contains(MAX_ENDPOINT)) {
            Constants.RETHINK_BASE_URL_MAX + stamp
        } else {
            Constants.RETHINK_BASE_URL_SKY + stamp
        }
    }

    private fun listViewToggleListener(binding: FragmentRethinkBlocklistBinding) =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            val mb: MaterialButton = binding.lbListToggleGroup.findViewById(checkedId)
            if (isChecked) {
                selectToggleBtnUi(mb)
                showList(binding, mb.tag.toString())
                return@OnButtonCheckedListener
            }

            unselectToggleBtnUi(mb)
        }

    private fun showList(binding: FragmentRethinkBlocklistBinding, id: String) {
        when (RethinkBlocklistState.BlocklistView.getTag(id)) {
            RethinkBlocklistState.BlocklistView.PACKS -> {
                binding.lbSimpleRecyclerPacks.visibility = View.VISIBLE
                binding.lbAdvContainer.visibility = View.INVISIBLE
            }
            RethinkBlocklistState.BlocklistView.ADVANCED -> {
                binding.lbSimpleRecyclerPacks.visibility = View.GONE
                binding.lbAdvContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun selectToggleBtnUi(mb: MaterialButton) {
        mb.backgroundTintList =
            ColorStateList.valueOf(fetchToggleBtnColors(this, R.color.accentGood))
        mb.setTextColor(UIUtils.fetchColor(this, R.attr.homeScreenHeaderTextColor))
    }

    private fun unselectToggleBtnUi(mb: MaterialButton) {
        mb.setTextColor(UIUtils.fetchColor(this, R.attr.primaryTextColor))
        mb.backgroundTintList =
            ColorStateList.valueOf(
                fetchToggleBtnColors(this, R.color.defaultToggleBtnBg)
            )
    }

    private fun setListAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        io {
            processSelectedFileTags(getStamp())
            uiCtx {
                if (blocklistType.isLocal()) {
                    setLocalAdapter(binding, lifecycleOwner)
                } else {
                    setRemoteAdapter(binding, lifecycleOwner)
                }
                showList(binding, binding.lbSimpleToggleBtn.tag.toString())
            }
        }
    }

    private fun setupRecyclerScrollListener(
        binding: FragmentRethinkBlocklistBinding,
        recycler: RecyclerView,
        viewType: RethinkBlocklistState.BlocklistView
    ) {
        val scrollListener =
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (recyclerView.getChildAt(0)?.tag == null) return

                    val tag: String = recyclerView.getChildAt(0).tag as String

                    if (viewType.isSimple()) {
                        binding.recyclerScrollHeaderSimple.visibility = View.VISIBLE
                        binding.recyclerScrollHeaderSimple.text = tag
                        binding.recyclerScrollHeaderAdv.visibility = View.GONE
                    } else {
                        binding.recyclerScrollHeaderAdv.visibility = View.VISIBLE
                        binding.recyclerScrollHeaderAdv.text = tag
                        binding.recyclerScrollHeaderSimple.visibility = View.GONE
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        binding.recyclerScrollHeaderSimple.visibility = View.GONE
                        binding.recyclerScrollHeaderAdv.visibility = View.GONE
                    }
                }
            }
        recycler.addOnScrollListener(scrollListener)
    }

    private fun setSimpleAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        if (blocklistType.isLocal()) {
            setLocalSimpleViewAdapter(binding, lifecycleOwner)
        } else {
            setRemoteSimpleViewAdapter(binding, lifecycleOwner)
        }
    }

    private suspend fun processSelectedFileTags(stamp: String) {
        val list = RethinkBlocklistManager.getTagsFromStamp(stamp, blocklistType)
        updateSelectedFileTags(list.toMutableSet())
    }

    private suspend fun updateSelectedFileTags(selectedTags: MutableSet<Int>) {
        if (selectedTags.isEmpty()) {
            if (blocklistType.isLocal()) {
                RethinkBlocklistManager.clearTagsSelectionLocal()
            } else {
                RethinkBlocklistManager.clearTagsSelectionRemote()
            }
            return
        }

        if (blocklistType.isLocal()) {
            RethinkBlocklistManager.clearTagsSelectionLocal()
            RethinkBlocklistManager.updateFiletagsLocal(selectedTags, 1)
            val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        } else {
            RethinkBlocklistManager.clearTagsSelectionRemote()
            RethinkBlocklistManager.updateFiletagsRemote(selectedTags, 1)
            val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
            RethinkBlocklistState.updateFileTagList(list)
        }
    }

    private fun getStamp(): String {
        return if (blocklistType.isLocal()) {
            persistentState.localBlocklistStamp
        } else {
            getRemoteBlocklistStamp(remoteUrl)
        }
    }

    private fun isRethinkStampSearch(t: String): Boolean {
        if (!t.contains(Constants.RETHINKDNS_DOMAIN)) return false

        val split = t.split("/")
        split.forEach {
            if (it.contains("$RETHINK_STAMP_VERSION:") && isBase64(it)) {
                io { processSelectedFileTags(it) }
                showToastUiCentered(this, "Blocklists restored", Toast.LENGTH_SHORT)
                return true
            }
        }

        return false
    }

    private fun isBase64(stamp: String): Boolean {
        val whitespaceRegex = "\\s"
        val pattern =
            Pattern.compile(
                "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$"
            )

        val versionSplit = stamp.split(":").getOrNull(1) ?: return false

        if (versionSplit.isEmpty()) return false

        val result = versionSplit.replace(whitespaceRegex, "")
        return pattern.matcher(result).matches()
    }

    private fun addQueryToFilters(query: String) {
        val a = filterObserver()
        if (a.value == null) {
            val temp = RethinkBlocklistState.Filters()
            temp.query = formatQuery(query)
            filters.postValue(temp)
            return
        }

        a.value!!.query = formatQuery(query)
        filters.postValue(a.value)
    }

    private fun formatQuery(q: String): String {
        return "%$q%"
    }

    private fun setLocalSimpleViewAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        localSimpleViewAdapter = LocalSimpleViewAdapter(this)
        val layoutManager = CustomLinearLayoutManager(this)
        binding.lbSimpleRecyclerPacks.layoutManager = layoutManager

        localBlocklistPacksMapViewModel.simpleTags.observe(lifecycleOwner) {
            val l = it.filter { it1 -> !it1.pack.contains(DEAD_PACK) && it1.pack.isNotEmpty() }
            localSimpleViewAdapter?.submitData(lifecycleOwner.lifecycle, l)
        }
        binding.lbSimpleRecyclerPacks.adapter = localSimpleViewAdapter
        setupRecyclerScrollListener(binding, binding.lbSimpleRecyclerPacks, RethinkBlocklistState.BlocklistView.PACKS)
    }

    private fun setRemoteSimpleViewAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        remoteSimpleViewAdapter = RemoteSimpleViewAdapter(this)
        val layoutManager = CustomLinearLayoutManager(this)
        binding.lbSimpleRecyclerPacks.layoutManager = layoutManager

        remoteBlocklistPacksMapViewModel.simpleTags.observe(lifecycleOwner) {
            val r = it.filter { it1 -> !it1.pack.contains(DEAD_PACK) && it1.pack.isNotEmpty() }
            remoteSimpleViewAdapter?.submitData(lifecycleOwner.lifecycle, r)
        }
        binding.lbSimpleRecyclerPacks.adapter = remoteSimpleViewAdapter
        setupRecyclerScrollListener(binding, binding.lbSimpleRecyclerPacks, RethinkBlocklistState.BlocklistView.PACKS)
    }

    private fun remakeFilterChipsUi(binding: FragmentRethinkBlocklistBinding) {
        binding.filterChipGroup.removeAllViews()

        val all = makeChip(binding, RethinkBlocklistState.BlocklistSelectionFilter.ALL.id, getString(R.string.lbl_all), true)
        val selected =
            makeChip(
                binding,
                RethinkBlocklistState.BlocklistSelectionFilter.SELECTED.id,
                getString(R.string.rt_filter_parent_selected),
                false
            )

        binding.filterChipGroup.addView(all)
        binding.filterChipGroup.addView(selected)
    }

    private fun makeChip(
        binding: FragmentRethinkBlocklistBinding,
        id: Int,
        label: String,
        checked: Boolean
    ): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, binding.root, false) as Chip
        chip.tag = id
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applyFilter(button.tag)
            }
        }

        return chip
    }

    private fun applyFilter(tag: Any) {
        val a = filterObserver().value ?: RethinkBlocklistState.Filters()

        when (tag) {
            RethinkBlocklistState.BlocklistSelectionFilter.ALL.id -> {
                a.filterSelected = RethinkBlocklistState.BlocklistSelectionFilter.ALL
            }
            RethinkBlocklistState.BlocklistSelectionFilter.SELECTED.id -> {
                a.filterSelected = RethinkBlocklistState.BlocklistSelectionFilter.SELECTED
            }
        }
        filters.postValue(a)
    }

    private fun openFilterBottomSheet() {
        io {
            val dialog = RethinkPlusFilterDialog(this@ConfigureRethinkBasicActivity, this@ConfigureRethinkBasicActivity, getAllList(), persistentState)
            uiCtx { dialog.show() }
        }
    }

    private suspend fun getAllList(): List<FileTag> {
        return if (blocklistType.isLocal()) {
            localFileTagViewModel.allFileTags()
        } else {
            remoteFileTagViewModel.allFileTags()
        }
    }

    private fun setRemoteAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        if (advanceRemoteViewAdapter != null) return

        advanceRemoteViewAdapter = RemoteAdvancedViewAdapter(this)
        val layoutManager = CustomLinearLayoutManager(this)
        binding.lbAdvancedRecycler.layoutManager = layoutManager

        remoteFileTagViewModel.remoteFileTags.observe(lifecycleOwner) {
            advanceRemoteViewAdapter!!.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.lbAdvancedRecycler.adapter = advanceRemoteViewAdapter
        setupRecyclerScrollListener(binding, binding.lbAdvancedRecycler, RethinkBlocklistState.BlocklistView.ADVANCED)
    }

    private fun setLocalAdapter(binding: FragmentRethinkBlocklistBinding, lifecycleOwner: LifecycleOwner) {
        if (advanceLocalViewAdapter != null) return

        advanceLocalViewAdapter = LocalAdvancedViewAdapter(this)
        val layoutManager = CustomLinearLayoutManager(this)
        binding.lbAdvancedRecycler.layoutManager = layoutManager

        localFileTagViewModel.localFiletags.observe(lifecycleOwner) {
            advanceLocalViewAdapter!!.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.lbAdvancedRecycler.adapter = advanceLocalViewAdapter
        setupRecyclerScrollListener(binding, binding.lbAdvancedRecycler, RethinkBlocklistState.BlocklistView.ADVANCED)
    }

    private fun observeWorkManager(
        binding: FragmentRethinkBlocklistBinding,
        lifecycleOwner: LifecycleOwner
    ) {
        val workManager = WorkManager.getInstance(applicationContext)

        workManager.getWorkInfosByTagLiveData(CUSTOM_DOWNLOAD).observe(lifecycleOwner) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_DOWNLOAD,
                "WorkManager state: ${workInfo.state} for $CUSTOM_DOWNLOAD"
            )
            if (
                WorkInfo.State.ENQUEUED == workInfo.state ||
                    WorkInfo.State.RUNNING == workInfo.state
            ) {
                onDownloadStart(binding)
            } else if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                onDownloadSuccess(binding)
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                onDownloadFail(binding)
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(CUSTOM_DOWNLOAD)
            }
        }

        workManager.getWorkInfosByTagLiveData(DOWNLOAD_TAG).observe(lifecycleOwner) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_DOWNLOAD,
                "WorkManager state: ${workInfo.state} for $DOWNLOAD_TAG"
            )
            if (
                WorkInfo.State.ENQUEUED == workInfo.state ||
                    WorkInfo.State.RUNNING == workInfo.state
            ) {
                onDownloadStart(binding)
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                onDownloadFail(binding)
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DOWNLOAD_TAG)
                workManager.cancelAllWorkByTag(FILE_TAG)
            }
        }

        workManager.getWorkInfosByTagLiveData(FILE_TAG).observe(lifecycleOwner) { workInfoList ->
            if (workInfoList != null && workInfoList.isNotEmpty()) {
                val workInfo = workInfoList[0]
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager completed - $FILE_TAG"
                    )
                    onDownloadSuccess(binding)
                    workManager.pruneWork()
                } else if (
                    workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED
                ) {
                    onDownloadFail(binding)
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(FILE_TAG)
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager failed - $FILE_TAG"
                    )
                } else {
                    Logger.i(
                        Logger.LOG_TAG_DOWNLOAD,
                        "AppDownloadManager Work Manager - $FILE_TAG, ${workInfo.state}"
                    )
                }
            }
        }
    }

    private fun onDownloadStart(binding: FragmentRethinkBlocklistBinding) {
        showDownloadUi(binding)
        binding.lbDownloadProgress.visibility = View.VISIBLE
        binding.lbDownloadBtn.text = getString(R.string.rt_download_start)
        hideConfigureUi(binding)
    }

    private fun onDownloadFail(binding: FragmentRethinkBlocklistBinding) {
        binding.lbDownloadProgress.visibility = View.GONE
        binding.lbDownloadProgressRemote.visibility = View.GONE
        binding.lbDownloadBtn.visibility = View.VISIBLE
        binding.lbDownloadBtn.isEnabled = true
        binding.lbDownloadBtn.text = getString(R.string.rt_download)
        showDownloadUi(binding)
        hideConfigureUi(binding)
    }

    private fun onDownloadSuccess(binding: FragmentRethinkBlocklistBinding) {
        binding.lbDownloadProgress.visibility = View.GONE
        binding.lbDownloadProgressRemote.visibility = View.GONE
        binding.lbDownloadBtn.text = getString(R.string.rt_download)
        hideDownloadUi(binding)
        hasBlocklist(binding, this)
        binding.lbListToggleGroup.check(R.id.lb_simple_toggle_btn)
        showToastUiCentered(
            this,
            getString(R.string.download_update_dialog_message_success),
            Toast.LENGTH_SHORT
        )
    }

    private fun initRethinkList(binding: FragmentRethinkListBinding, lifecycleOwner: LifecycleOwner) {
        showBlocklistVersionUi(binding)
        showUpdateCheckUi(binding)
        updateMaxSwitchUi(binding)

        listLayoutManager = CustomLinearLayoutManager(this)
        binding.recyclerDohConnections.layoutManager = listLayoutManager

        listAdapter = RethinkEndpointAdapter(this, get())
        rethinkEndpointViewModel.setFilter(uid)
        rethinkEndpointViewModel.rethinkEndpointList.observe(lifecycleOwner) {
            listAdapter!!.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerDohConnections.adapter = listAdapter

        initRethinkListObservers(binding, lifecycleOwner)
        initRethinkListClickListeners(binding)
    }

    private fun showBlocklistVersionUi(binding: FragmentRethinkListBinding) {
        if (getDownloadTimeStamp() == Constants.Companion.INIT_TIME_MS) {
            binding.dohFabAddServerIcon.visibility = View.GONE
            binding.lbVersion.visibility = View.GONE
            return
        }

        binding.lbVersion.text =
            getString(
                R.string.settings_local_blocklist_version,
                Utilities.convertLongToTime(getDownloadTimeStamp(), Constants.TIME_FORMAT_2)
            )
    }

    private fun showUpdateCheckUi(binding: FragmentRethinkListBinding) {
        if (isBlocklistUpdateAvailable()) {
            binding.bslbUpdateAvailableBtn.visibility = View.VISIBLE
            binding.bslbRedownloadBtn.visibility = View.GONE
            binding.bslbCheckUpdateBtn.visibility = View.GONE
            return
        }

        binding.bslbCheckUpdateBtn.visibility = View.VISIBLE
        binding.bslbRedownloadBtn.visibility = View.GONE
        binding.bslbUpdateAvailableBtn.visibility = View.GONE
    }

    private fun isBlocklistUpdateAvailable(): Boolean {
        Logger.d(
            Logger.LOG_TAG_DOWNLOAD,
            "Update available? newest: ${persistentState.newestRemoteBlocklistTimestamp}, available: ${persistentState.remoteBlocklistTimestamp}"
        )
        return (persistentState.newestRemoteBlocklistTimestamp != Constants.Companion.INIT_TIME_MS &&
            persistentState.newestRemoteBlocklistTimestamp >
                persistentState.remoteBlocklistTimestamp)
    }

    private fun checkBlocklistUpdate() {
        io { appDownloadManager.isDownloadRequired(RethinkBlocklistManager.DownloadType.REMOTE) }
    }

    private fun getDownloadTimeStamp(): Long {
        return persistentState.remoteBlocklistTimestamp
    }

    private fun initRethinkListClickListeners(binding: FragmentRethinkListBinding) {
        binding.dohFabAddServerIcon.bringToFront()
        binding.dohFabAddServerIcon.setOnClickListener {
            val intent = Intent(this, ConfigureRethinkBasicActivity::class.java)
            intent.putExtra(INTENT, FragmentLoader.REMOTE.ordinal)
            startActivity(intent)
        }

        binding.bslbCheckUpdateBtn.setOnClickListener {
            binding.bslbCheckUpdateBtn.isEnabled = false
            showProgress(binding.bslbCheckUpdateBtn)
            checkBlocklistUpdate()
        }

        binding.bslbUpdateAvailableBtn.setOnClickListener {
            binding.bslbUpdateAvailableBtn.isEnabled = false
            val timestamp = getDownloadTimeStamp()
            showProgress(binding.bslbUpdateAvailableBtn)
            download(timestamp, isRedownload = false)
        }

        binding.bslbRedownloadBtn.setOnClickListener {
            binding.bslbRedownloadBtn.isEnabled = false
            showProgress(binding.bslbRedownloadBtn)
            download(getDownloadTimeStamp(), isRedownload = true)
        }

        binding.radioMax.setOnCheckedChangeListener(null)
        binding.radioMax.setOnClickListener {
            if (binding.radioMax.isChecked) {
                io { appConfig.switchRethinkDnsToMax() }
                updateRethinkRadioUi(binding, isMax = true)
            }
        }

        binding.radioSky.setOnCheckedChangeListener(null)
        binding.radioSky.setOnClickListener {
            if (binding.radioSky.isChecked) {
                io { appConfig.switchRethinkDnsToSky() }
                updateRethinkRadioUi(binding, isMax = false)
            }
        }
    }

    private fun showProgress(chip: Chip) {
        val cpDrawable = androidx.swiperefreshlayout.widget.CircularProgressDrawable(this)
        cpDrawable.setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
        val color = UIUtils.fetchColor(this, R.attr.chipTextPositive)
        cpDrawable.colorFilter =
            androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                color,
                androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        cpDrawable.start()

        chip.chipIcon = cpDrawable
        chip.isChipIconVisible = true
    }

    private fun hideProgress(binding: FragmentRethinkListBinding) {
        binding.bslbCheckUpdateBtn.isChipIconVisible = false
        binding.bslbRedownloadBtn.isChipIconVisible = false
        binding.bslbUpdateAvailableBtn.isChipIconVisible = false
    }

    private fun updateRethinkRadioUi(binding: FragmentRethinkListBinding, isMax: Boolean) {
        if (isMax) {
            binding.radioMax.isChecked = true
            binding.radioSky.isChecked = false
            binding.frlDesc.text = getString(R.string.rethink_max_desc)
        } else {
            binding.radioSky.isChecked = true
            binding.radioMax.isChecked = false
            binding.frlDesc.text = getString(R.string.rethink_sky_desc)
        }
    }

    private fun download(timestamp: Long, isRedownload: Boolean) {
        io {
            val initiated = appDownloadManager.downloadRemoteBlocklist(timestamp, isRedownload)
            uiCtx {
                if (!initiated) {
                    onRemoteDownloadFailure(listBinding)
                }
            }
        }
    }

    private fun updateMaxSwitchUi(binding: FragmentRethinkListBinding) {
        ui {
            var endpointUrl: String? = null
            ioCtx { endpointUrl = appConfig.getRethinkPlusEndpoint()?.url }
            updateRethinkRadioUi(binding, isMax = endpointUrl?.contains(Constants.MAX_ENDPOINT) == true)
        }
    }

    private fun initRethinkListObservers(binding: FragmentRethinkListBinding, lifecycleOwner: LifecycleOwner) {
        val workManager = WorkManager.getInstance(applicationContext)
        workManager
            .getWorkInfosByTagLiveData(RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER)
            .observe(lifecycleOwner) { workInfoList ->
                val workInfo = workInfoList?.getOrNull(0) ?: return@observe
                Logger.i(
                    Logger.LOG_TAG_DOWNLOAD,
                    "WorkManager state: ${workInfo.state} for ${RemoteBlocklistCoordinator.REMOTE_DOWNLOAD_WORKER}"
                )
                if (
                    WorkInfo.State.ENQUEUED == workInfo.state ||
                        WorkInfo.State.RUNNING == workInfo.state
                ) {
                    // no-op
                } else if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                    hideProgress(binding)
                    onDownloadSuccess(binding)
                    workManager.pruneWork()
                } else if (
                    WorkInfo.State.CANCELLED == workInfo.state ||
                        WorkInfo.State.FAILED == workInfo.state
                ) {
                    hideProgress(binding)
                    onRemoteDownloadFailure(binding)
                    Utilities.showToastUiCentered(
                        this,
                        getString(R.string.blocklist_update_check_failure),
                        Toast.LENGTH_SHORT
                    )
                    workManager.pruneWork()
                    workManager.cancelAllWorkByTag(CUSTOM_DOWNLOAD)
                }
            }

        appDownloadManager.downloadRequired.observe(lifecycleOwner) {
            Logger.i(Logger.LOG_TAG_DNS, "Check for blocklist update, status: $it")
            if (it == null) return@observe

            when (it) {
                AppDownloadManager.DownloadManagerStatus.NOT_STARTED -> {
                    // no-op
                }
                AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {
                    // no-op
                }
                AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                    Utilities.showToastUiCentered(
                        this,
                        "Download latest version to update the blocklists",
                        Toast.LENGTH_SHORT
                    )
                    hideProgress(binding)
                }
                AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                    hideProgress(binding)
                    showRedownloadUi(binding)
                    Utilities.showToastUiCentered(
                        this,
                        getString(R.string.blocklist_update_check_not_required),
                        Toast.LENGTH_SHORT
                    )
                    appDownloadManager.downloadRequired.postValue(
                        AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                    )
                }
                AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                    hideProgress(binding)
                    Utilities.showToastUiCentered(
                        this,
                        getString(R.string.blocklist_update_check_failure),
                        Toast.LENGTH_SHORT
                    )
                    appDownloadManager.downloadRequired.postValue(
                        AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                    )
                }
                AppDownloadManager.DownloadManagerStatus.SUCCESS -> {
                    hideProgress(binding)
                    showNewUpdateUi(binding)
                    appDownloadManager.downloadRequired.postValue(
                        AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                    )
                }
                AppDownloadManager.DownloadManagerStatus.STARTED -> {
                    // no-op
                }
            }
        }
    }

    private fun showNewUpdateUi(binding: FragmentRethinkListBinding) {
        enableChips(binding)
        binding.bslbUpdateAvailableBtn.visibility = View.VISIBLE
        binding.bslbCheckUpdateBtn.visibility = View.GONE
    }

    private fun showRedownloadUi(binding: FragmentRethinkListBinding) {
        enableChips(binding)
        binding.bslbUpdateAvailableBtn.visibility = View.GONE
        binding.bslbCheckUpdateBtn.visibility = View.GONE
        binding.bslbRedownloadBtn.visibility = View.VISIBLE
    }

    private fun onRemoteDownloadFailure(binding: FragmentRethinkListBinding?) {
        binding ?: return
        enableChips(binding)
    }

    private fun enableChips(binding: FragmentRethinkListBinding) {
        binding.bslbUpdateAvailableBtn.isEnabled = true
        binding.bslbCheckUpdateBtn.isEnabled = true
        binding.bslbRedownloadBtn.isEnabled = true
    }

    private fun onDownloadSuccess(binding: FragmentRethinkListBinding) {
        binding.lbVersion.text =
            getString(
                R.string.settings_local_blocklist_version,
                Utilities.convertLongToTime(getDownloadTimeStamp(), Constants.TIME_FORMAT_2)
            )
        enableChips(binding)
        showRedownloadUi(binding)
        Utilities.showToastUiCentered(
            this,
            getString(R.string.download_update_dialog_message_success),
            Toast.LENGTH_SHORT
        )
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun ui(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) { f() }
    }

    private fun go(f: suspend () -> Unit) {
        lifecycleScope.launch { f() }
    }
}
