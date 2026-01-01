/*
 * Copyright 2024 RethinkDNS and its authors
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

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.AppWiseDomainsAdapter
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppWiseDomainLogsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val networkLogsViewModel: AppConnectionsViewModel by viewModel()
    private var uid: Int = INVALID_UID
    private lateinit var appInfo: AppInfo
    private var isActiveConns = false
    private var isRethinkApp = false

    private var selectedCategory by mutableStateOf(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS)
    private var searchHint by mutableStateOf("")
    private var appIcon by mutableStateOf<Drawable?>(null)
    private var showToggleGroup by mutableStateOf(true)
    private var showDeleteIcon by mutableStateOf(true)

    companion object {
        private const val QUERY_TEXT_DELAY: Long = 1000
        private const val TAG = "AppWiseDomainLogs"
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
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
        uid = intent.getIntExtra(AppInfoActivity.INTENT_UID, INVALID_UID)
        isActiveConns = intent.getBooleanExtra(AppInfoActivity.INTENT_ACTIVE_CONNS, false)

        if (uid == INVALID_UID) {
            finish()
        }
        isRethinkApp = Utilities.getApplicationInfo(this, this.packageName)?.uid == uid
        init()

        setContent {
            RethinkTheme {
                AppWiseDomainLogsScreen()
            }
        }
    }

    private fun init() {
        if (!isActiveConns) {
            selectedCategory = AppConnectionsViewModel.TimeCategory.SEVEN_DAYS
            networkLogsViewModel.timeCategoryChanged(selectedCategory, true)
        } else {
            showToggleGroup = false
            showDeleteIcon = false
        }

        io {
            val appInfo = FirewallManager.getAppInfoByUid(uid)
            if (appInfo == null || uid == INVALID_UID) {
                uiCtx { finish() }
                return@io
            }

            val packages = FirewallManager.getPackageNamesByUid(appInfo.uid)
            uiCtx {
                this.appInfo = appInfo

                val appName = appName(packages.count())
                updateAppNameInSearchHint(appName)
                appIcon = Utilities.getIcon(this, appInfo.packageName, appInfo.appName)
            }
        }
    }

    private fun appName(packageCount: Int): String {
        return if (packageCount >= 2) {
            getString(
                R.string.ctbs_app_other_apps,
                appInfo.appName,
                packageCount.minus(1).toString()
            )
        } else {
            appInfo.appName
        }
    }

    private fun updateAppNameInSearchHint(appName: String) {
        val appNameTruncated = appName.substring(0, appName.length.coerceAtMost(10))
        val hint = if (isActiveConns) {
            getString(
                R.string.two_argument_colon,
                appNameTruncated,
                getString(R.string.search_universal_ips)
            )
        } else {
            getString(
                R.string.two_argument_colon,
                appNameTruncated,
                getString(R.string.search_custom_domains)
            )
        }
        searchHint = hint
    }

    private fun showDeleteConnectionsDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.ada_delete_logs_dialog_title)
        builder.setMessage(R.string.ada_delete_logs_dialog_desc)
        builder.setCancelable(true)
        builder.setPositiveButton(getString(R.string.lbl_proceed)) { _, _ -> deleteAppLogs() }

        builder.setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
        builder.create().show()
    }

    private fun deleteAppLogs() {
        io { networkLogsViewModel.deleteLogs(uid) }
    }

    private fun io(f: suspend () -> Unit): Job {
        return lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    @Composable
    private fun AppWiseDomainLogsScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showToggleGroup) {
                ToggleRow()
                Spacer(modifier = Modifier.height(8.dp))
            }
            HeaderRow()
            AppWiseDomainList()
        }
    }

    @Composable
    private fun ToggleRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleButton(
                label = stringResourceCompat(R.string.ci_desc, "1", stringResourceCompat(R.string.lbl_hour)),
                selected = selectedCategory == AppConnectionsViewModel.TimeCategory.ONE_HOUR,
                onClick = { updateTimeCategory(AppConnectionsViewModel.TimeCategory.ONE_HOUR) }
            )
            ToggleButton(
                label = stringResourceCompat(R.string.ci_desc, "24", stringResourceCompat(R.string.lbl_hour)),
                selected = selectedCategory == AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR,
                onClick = { updateTimeCategory(AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR) }
            )
            ToggleButton(
                label = stringResourceCompat(R.string.ci_desc, "7", stringResourceCompat(R.string.lbl_day)),
                selected = selectedCategory == AppConnectionsViewModel.TimeCategory.SEVEN_DAYS,
                onClick = { updateTimeCategory(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
            )
        }
    }

    private fun updateTimeCategory(category: AppConnectionsViewModel.TimeCategory) {
        selectedCategory = category
        networkLogsViewModel.timeCategoryChanged(category, true)
    }

    @Composable
    private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
        val context = LocalContext.current
        val background =
            if (selected) {
                Color(UIUtils.fetchToggleBtnColors(context, R.color.accentGood))
            } else {
                Color(UIUtils.fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
            }
        val content =
            if (selected) {
                Color(UIUtils.fetchColor(context, R.attr.homeScreenHeaderTextColor))
            } else {
                Color(UIUtils.fetchColor(context, R.attr.primaryTextColor))
            }
        androidx.compose.material3.Button(
            onClick = onClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = background,
                contentColor = content
            )
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun HeaderRow() {
        var query by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            snapshotFlow { query }
                .debounce(QUERY_TEXT_DELAY)
                .distinctUntilChanged()
                .collect { value ->
                    val type =
                        if (isActiveConns) {
                            AppConnectionsViewModel.FilterType.ACTIVE_CONNECTIONS
                        } else {
                            AppConnectionsViewModel.FilterType.DOMAIN
                        }
                    networkLogsViewModel.setFilter(value, type)
                }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(24, 24)
                        alpha = 0.75f
                    }
                },
                update = { imageView ->
                    Glide.with(imageView)
                        .load(appIcon)
                        .error(Utilities.getDefaultIcon(this@AppWiseDomainLogsActivity))
                        .into(imageView)
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(text = searchHint.ifEmpty { getString(R.string.search_custom_domains) }) }
            )

            if (showDeleteIcon) {
                IconButton(onClick = { showDeleteConnectionsDialog() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    @Composable
    private fun AppWiseDomainList() {
        val adapter =
            remember {
                AppWiseDomainsAdapter(
                    this@AppWiseDomainLogsActivity,
                    this@AppWiseDomainLogsActivity,
                    uid,
                    isActiveConns
                )
            }
        if (!isRethinkApp) {
            networkLogsViewModel.setUid(uid)
        }

        val items =
            if (isActiveConns) {
                if (isRethinkApp) {
                    val uptime = remember { VpnController.uptimeMs() }
                    networkLogsViewModel.getRethinkAllActiveConns(uptime).asFlow()
                        .collectAsLazyPagingItems()
                } else {
                    networkLogsViewModel.activeConnections.asFlow().collectAsLazyPagingItems()
                }
            } else {
                if (isRethinkApp) {
                    networkLogsViewModel.rinrDomainLogs.asFlow().collectAsLazyPagingItems()
                } else {
                    networkLogsViewModel.appDomainLogs.asFlow().collectAsLazyPagingItems()
                }
            }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            items(count = items.itemCount) { index ->
                val item = items[index] ?: return@items
                adapter.DomainRow(item)
            }
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) context.getString(id, *args) else context.getString(id)
    }
}
