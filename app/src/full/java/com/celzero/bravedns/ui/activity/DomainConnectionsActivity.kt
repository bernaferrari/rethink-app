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
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.DomainConnectionsAdapter
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.CustomLinearLayoutManager
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.UIUtils.getCountryNameFromFlag
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DomainConnectionsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val viewModel by viewModel<DomainConnectionsViewModel>()

    private var type: InputType = InputType.DOMAIN
    private var titleText by mutableStateOf("")
    private var subtitleText by mutableStateOf("")
    private var showNoData by mutableStateOf(false)
    private var recyclerAdapter: DomainConnectionsAdapter? = null

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            UI_MODE_NIGHT_YES
    }

    companion object {
        const val INTENT_EXTRA_TYPE = "TYPE"
        const val INTENT_EXTRA_FLAG = "FLAG"
        const val INTENT_EXTRA_DOMAIN = "DOMAIN"
        const val INTENT_EXTRA_ASN = "ASN"
        const val INTENT_EXTRA_IP = "IP"
        const val INTENT_EXTRA_IS_BLOCKED = "IS_BLOCKED"
        const val INTENT_EXTRA_TIME_CATEGORY = "TIME_CATEGORY"
        private const val HEADER_ALPHA = 0.5f
    }

    enum class InputType(val type: Int) {
        DOMAIN(0),
        FLAG(1),
        ASN(2),
        IP(3);
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        val t = intent.getIntExtra(INTENT_EXTRA_TYPE, 0)
        type = InputType.entries.toTypedArray()[t]
        when (type) {
            InputType.DOMAIN -> {
                val domain = intent.getStringExtra(INTENT_EXTRA_DOMAIN) ?: ""
                val isBlocked = intent.getBooleanExtra(INTENT_EXTRA_IS_BLOCKED, false)
                viewModel.setDomain(domain, isBlocked)
                titleText = domain
            }
            InputType.FLAG -> {
                val flag = intent.getStringExtra(INTENT_EXTRA_FLAG) ?: ""
                viewModel.setFlag(flag)
                titleText = getString(R.string.two_argument_space, flag, getCountryNameFromFlag(flag))
            }
            InputType.ASN -> {
                val asn = intent.getStringExtra(INTENT_EXTRA_ASN) ?: ""
                val isBlocked = intent.getBooleanExtra(INTENT_EXTRA_IS_BLOCKED, false)
                viewModel.setAsn(asn, isBlocked)
                titleText = asn
            }
            InputType.IP -> {
                val ip = intent.getStringExtra(INTENT_EXTRA_IP) ?: ""
                val isBlocked = intent.getBooleanExtra(INTENT_EXTRA_IS_BLOCKED, false)
                viewModel.setIp(ip, isBlocked)
                titleText = ip
            }
        }

        val tc = intent.getIntExtra(INTENT_EXTRA_TIME_CATEGORY, 0)
        val timeCategory =
            DomainConnectionsViewModel.TimeCategory.fromValue(tc)
                ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
        setSubTitle(timeCategory)
        viewModel.timeCategoryChanged(timeCategory)
        setupAdapter()

        setContent {
            RethinkTheme {
                DomainConnectionsScreen()
            }
        }
    }

    private fun setSubTitle(timeCategory: DomainConnectionsViewModel.TimeCategory) {
        subtitleText =
            when (timeCategory) {
                DomainConnectionsViewModel.TimeCategory.ONE_HOUR -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_one),
                        getString(R.string.lbl_hour)
                    )
                }

                DomainConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_twenty_four),
                        getString(R.string.lbl_hour)
                    )
                }

                DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_seven),
                        getString(R.string.lbl_day)
                    )
                }
            }
    }

    private fun setupAdapter() {
        recyclerAdapter = DomainConnectionsAdapter(this, type)

        val liveData = when (type) {
            InputType.DOMAIN -> viewModel.domainConnectionList
            InputType.FLAG -> viewModel.flagConnectionList
            InputType.ASN -> viewModel.asnConnectionList
            InputType.IP -> viewModel.ipConnectionList
        }

        liveData.observe(this) { recyclerAdapter?.submitData(this.lifecycle, it) }

        recyclerAdapter?.addLoadStateListener {
            if (it.append.endOfPaginationReached) {
                showNoData = recyclerAdapter?.itemCount?.let { count -> count < 1 } ?: true
            } else {
                showNoData = false
            }
        }
    }

    @Composable
    private fun DomainConnectionsScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Header()
            Box(modifier = Modifier.fillMaxSize()) {
                ConnectionsList()
                if (showNoData) {
                    EmptyState()
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResourceCompat(R.string.app_name_small_case),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(HEADER_ALPHA)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Column {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    @Composable
    private fun ConnectionsList() {
        val listAdapter = recyclerAdapter
        if (listAdapter == null) return
        AndroidView(
            factory = { ctx ->
                RecyclerView(ctx).apply {
                    layoutManager = CustomLinearLayoutManager(ctx)
                    this.adapter = listAdapter
                    isNestedScrollingEnabled = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResourceCompat(R.string.blocklist_update_check_failure),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Image(
                painter = painterResource(id = R.drawable.illustrations_no_record),
                contentDescription = null,
                modifier = Modifier.size(220.dp)
            )
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}
