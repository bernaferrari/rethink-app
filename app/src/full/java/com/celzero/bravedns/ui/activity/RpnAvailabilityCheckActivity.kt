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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class RpnAvailabilityCheckActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    private var options by mutableStateOf<List<String>>(emptyList())
    private var items by mutableStateOf<List<RpnAvailabilityItem>>(emptyList())
    private var strength by mutableStateOf(0)
    private var maxStrength by mutableStateOf(0)

    companion object {
        private const val TAG = "RpnAvailabilityCheckActivity"
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

        options = listOf("WIN-US", "WIN-UK", "WIN-IN", "WIN-DE", "WIN-CA")
        maxStrength = options.size
        items = options.map { RpnAvailabilityItem(it, RpnAvailabilityStatus.Loading) }

        setContent {
            RethinkTheme {
                RpnAvailabilityScreen()
            }
        }

        startChecks()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun startChecks() {
        lifecycleScope.launch {
            options.forEach { option ->
                updateItemStatus(option, RpnAvailabilityStatus.Loading)
                val res = withContext(Dispatchers.IO) {
                    false
                }

                if (res) {
                    strength++
                    updateItemStatus(option, RpnAvailabilityStatus.Active)
                } else {
                    updateItemStatus(option, RpnAvailabilityStatus.Inactive)
                }
                Napier.i("$TAG strength: $strength ($res)")
            }
        }
    }

    private fun updateItemStatus(option: String, status: RpnAvailabilityStatus) {
        items = items.map { item ->
            if (item.name == option) {
                item.copy(status = status)
            } else {
                item
            }
        }
    }

    @Composable
    private fun RpnAvailabilityScreen() {
        val progress = if (maxStrength > 0) strength.toFloat() / maxStrength else 0f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResourceCompat(R.string.rpn_availability_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp
                )
                Text(
                    text = "$strength/$maxStrength",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, item ->
                        AvailabilityRow(item)
                        if (index != items.lastIndex) {
                            Divider()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AvailabilityRow(item: RpnAvailabilityItem) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium
            )
            when (item.status) {
                RpnAvailabilityStatus.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                RpnAvailabilityStatus.Active -> {
                    Text(
                        text = stringResourceCompat(R.string.lbl_active),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                RpnAvailabilityStatus.Inactive -> {
                    Text(
                        text = stringResourceCompat(R.string.lbl_inactive),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }

    private data class RpnAvailabilityItem(
        val name: String,
        val status: RpnAvailabilityStatus
    )

    private enum class RpnAvailabilityStatus {
        Loading,
        Active,
        Inactive
    }
}
