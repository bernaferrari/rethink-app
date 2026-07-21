package com.celzero.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celzero.bravedns.data.BlockFreeDnsItem
import com.celzero.bravedns.data.BlockFreeDnsType
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.viewmodel.BlockFreeDnsViewModel

/** Compose port of the endpoint picker used for trusted/block-free DNS. */
@Composable
fun BlockFreeDnsScreen(viewModel: BlockFreeDnsViewModel, persistentState: PersistentState, onBackClick: () -> Unit) {
    val items by viewModel.filteredItemsFlow.collectAsStateWithLifecycle()
    Scaffold(topBar = { RethinkTopBar(title = "Trusted DNS endpoint", onBackClick = onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            LazyColumn(horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                item {
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                        FilterChip(selected = viewModel.activeFilter == null, onClick = { viewModel.setFilter(null) }, label = { Text("All") })
                        BlockFreeDnsType.entries.forEach { type -> FilterChip(selected = viewModel.activeFilter == type, onClick = { viewModel.setFilter(type) }, label = { Text(type.label) }, modifier = Modifier.padding(start = 6.dp)) }
                    }
                }
                items(items, key = BlockFreeDnsItem::key) { item ->
                    val selected = persistentState.blockFreeDns == item.key
                    Surface(onClick = { persistentState.blockFreeDns = item.key }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.large, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp)) { Text(item.name, style = MaterialTheme.typography.titleSmall); Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
