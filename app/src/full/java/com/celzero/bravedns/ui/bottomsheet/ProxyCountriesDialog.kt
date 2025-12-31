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
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getFlag
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProxyCountriesDialog(
    private val activity: FragmentActivity,
    private val type: InputType,
    private val obj: Any?,
    private val confs: List<String>,
    private val listener: CountriesDismissListener
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()

    private val cd: CustomDomain? = if (type == InputType.DOMAIN) obj as CustomDomain else null
    private val ci: CustomIp? = if (type == InputType.IP) obj as CustomIp else null
    private val ai: AppInfo? = if (type == InputType.APP) obj as AppInfo else null

    enum class InputType(val id: Int) {
        DOMAIN(0),
        IP(1),
        APP(2)
    }

    companion object {
        private const val TAG = "PCCBtmSheet"
    }

    interface CountriesDismissListener {
        fun onDismissCC(obj: Any?)
    }

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                ProxyCountriesContent()
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
    private fun ProxyCountriesContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val infoText =
            when (type) {
                InputType.IP -> ci?.ipAddress
                InputType.DOMAIN -> cd?.domain
                InputType.APP -> null
            }
        var selectedConf by
            remember {
                mutableStateOf(
                    when (type) {
                        InputType.DOMAIN -> cd?.proxyCC ?: ""
                        InputType.IP -> ci?.proxyCC ?: ""
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

            Text(
                text = "Select Proxy Country",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            LazyColumn {
                items(confs, key = { it }) { conf ->
                    val isSelected = selectedConf == conf
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selectedConf = conf
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
                        Text(text = getFlag(conf), style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = conf, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = getFlag(conf),
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

    private fun processDomain(conf: String) {
        io {
            val domain = cd ?: run {
                Logger.w(LOG_TAG_UI, "$TAG: Custom domain is null")
                return@io
            }
            DomainRulesManager.setCC(domain, conf)
            domain.proxyCC = conf
            uiCtx {
                Utilities.showToastUiCentered(activity, getFlag(conf), Toast.LENGTH_SHORT)
            }
        }
    }

    private fun processIp(conf: String) {
        io {
            val ip = ci ?: run {
                Logger.w(LOG_TAG_UI, "$TAG: Custom IP is null")
                return@io
            }
            IpRulesManager.updateProxyCC(ip, conf)
            ip.proxyCC = conf
            uiCtx {
                Utilities.showToastUiCentered(activity, getFlag(conf), Toast.LENGTH_SHORT)
            }
        }
    }

    private fun handleDismiss() {
        Logger.v(LOG_TAG_UI, "$TAG: Dismissed, input: ${type.name}")
        when (type) {
            InputType.DOMAIN -> listener.onDismissCC(cd)
            InputType.IP -> listener.onDismissCC(ci)
            InputType.APP -> listener.onDismissCC(ai)
        }
    }

    private fun io(f: suspend () -> Unit) {
        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}
