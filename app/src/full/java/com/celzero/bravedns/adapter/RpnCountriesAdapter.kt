/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities.getFlag

class RpnCountriesAdapter(
    private val context: Context,
    private val countries: List<String>,
    private val selectedCCs: Set<String>
) : RecyclerView.Adapter<RpnCountriesAdapter.RpnCountriesViewHolder>() {

    private var lifecycleOwner: LifecycleOwner? = null

    override fun onBindViewHolder(holder: RpnCountriesViewHolder, position: Int) {
        holder.update(countries[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RpnCountriesViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        if (lifecycleOwner == null) {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
        }
        return RpnCountriesViewHolder(composeView)
    }

    override fun getItemCount(): Int {
        return countries.size
    }

    inner class RpnCountriesViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(conf: String) {
            composeView.setContent {
                RethinkTheme {
                    CountryRow(conf)
                }
            }
        }
    }

    @Composable
    fun CountryRow(conf: String) {
        val flag = getFlag(conf)
        val ccName = conf
        val isSelected = selectedCCs.contains(conf)
        val strokeColor = getStrokeColorForStatus(isSelected)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardDefaults.shape,
            colors = CardDefaults.cardColors(),
            border =
                if (isSelected) {
                    BorderStroke(2.dp, Color(strokeColor))
                } else {
                    BorderStroke(0.dp, Color(strokeColor))
                }
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = flag,
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = ccName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (isSelected) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.lbl_active),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        Text(
                            text = stringResource(id = R.string.lbl_active)
                                .replaceFirstChar(Char::titlecase),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.lbl_disabled)
                                .replaceFirstChar(Char::titlecase),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.padding(end = 8.dp))
                Checkbox(checked = isSelected, onCheckedChange = null, enabled = false)
            }
        }
    }

    private fun getStrokeColorForStatus(isActive: Boolean): Int {
        if (!isActive) return fetchColor(context, R.attr.chipTextNegative)
        return fetchColor(context, R.attr.accentGood)
    }
}
