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
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_FIREWALL
import Logger.LOG_TAG_UI
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.widget.ImageView
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppDomainRulesDialog(
    private val activity: FragmentActivity,
    private val uid: Int,
    private val domain: String,
    private val position: Int,
    private val onDismiss: (Int) -> Unit
) : KoinComponent, WireguardListDialog.WireguardDismissListener {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private var domainRule by mutableStateOf(DomainRulesManager.Status.NONE)
    private var cd: CustomDomain? = null
    private var appName by mutableStateOf<String?>(null)
    private var appIcon by mutableStateOf<Drawable?>(null)

    companion object {
        private const val TAG = "AppDomainBtmSht"
    }

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                AppDomainRulesContent()
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
        dialog.setOnDismissListener { onDismiss(position) }

        initData()
        setRulesUi()
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun initData() {
        if (uid == INVALID_UID) {
            dialog.dismiss()
            return
        }
        io {
            cd = DomainRulesManager.getObj(uid, domain)
            if (cd == null) {
                cd = DomainRulesManager.makeCustomDomain(uid, domain)
            }
        }
        updateAppDetails()
    }

    @Composable
    private fun AppDomainRulesContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val trustIcon =
            if (domainRule == DomainRulesManager.Status.TRUST) {
                R.drawable.ic_trust_accent
            } else {
                R.drawable.ic_trust
            }
        val blockIcon =
            if (domainRule == DomainRulesManager.Status.BLOCK) {
                R.drawable.ic_block_accent
            } else {
                R.drawable.ic_block
            }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            if (appName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    appIcon?.let { icon ->
                        AndroidView(
                            factory = { ctx -> ImageView(ctx) },
                            update = { view ->
                                view.setImageDrawable(icon)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = appName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = activity.getString(R.string.bsct_block_domain),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    painter = painterResource(id = trustIcon),
                    contentDescription = null,
                    modifier =
                        Modifier.size(28.dp).clickable {
                            if (domainRule == DomainRulesManager.Status.TRUST) {
                                applyDomainRule(DomainRulesManager.Status.NONE)
                            } else {
                                applyDomainRule(DomainRulesManager.Status.TRUST)
                            }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    painter = painterResource(id = blockIcon),
                    contentDescription = null,
                    modifier =
                        Modifier.size(28.dp).clickable {
                            if (domainRule == DomainRulesManager.Status.BLOCK) {
                                applyDomainRule(DomainRulesManager.Status.NONE)
                            } else {
                                applyDomainRule(DomainRulesManager.Status.BLOCK)
                            }
                        }
                )
            }

            Text(
                text = activity.getString(R.string.bsac_title_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )
        }
    }

    private fun updateAppDetails() {
        if (uid == -1) return

        io {
            val appNames = FirewallManager.getAppNamesByUid(uid)
            if (appNames.isEmpty()) {
                uiCtx {
                    appName = null
                    appIcon = null
                }
                return@io
            }
            val pkgName = FirewallManager.getPackageNameByAppName(appNames[0])

            val appCount = appNames.count()
            uiCtx {
                if (appCount >= 1) {
                    appName =
                        if (appCount >= 2) {
                            activity.getString(
                                R.string.ctbs_app_other_apps,
                                appNames[0],
                                appCount.minus(1).toString()
                            )
                        } else {
                            appNames[0]
                        }
                    if (pkgName != null) {
                        appIcon = Utilities.getIcon(activity, pkgName)
                    }
                } else {
                    appName = null
                    appIcon = null
                }
            }
        }
    }

    private fun setRulesUi() {
        io {
            domainRule = DomainRulesManager.status(domain, uid)
            Logger.d(LOG_TAG_FIREWALL, "$TAG set selection of ip: $domain, ${domainRule.id}")
        }
    }

    private fun showWgListDialog(data: List<WgConfigFilesImmutable?>) {
        Logger.v(LOG_TAG_UI, "$TAG show wg list(${data.size} for ${cd?.domain}, uid: $uid")
        WireguardListDialog(
            activity,
            WireguardListDialog.InputType.DOMAIN,
            cd,
            data,
            this
        ).show()
    }

    private fun applyDomainRule(status: DomainRulesManager.Status) {
        Logger.i(LOG_TAG_FIREWALL, "$TAG domain rule for uid: $uid:$domain (${status.name})")
        domainRule = status

        io {
            DomainRulesManager.changeStatus(
                domain,
                uid,
                "",
                DomainRulesManager.DomainType.DOMAIN,
                status
            )
        }
        logEvent("Domain rule applied: $domain, $uid, ${status.name}")
    }

    private fun logEvent(details: String) {
        eventLogger.log(
            EventType.FW_RULE_MODIFIED,
            Severity.LOW,
            "App domain rule",
            EventSource.UI,
            false,
            details
        )
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    override fun onDismissWg(obj: Any?) {
        try {
            val customDomain = obj as CustomDomain
            cd = customDomain
            setRulesUi()
            Logger.i(LOG_TAG_UI, "$TAG onDismissWg: ${cd?.domain}, $uid, ${cd?.proxyId}")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG err in onDismissWg ${e.message}", e)
        }
    }
}
