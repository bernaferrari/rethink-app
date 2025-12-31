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

import Logger
import Logger.LOG_TAG_FIREWALL
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DomainRulesBtmSheetAdapter(
    val context: Context,
    private val uid: Int,
    private val domains: Array<String>
) : RecyclerView.Adapter<DomainRulesBtmSheetAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return ViewHolder(composeView)
    }

    override fun getItemCount(): Int {
        return domains.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val domain: String = domains[position]
        holder.update(domain)
    }

    inner class ViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(item: String) {
            val domain = item.trim()
            composeView.setContent {
                RethinkTheme {
                    DomainRuleRow(domain = domain)
                }
            }
        }

    }

    private fun applyDomainRule(domain: String, domainRuleStatus: DomainRulesManager.Status) {
        Logger.i(LOG_TAG_FIREWALL, "Apply domain rule for $domain, ${domainRuleStatus.name}")
        io {
            DomainRulesManager.addDomainRule(
                domain.trim(),
                domainRuleStatus,
                DomainRulesManager.DomainType.DOMAIN,
                uid,
            )
        }
    }

    @Composable
    private fun DomainRuleRow(domain: String) {
        var status by remember(domain) {
            mutableStateOf(DomainRulesManager.getDomainRule(domain, uid))
        }

        val trustIcon =
            if (status == DomainRulesManager.Status.TRUST) {
                R.drawable.ic_trust_accent
            } else {
                R.drawable.ic_trust
            }
        val blockIcon =
            if (status == DomainRulesManager.Status.BLOCK) {
                R.drawable.ic_block_accent
            } else {
                R.drawable.ic_block
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    status =
                        if (status == DomainRulesManager.Status.TRUST) {
                            applyDomainRule(domain, DomainRulesManager.Status.NONE)
                            DomainRulesManager.Status.NONE
                        } else {
                            applyDomainRule(domain, DomainRulesManager.Status.TRUST)
                            DomainRulesManager.Status.TRUST
                        }
                }
            ) {
                Icon(painter = painterResource(id = trustIcon), contentDescription = null)
            }
            IconButton(
                onClick = {
                    status =
                        if (status == DomainRulesManager.Status.BLOCK) {
                            applyDomainRule(domain, DomainRulesManager.Status.NONE)
                            DomainRulesManager.Status.NONE
                        } else {
                            applyDomainRule(domain, DomainRulesManager.Status.BLOCK)
                            DomainRulesManager.Status.BLOCK
                        }
                }
            ) {
                Icon(painter = painterResource(id = blockIcon), contentDescription = null)
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}
