/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.data.BlockFreeDnsItem
import com.bernaferrari.bravedns.data.BlockFreeDnsType
import com.bernaferrari.bravedns.database.DnsCryptEndpointDAO
import com.bernaferrari.bravedns.database.DnsProxyEndpointDAO
import com.bernaferrari.bravedns.database.DoHEndpointDAO
import com.bernaferrari.bravedns.database.DoTEndpointDAO
import com.bernaferrari.bravedns.database.ODoHEndpointDAO
import com.bernaferrari.bravedns.database.RethinkDnsEndpointDao
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

/** Common endpoint loader and filter state for the Block-Free DNS picker. */
@KoinViewModel
class BlockFreeDnsViewModel(
    @Provided private val rethinkDao: RethinkDnsEndpointDao,
    @Provided private val dohDao: DoHEndpointDAO,
    @Provided private val dotDao: DoTEndpointDAO,
    @Provided private val dnsCryptDao: DnsCryptEndpointDAO,
    @Provided private val odohDao: ODoHEndpointDAO,
    @Provided private val dnsProxyDao: DnsProxyEndpointDAO
) : ViewModel() {
    private val allItemsMutable = MutableStateFlow<List<BlockFreeDnsItem>>(emptyList())
    private val activeFilterMutable = MutableStateFlow<BlockFreeDnsType?>(null)

    val allItems: StateFlow<List<BlockFreeDnsItem>> = allItemsMutable.asStateFlow()
    val activeFilter: BlockFreeDnsType? get() = activeFilterMutable.value
    val filteredItemsFlow: StateFlow<List<BlockFreeDnsItem>> =
        combine(allItemsMutable, activeFilterMutable) { items, filter ->
            if (filter == null) items else items.filter { it.type == filter }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val items = linkedMapOf<String, BlockFreeDnsItem>()
            fun add(item: BlockFreeDnsItem) {
                items[item.key] = item
            }

            Constants.DEFAULT_DNS_LIST.forEach { endpoint ->
                val type = endpoint.type.toBlockFreeDnsType() ?: return@forEach
                add(
                    BlockFreeDnsItem(
                        key = BlockFreeDnsType.buildKey(type, endpoint.url),
                        type = type,
                        name = endpoint.name,
                        url = endpoint.url
                    )
                )
            }

            rethinkDao.getAllForBlockFree().forEach { endpoint ->
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.RETHINK, endpoint.url),
                        BlockFreeDnsType.RETHINK,
                        endpoint.name,
                        endpoint.url
                    )
                )
            }
            dohDao.getAll().forEach { endpoint ->
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.DOH, endpoint.dohURL),
                        BlockFreeDnsType.DOH,
                        endpoint.dohName,
                        endpoint.dohURL
                    )
                )
            }
            dotDao.getAll().forEach { endpoint ->
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.DOT, endpoint.url),
                        BlockFreeDnsType.DOT,
                        endpoint.name,
                        endpoint.url
                    )
                )
            }
            dnsCryptDao.getAll().forEach { endpoint ->
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.DNSCRYPT, endpoint.dnsCryptURL),
                        BlockFreeDnsType.DNSCRYPT,
                        endpoint.dnsCryptName,
                        endpoint.dnsCryptURL
                    )
                )
            }
            odohDao.getAll().forEach { endpoint ->
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.ODOH, endpoint.proxy),
                        BlockFreeDnsType.ODOH,
                        endpoint.name,
                        endpoint.resolver
                    )
                )
            }
            dnsProxyDao.getAll().forEach { endpoint ->
                val identifier = "${endpoint.proxyIP}:${endpoint.proxyPort}"
                add(
                    BlockFreeDnsItem(
                        BlockFreeDnsType.buildKey(BlockFreeDnsType.DNS_PROXY, identifier),
                        BlockFreeDnsType.DNS_PROXY,
                        endpoint.proxyName,
                        identifier
                    )
                )
            }

            allItemsMutable.value = items.values.toList()
        }
    }

    fun setFilter(type: BlockFreeDnsType?) {
        activeFilterMutable.value = type
    }

    private fun String.toBlockFreeDnsType(): BlockFreeDnsType? =
        when (uppercase()) {
            "RETHINK" -> BlockFreeDnsType.RETHINK
            "DOH" -> BlockFreeDnsType.DOH
            "DOT" -> BlockFreeDnsType.DOT
            "DNSCRYPT" -> BlockFreeDnsType.DNSCRYPT
            "ODOH" -> BlockFreeDnsType.ODOH
            "DNS_PROXY" -> BlockFreeDnsType.DNS_PROXY
            "SYSTEM", "NONE" -> BlockFreeDnsType.SYSTEM
            else -> null
        }
}
