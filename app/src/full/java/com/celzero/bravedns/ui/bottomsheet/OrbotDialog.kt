/*
 * Copyright 2020 RethinkDNS and its authors
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

import android.content.Intent
import android.content.res.Configuration
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgIncludeAppsAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.DnsDetailActivity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.OrbotHelper
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OrbotDialog(
    private val activity: FragmentActivity,
    private val mappingViewModel: ProxyAppsMappingViewModel
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val orbotHelper by inject<OrbotHelper>()
    private val eventLogger by inject<EventLogger>()

    private var isConnecting by mutableStateOf(false)
    private var includeAppsLabel by mutableStateOf("")
    private var statusText by mutableStateOf("")
    private var selectedType by mutableStateOf(AppConfig.ProxyType.NONE.name)
    private var showInfoDialog by mutableStateOf(false)
    private var showStopDialog by mutableStateOf(false)
    private var isOrbotDns by mutableStateOf(false)

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                OrbotContent()
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
        initView()
        observeApps()
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
        persistentState.orbotConnectionStatus.observe(activity) {
            isConnecting = it == true
            if (!isConnecting) {
                io {
                    isOrbotDns = appConfig.isOrbotDns()
                    withContext(Dispatchers.Main) { updateUi(isOrbotDns) }
                }
            }
        }
        io {
            isOrbotDns = appConfig.isOrbotDns()
            withContext(Dispatchers.Main) { updateUi(isOrbotDns) }
        }
    }

    private fun observeApps() {
        mappingViewModel.getAppCountById(ProxyManager.ID_ORBOT_BASE).observe(activity) {
            includeAppsLabel = activity.getString(R.string.add_remove_apps, it.toString())
        }
    }

    @Composable
    private fun OrbotContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val hasHttpSupport = isAtleastQ()
        val transition = rememberInfiniteTransition(label = "orbot-rotation")
        val rotation by
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                label = "rotation"
            )

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter =
                        painterResource(
                            id =
                                if (selectedType == AppConfig.ProxyType.NONE.name) {
                                    R.drawable.orbot_disabled
                                } else {
                                    R.drawable.orbot_enabled
                                }
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).rotate(if (isConnecting) rotation else 0f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.getString(R.string.orbot_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.ic_info_white),
                    contentDescription = activity.getString(R.string.orbot_explanation),
                    modifier = Modifier.size(22.dp).clickable { showInfoDialog = true }
                )
            }

            OrbotOptionRow(
                label = activity.getString(R.string.orbot_none),
                description = activity.getString(R.string.orbot_none_desc),
                selected = selectedType == AppConfig.ProxyType.NONE.name
            ) {
                handleOrbotStop()
            }

            OrbotOptionRow(
                label = activity.getString(R.string.orbot_socks5),
                description = activity.getString(R.string.orbot_socks5_desc),
                selected = selectedType == AppConfig.ProxyType.SOCKS5.name
            ) {
                enableProxy(AppConfig.ProxyType.SOCKS5.name)
            }

            if (hasHttpSupport) {
                OrbotOptionRow(
                    label = activity.getString(R.string.orbot_http),
                    description = activity.getString(R.string.orbot_http_desc),
                    selected = selectedType == AppConfig.ProxyType.HTTP.name
                ) {
                    enableProxy(AppConfig.ProxyType.HTTP.name)
                }
                OrbotOptionRow(
                    label = activity.getString(R.string.orbot_both),
                    description = activity.getString(R.string.orbot_both_desc),
                    selected = selectedType == AppConfig.ProxyType.HTTP_SOCKS5.name
                ) {
                    enableProxy(AppConfig.ProxyType.HTTP_SOCKS5.name)
                }
            }

            TextButton(onClick = { orbotHelper.openOrbotApp() }) {
                Text(text = activity.getString(R.string.settings_orbot_header))
            }

            TextButton(onClick = { openAppsDialog() }) {
                Text(text = includeAppsLabel)
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text(text = activity.getString(R.string.orbot_title)) },
                text = { HtmlText(activity.getString(R.string.orbot_explanation)) },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text(text = activity.getString(R.string.lbl_dismiss))
                    }
                }
            )
        }

        if (showStopDialog) {
            AlertDialog(
                onDismissRequest = { showStopDialog = false },
                title = { Text(text = activity.getString(R.string.orbot_stop_dialog_title)) },
                text = { Text(text = stopDialogMessage()) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showStopDialog = false }) {
                            Text(text = activity.getString(R.string.lbl_dismiss))
                        }
                        TextButton(
                            onClick = {
                                showStopDialog = false
                                orbotHelper.openOrbotApp()
                            }
                        ) {
                            Text(text = activity.getString(R.string.orbot_stop_dialog_negative))
                        }
                        if (isOrbotDns) {
                            TextButton(
                                onClick = {
                                    showStopDialog = false
                                    gotoDnsConfigureScreen()
                                }
                            ) {
                                Text(text = activity.getString(R.string.orbot_stop_dialog_neutral))
                            }
                        }
                    }
                }
            )
        }
    }

    @Composable
    private fun OrbotOptionRow(
        label: String,
        description: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(enabled = !isConnecting) { onClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, onClick = null, enabled = !isConnecting)
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun HtmlText(html: String) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    text = HtmlCompat.fromHtml(html.replace("\n", "<br /><br />"), HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
            },
            update = { view ->
                view.text =
                    HtmlCompat.fromHtml(
                        html.replace("\n", "<br /><br />"),
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
            }
        )
    }

    private fun enableProxy(type: String) {
        io {
            val isSelected = ProxyManager.isAnyAppSelected(ProxyManager.ID_ORBOT_BASE)
            withContext(Dispatchers.Main) {
                if (!isSelected) {
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.orbot_no_app_toast),
                        Toast.LENGTH_SHORT
                    )
                    updateUi(isOrbotDns)
                    return@withContext
                }
                if (type == selectedType) return@withContext
                persistentState.orbotConnectionStatus.postValue(true)
                startOrbot(type)
            }
        }
    }

    private fun handleOrbotStop() {
        stopOrbot()
        io {
            isOrbotDns = appConfig.isOrbotDns()
            withContext(Dispatchers.Main) {
                showStopDialog = true
            }
            logEvent("Orbot Stopped", "User stopped Orbot from Orbot Bottom Sheet")
        }
    }

    private fun updateUi(isOrbotDns: Boolean) {
        selectedType = OrbotHelper.selectedProxyType
        statusText =
            when (selectedType) {
                AppConfig.ProxyType.SOCKS5.name -> {
                    if (isOrbotDns) {
                        activity.getString(
                            R.string.orbot_bs_status_1,
                            activity.getString(R.string.orbot_status_arg_3)
                        )
                    } else {
                        activity.getString(
                            R.string.orbot_bs_status_1,
                            activity.getString(R.string.orbot_status_arg_2)
                        )
                    }
                }
                AppConfig.ProxyType.HTTP.name -> {
                    activity.getString(R.string.orbot_bs_status_2)
                }
                AppConfig.ProxyType.HTTP_SOCKS5.name -> {
                    if (isOrbotDns) {
                        activity.getString(
                            R.string.orbot_bs_status_3,
                            activity.getString(R.string.orbot_status_arg_3)
                        )
                    } else {
                        activity.getString(
                            R.string.orbot_bs_status_3,
                            activity.getString(R.string.orbot_status_arg_2)
                        )
                    }
                }
                else -> activity.getString(R.string.orbot_bs_status_4)
            }
    }

    private fun openAppsDialog() {
        val appsAdapter =
            WgIncludeAppsAdapter(
                activity,
                ProxyManager.ID_ORBOT_BASE,
                ProxyManager.ORBOT_PROXY_NAME
            )
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val includeAppsDialog =
            WgIncludeAppsDialog(
                activity,
                appsAdapter,
                mappingViewModel,
                themeId,
                ProxyManager.ID_ORBOT_BASE,
                ProxyManager.ID_ORBOT_BASE
            )
        includeAppsDialog.setCanceledOnTouchOutside(false)
        includeAppsDialog.show()
    }

    private fun stopOrbot() {
        appConfig.removeAllProxies()
        selectedType = AppConfig.ProxyType.NONE.name
        orbotHelper.stopOrbot(isInteractive = true)
    }

    private fun startOrbot(type: String) {
        io {
            val isOrbotInstalled = FirewallManager.isOrbotInstalled()
            withContext(Dispatchers.Main) {
                if (!isOrbotInstalled) {
                    return@withContext
                }

                if (VpnController.hasTunnel()) {
                    orbotHelper.startOrbot(type)
                    logEvent(
                        "Orbot Started with type: $type",
                        "User started Orbot from Orbot Bottom Sheet with type: $type"
                    )
                    selectedType = type
                } else {
                    Utilities.showToastUiCentered(
                        activity,
                        activity.getString(R.string.settings_socks5_vpn_disabled_error),
                        Toast.LENGTH_LONG
                    )
                }
            }
        }
    }

    private fun stopDialogMessage(): String {
        return if (isOrbotDns) {
            activity.getString(
                R.string.orbot_stop_dialog_message_combo,
                activity.getString(R.string.orbot_stop_dialog_message),
                activity.getString(R.string.orbot_stop_dialog_dns_message)
            )
        } else {
            activity.getString(R.string.orbot_stop_dialog_message)
        }
    }

    private fun gotoDnsConfigureScreen() {
        dialog.dismiss()
        val intent = Intent(activity, DnsDetailActivity::class.java)
        intent.putExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, 0)
        activity.startActivity(intent)
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun isDarkThemeOn(): Boolean {
        return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}
