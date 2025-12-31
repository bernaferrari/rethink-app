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
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.databinding.BottomSheetProxiesListBinding
import com.celzero.bravedns.databinding.ListItemProxyCcWgBinding
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
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
    private val b = BottomSheetProxiesListBinding.inflate(LayoutInflater.from(activity))
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
        b.title.text = "Select Proxy Country"
        b.recyclerView.layoutManager = LinearLayoutManager(activity)

        when (type) {
            InputType.IP -> {
                b.ipDomainInfo.visibility = View.VISIBLE
                b.ipDomainInfo.text = ci?.ipAddress
            }
            InputType.DOMAIN -> {
                b.ipDomainInfo.visibility = View.VISIBLE
                b.ipDomainInfo.text = cd?.domain
            }
            InputType.APP -> {
                // no-op
            }
        }

        val adapter = RecyclerViewAdapter(confs) { country ->
            when (type) {
                InputType.DOMAIN -> processDomain(country)
                InputType.IP -> processIp(country)
                InputType.APP -> {
                    // no-op
                }
            }
        }
        b.recyclerView.adapter = adapter
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

    inner class RecyclerViewAdapter(
        private val data: List<String>,
        private val onItemClicked: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

        inner class ViewHolder(private val bb: ListItemProxyCcWgBinding) :
            RecyclerView.ViewHolder(bb.root) {
            fun bind(conf: String) {
                bb.proxyIconCc.text = getFlag(conf)
                bb.proxyNameCc.text = conf
                bb.proxyDescCc.text = getFlag(conf)
                bb.proxyRadioCc.isChecked = false
                when (type) {
                    InputType.DOMAIN -> {
                        bb.proxyRadioCc.isChecked = conf == cd?.proxyCC
                    }
                    InputType.IP -> {
                        bb.proxyRadioCc.isChecked = conf == ci?.proxyCC
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
