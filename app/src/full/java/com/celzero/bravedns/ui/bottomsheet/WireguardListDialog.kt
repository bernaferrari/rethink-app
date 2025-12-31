package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
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

class WireguardListDialog(
    private val activity: FragmentActivity,
    private val type: InputType,
    private val obj: Any?,
    private val confs: List<WgConfigFilesImmutable?>,
    private val listener: WireguardDismissListener
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()

    private val cd: CustomDomain? = if (type == InputType.DOMAIN) obj as CustomDomain else null
    private val ci: CustomIp? = if (type == InputType.IP) obj as CustomIp else null
    private val ai: AppInfo? = if (type == InputType.APP) obj as AppInfo else null

    companion object {
        private const val TAG = "WglBtmSht"
    }

    interface WireguardDismissListener {
        fun onDismissWg(obj: Any?)
    }

    enum class InputType(val id: Int) {
        DOMAIN(0),
        IP(1),
        APP(2)
    }

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                WireguardListContent()
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
        dialog.setOnDismissListener { handleDismiss() }
        Logger.v(LOG_TAG_UI, "$TAG: view created")
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

    @Composable
    private fun WireguardListContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val infoText =
            when (type) {
                InputType.DOMAIN -> cd?.domain
                InputType.IP -> ci?.ipAddress
                InputType.APP -> null
            }
        var selectedProxyId by
            remember {
                mutableStateOf(
                    when (type) {
                        InputType.DOMAIN -> cd?.proxyId ?: ""
                        InputType.IP -> ci?.proxyId ?: ""
                        InputType.APP -> ""
                    }
                )
            }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            infoText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            LazyColumn {
                items(confs, key = { it?.id ?: -1 }) { conf ->
                    val proxyId = conf?.let { ID_WG_BASE + it.id } ?: ""
                    val isSelected = selectedProxyId == proxyId
                    val name =
                        conf?.name
                            ?: activity.getString(R.string.settings_app_list_default_app)
                    val idSuffix = conf?.id?.toString()?.padStart(3, '0')
                    val desc =
                        if (conf == null) {
                            activity.getString(R.string.settings_app_list_default_app)
                        } else {
                            activity.getString(R.string.settings_app_list_default_app) +
                                " $idSuffix"
                        }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selectedProxyId = proxyId
                                    when (type) {
                                        InputType.DOMAIN -> processDomain(conf)
                                        InputType.IP -> processIp(conf)
                                        InputType.APP -> {}
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = ID_WG_BASE.uppercase(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = isSelected, onClick = null)
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
        }
    }

    private fun processDomain(conf: WgConfigFilesImmutable?) {
        io {
            val domain = cd ?: run {
                Logger.w(LOG_TAG_UI, "$TAG: Custom domain is null")
                return@io
            }
            if (conf == null) {
                DomainRulesManager.setProxyId(domain, "")
                domain.proxyId = ""
            } else {
                val id = ID_WG_BASE + conf.id
                DomainRulesManager.setProxyId(domain, id)
                domain.proxyId = id
            }
            val name = conf?.name ?: activity.getString(R.string.settings_app_list_default_app)
            Logger.v(LOG_TAG_UI, "$TAG: wg-endpoint set to $name for ${domain.domain}")
            uiCtx {
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.config_add_success_toast),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun processIp(conf: WgConfigFilesImmutable?) {
        io {
            val ip = ci ?: run {
                Logger.w(LOG_TAG_UI, "$TAG: Custom IP is null")
                return@io
            }
            if (conf == null) {
                IpRulesManager.updateProxyId(ip, "")
                ip.proxyId = ""
            } else {
                val id = ID_WG_BASE + conf.id
                IpRulesManager.updateProxyId(ip, id)
                ip.proxyId = id
            }
            val name = conf?.name ?: activity.getString(R.string.settings_app_list_default_app)
            Logger.v(LOG_TAG_UI, "$TAG: wg-endpoint set to $name for ${ip.ipAddress}")
            uiCtx {
                Utilities.showToastUiCentered(
                    activity,
                    activity.getString(R.string.config_add_success_toast),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun handleDismiss() {
        Logger.v(LOG_TAG_UI, "$TAG: Dismissed, input: ${type.name}")
        when (type) {
            InputType.DOMAIN -> {
                listener.onDismissWg(cd)
            }
            InputType.IP -> {
                listener.onDismissWg(ci)
            }
            InputType.APP -> {
                listener.onDismissWg(ai)
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
