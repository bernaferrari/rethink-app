package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.databinding.BottomSheetProxiesListBinding
import com.celzero.bravedns.databinding.ListItemProxyCcWgBinding
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
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
    private val b = BottomSheetProxiesListBinding.inflate(LayoutInflater.from(activity))
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
        dialog.setContentView(b.root)
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
        init()
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

    private fun init() {
        when (type) {
            InputType.DOMAIN -> {
                b.ipDomainInfo.visibility = View.VISIBLE
                b.ipDomainInfo.text = cd?.domain
            }
            InputType.IP -> {
                b.ipDomainInfo.visibility = View.VISIBLE
                b.ipDomainInfo.text = ci?.ipAddress
            }
            InputType.APP -> {
                // no-op
            }
        }

        val lst = confs.map { it }
        b.recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = RecyclerViewAdapter(lst) { conf ->
            Logger.v(LOG_TAG_UI, "$TAG: Item clicked: ${conf?.name ?: "None"}")
            when (type) {
                InputType.DOMAIN -> {
                    processDomain(conf)
                }
                InputType.IP -> {
                    processIp(conf)
                }
                InputType.APP -> {
                    // no-op
                }
            }
        }

        b.recyclerView.adapter = adapter
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

    inner class RecyclerViewAdapter(
        private val data: List<WgConfigFilesImmutable?>,
        private val onItemClicked: (WgConfigFilesImmutable?) -> Unit
    ) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

        inner class ViewHolder(private val bb: ListItemProxyCcWgBinding) :
            RecyclerView.ViewHolder(bb.root) {
            fun bind(conf: WgConfigFilesImmutable?) {
                val idSuffix = conf?.id?.toString()?.padStart(3, '0')
                val proxyId = conf?.let { ID_WG_BASE + it.id } ?: ""
                bb.proxyIconCc.text = ID_WG_BASE.uppercase()
                bb.proxyNameCc.text =
                    conf?.name ?: activity.getString(R.string.settings_app_list_default_app)
                bb.proxyDescCc.text =
                    if (conf == null) {
                        activity.getString(R.string.settings_app_list_default_app)
                    } else {
                        activity.getString(R.string.settings_app_list_default_app) + " $idSuffix"
                    }
                bb.proxyRadioCc.isChecked = false
                when (type) {
                    InputType.DOMAIN -> {
                        bb.proxyRadioCc.isChecked = proxyId == cd?.proxyId
                    }
                    InputType.IP -> {
                        bb.proxyRadioCc.isChecked = proxyId == ci?.proxyId
                    }
                    InputType.APP -> {
                        // no-op
                    }
                }
                bb.lipCcWgParent.setOnClickListener {
                    onItemClicked(conf)
                    notifyDataSetChanged()
                }

                bb.proxyRadioCc.setOnClickListener {
                    onItemClicked(conf)
                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ListItemProxyCcWgBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount(): Int = data.size
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
